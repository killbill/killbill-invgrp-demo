/*
 * Copyright 2022 The Billing Project, LLC - All Rights Reserved
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package org.killbill.billing.plugin.invgrp;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.killbill.billing.catalog.api.BillingPeriod;
import org.killbill.billing.catalog.api.Currency;
import org.killbill.billing.catalog.api.PriceListSet;
import org.killbill.billing.catalog.api.ProductCategory;
import org.killbill.billing.client.JaxrsResource;
import org.killbill.billing.client.KillBillClientException;
import org.killbill.billing.client.KillBillHttpClient;
import org.killbill.billing.client.RequestOptions;
import org.killbill.billing.client.api.gen.AccountApi;
import org.killbill.billing.client.api.gen.CatalogApi;
import org.killbill.billing.client.api.gen.InvoiceApi;
import org.killbill.billing.client.api.gen.SubscriptionApi;
import org.killbill.billing.client.api.gen.TenantApi;
import org.killbill.billing.client.model.Bundles;
import org.killbill.billing.client.model.InvoicePayments;
import org.killbill.billing.client.model.Invoices;
import org.killbill.billing.client.model.PaymentMethods;
import org.killbill.billing.client.model.Tags;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.AuditLog;
import org.killbill.billing.client.model.gen.InvoiceItem;
import org.killbill.billing.client.model.gen.InvoicePayment;
import org.killbill.billing.client.model.gen.PaymentMethod;
import org.killbill.billing.client.model.gen.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.gen.Subscription;
import org.killbill.billing.client.model.gen.Tenant;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.Files;
import com.google.common.io.Resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestIntegration {

    // Assumes a running instance of Kill Bill with the invgrp-plugin installed on the following address:
    private final String SERVER_HOST = "127.0.0.1";
    private final int SERVER_PORT = 8080;

    // Context information to be passed around
    private static final String createdBy = "Integration test";
    private static final String reason = "test";
    private static final String comment = "no comment";

    private final int DEFAULT_CONNECT_TIMEOUT_SEC = 10;
    private final int DEFAULT_READ_TIMEOUT_SEC = 60;

    private final long DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC = 60;

    // Note that our invgrp-plugin implements the PaymentPluginApi just to satisfy the requirements of the test
    private static final String PAYMENT_PLUGIN_NAME = "invgrp-plugin";

    private static final ImmutableList<String> NULL_PLUGIN_NAMES = null;
    private static final ImmutableMap<String, String> NULL_PLUGIN_PROPERTIES = null;
    private static final ImmutableList<AuditLog> EMPTY_AUDIT_LOGS = ImmutableList.<AuditLog>of();

    // Default Kill Bill credentials
    private final String USERNAME = "admin";
    private final String PASSWORD = "password";

    // Create new keys for each tenant
    private final String DEFAULT_API_KEY = UUID.randomUUID().toString();
    private final String DEFAULT_API_SECRET = UUID.randomUUID().toString();

    private RequestOptions requestOptions;

    // Test parameter to control how many subscriptions/PMS should be created
    private static final int NB_SUBSCRIPTIONS = 10;

    private KillBillHttpClient killBillHttpClient;

    private AccountApi accountApi;
    private CatalogApi catalogApi;
    private InvoiceApi invoiceApi;
    private SubscriptionApi subscriptionApi;
    private TenantApi tenantApi;

    @BeforeClass(groups = "integration")
    public void beforeClass() throws Exception {

        // Initial requestOptions
        requestOptions = RequestOptions.builder()
                                       .withCreatedBy(createdBy)
                                       .withReason(reason)
                                       .withComment(comment)
                                       .build();

        // Create a new tenant and upload a catalog with one 'Gold' Plan
        createTenant(DEFAULT_API_KEY, DEFAULT_API_SECRET, true);
        uploadTenantCatalog("org/killbill/billing/plugin/invgrp/Catalog.xml", true);
    }

    @AfterClass(groups = "integration")
    public void afterClass() throws Exception {
        // For cleanliness
        logoutTenant();
    }

    @BeforeMethod(groups = "integration")
    public void beforeMethod() throws Exception {
        // No-op as we have only one test
    }

    @Test(groups = "integration")
    public void testBasic() throws Exception {

        // Create new Account
        final Account account = createAccount();
        assertNotNull(account);

        //
        // AUTO_PAY_OFF, AUTO_INVOICING_OFF
        // This is to make it easy for the test as eveything is driven from api calls (nothing asynchronous)
        //
        final UUID AUTO_PAY_OFF = new UUID(0L, 1L);
        final UUID AUTO_INVOICING_OFF = new UUID(0L, 2L);
        final Tags tags = accountApi.createAccountTags(account.getAccountId(), ImmutableList.<UUID>of(AUTO_PAY_OFF, AUTO_INVOICING_OFF), requestOptions);
        assertEquals(tags.get(0).getTagDefinitionName(), "AUTO_PAY_OFF");
        assertEquals(tags.get(1).getTagDefinitionName(), "AUTO_INVOICING_OFF");

        //
        // Create a default PM on the account
        // This is important to allow the built-in __INVOICE_PAYMENT_CONTROL_PLUGIN__ inside Kill Bill to not abort the request
        //
        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        final PaymentMethod defaultPaymentMethod = new PaymentMethod(null, UUID.randomUUID().toString(), account.getAccountId(), true, "__EXTERNAL_PAYMENT__", info, EMPTY_AUDIT_LOGS);
        final PaymentMethod defaultPm = accountApi.createPaymentMethod(account.getAccountId(), defaultPaymentMethod, true, false, NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertNotNull(defaultPm);

        //
        // Create the NB_SUBSCRIPTIONS subscriptions, and create one new payment method for each subscription
        //
        for (int i = 0; i < NB_SUBSCRIPTIONS; i++) {
            createSubscriptionWithPaymentMethod(account.getAccountId());
        }

        // Trigger an invoice run
        final LocalDate targetDate = new LocalDate();
        final Invoices invoices = invoiceApi.createFutureInvoiceGroup(account.getAccountId(), targetDate, null, requestOptions.extend()
  																													.withQueryParamsForFollow(Map.of(JaxrsResource.QUERY_ACCOUNT_ID, List.of(account.getAccountId().toString())))
                                                                                                                        .withFollowLocation(true).build());
        assertEquals(invoices.size(), NB_SUBSCRIPTIONS);

        final Invoices paidInvoices = accountApi.payAllInvoices(account.getAccountId(), null, false, null, null, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(paidInvoices.size(), NB_SUBSCRIPTIONS);


        validateMapping(account.getAccountId(), invoices);

        // Trigger another invoice run a month later and verify we split again the run into NB_SUBSCRIPTIONS invoices and separate payments
        final LocalDate targetDate2 = targetDate.plusMonths(1);
        final Invoices invoices2 = invoiceApi.createFutureInvoiceGroup(account.getAccountId(), targetDate2, null, requestOptions.extend()
                                                                                                                             .withQueryParamsForFollow(Map.of(JaxrsResource.QUERY_ACCOUNT_ID, List.of(account.getAccountId().toString())))
                                                                                                                          .withFollowLocation(true).build());
        assertEquals(invoices2.size(), NB_SUBSCRIPTIONS);

        final Invoices paidInvoices2 = accountApi.payAllInvoices(account.getAccountId(), null, false, null, targetDate2, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(paidInvoices2.size(), NB_SUBSCRIPTIONS);

        validateMapping(account.getAccountId(), invoices2);
    }

    //
    //        VALIDATION
    //
    // Validate that for each invoice payment we find one payment method associated with the one subscription item on the invoice
    //
    // The validation happens by checking that the payment method external key is he same as the subscription external key
    //
    private void validateMapping(final UUID accountId, final Invoices invoices) throws KillBillClientException {

        final InvoicePayments invoicePayments = accountApi.getInvoicePayments(accountId, NULL_PLUGIN_PROPERTIES, requestOptions);

        final Bundles bundles = accountApi.getAccountBundles(accountId, null, null, requestOptions);

        final PaymentMethods pms = accountApi.getPaymentMethodsForAccount(accountId, NULL_PLUGIN_PROPERTIES, requestOptions);

        invoices.stream()
                .forEach(i -> {

                    // Find the invoice payment
                    final InvoicePayment targetInvoicePayment = invoicePayments.stream()
                                                                               .filter(ip -> ip.getTargetInvoiceId().equals(i.getInvoiceId()))
                                                                               .findFirst()
                                                                               .orElse(null);
                    Assert.assertNotNull(targetInvoicePayment, String.format("Failed to find invoice payment for invoice %s", i.getInvoiceId()));

                    // Find the payment method and extract its externalKey
                    final String pmExternalKey = pms.stream()
                                                    .filter(pm -> pm.getPaymentMethodId().equals(targetInvoicePayment.getPaymentMethodId()))
                                                    .map(PaymentMethod::getExternalKey)
                                                    .findFirst()
                                                    .orElse(null);

                    // Verify we see 1 invoice item for a subscription
                    final List<InvoiceItem> items = i.getItems().stream()
                                                            .filter(ii -> ii.getSubscriptionId() != null)
                                                            .collect(Collectors.toList());
                    Assert.assertEquals(items.size(), 1, String.format("Failed to find 1 invoice item for invoice %s", i.getInvoiceId()));
                    final InvoiceItem targetItem = items.get(0);

                    // Find matching Subscription
                    final Subscription subscription = bundles.stream()
                                                             .flatMap(b -> b.getSubscriptions().stream())
                                                             .filter(s -> s.getSubscriptionId().equals(targetItem.getSubscriptionId()))
                                                             .findFirst()
                                                             .orElse(null);

                    Assert.assertNotNull(subscription, String.format("Failed to find subscription referenced by the invoice item %s", targetItem.getInvoiceId()));

                    // Finally validate the mapping
                    Assert.assertEquals(subscription.getExternalKey(), pmExternalKey, String.format("Failed to find subscription with external key %s", pmExternalKey));
                });
    }

    //
    //        SUBSCRIPTION CREATION
    //
    private void createSubscriptionWithPaymentMethod(final UUID accountId) throws KillBillClientException {

        // We use the same subscription and payment external key
        // Just to make it easy to check the mapping between the two - i.e this information is not used by the plugin
        //
        final String externalKey = UUID.randomUUID().toString();

        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        final PaymentMethod paymentMethodJson = new PaymentMethod(null, externalKey, accountId, false, PAYMENT_PLUGIN_NAME, info, EMPTY_AUDIT_LOGS);
        final PaymentMethod pm = accountApi.createPaymentMethod(accountId, paymentMethodJson, false, false, NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertNotNull(pm);

        final Subscription subscription = createSubscription(accountId, externalKey, "Gold", ProductCategory.BASE, BillingPeriod.MONTHLY, pm.getPaymentMethodId());
        assertNotNull(subscription);
    }

    private Subscription createSubscription(final UUID accountId, final String externalKey, final String productName, final ProductCategory productCategory, final BillingPeriod billingPeriod, final UUID pmId) throws KillBillClientException {

        // Specify the custom plugin property PM_ID to correctly attach the payment method for the subscription
        //
        // This is required by the invgrp-plugin to split the invoices into N invoices and later make individual payment for each invoice
        // using the correct payment method
        //
        final Map<String, String> properties = new HashMap<>();
        properties.put("PM_ID", pmId.toString());

        final Subscription input = new Subscription();
        input.setAccountId(accountId);
        input.setBundleExternalKey(externalKey);
        input.setExternalKey(externalKey);
        input.setProductName(productName);
        input.setProductCategory(productCategory);
        input.setBillingPeriod(billingPeriod);
        input.setPriceList(PriceListSet.DEFAULT_PRICELIST_NAME);
        final Subscription subscription = subscriptionApi.createSubscription(input, (DateTime) null, null, true, false, false, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, properties, requestOptions);
        return subscription;
    }

    //
    //        ACCOUNT CREATION
    //
    private Account createAccount() throws KillBillClientException {
        Account input = getAccount(UUID.randomUUID().toString());
        return accountApi.createAccount(input, requestOptions);
    }

    public Account getAccount(final String externalKey) {
        final Account tmp = new Account();
        tmp.setCurrency(Currency.USD);
        tmp.setLocale("en-US");
        tmp.setExternalKey(externalKey);
        return tmp;
    }

    //
    //        SETUP (CLIENT, TENANT, ...)
    //
    private String uploadTenantCatalog(final String catalog, final boolean fetch) throws IOException, KillBillClientException {

        final String resourcePath = Resources.getResource(catalog).getPath();
        final File catalogFile = new File(resourcePath);
        final String body = Files.toString(catalogFile, Charset.forName("UTF-8"));
        catalogApi.uploadCatalogXml(body, requestOptions);
        return fetch ? catalogApi.getCatalogXml(null, null, requestOptions) : null;
    }

    private Tenant createTenant(final String apiKey, final String apiSecret, final boolean useGlobalDefault) throws KillBillClientException, JsonProcessingException {
        loginTenant(apiKey, apiSecret);
        final Tenant tenant = new Tenant();
        tenant.setApiKey(apiKey);
        tenant.setApiSecret(apiSecret);

        requestOptions = requestOptions.extend()
                                       .withTenantApiKey(apiKey)
                                       .withTenantApiSecret(apiSecret)
                                       .build();

        final Tenant createdTenant = tenantApi.createTenant(tenant, useGlobalDefault, requestOptions);
        createdTenant.setApiSecret(apiSecret);

        // Add the per-tenant property as per README
        final ObjectMapper mapper = new ObjectMapper();
        final HashMap<String, String> perTenantProperties = new HashMap<>();
        perTenantProperties.put("org.killbill.payment.invoice.plugin", "invgrp-plugin");
        perTenantProperties.put("org.killbill.payment.method.overwrite", "true");
        final String perTenantConfig = mapper.writeValueAsString(perTenantProperties);
        tenantApi.uploadPerTenantConfiguration(perTenantConfig, requestOptions);
        return createdTenant;
    }

    private void loginTenant(final String apiKey, final String apiSecret) {
        setupClient(USERNAME, PASSWORD, apiKey, apiSecret);
    }

    private void logoutTenant() {
        setupClient(USERNAME, PASSWORD, null, null);
    }

    private void setupClient(final String username, final String password, final String apiKey, final String apiSecret) {
        requestOptions = requestOptions.extend()
                                       .withTenantApiKey(apiKey)
                                       .withTenantApiSecret(apiSecret)
                                       .build();

        killBillHttpClient = new KillBillHttpClient(String.format("http://%s:%d", SERVER_HOST, SERVER_PORT),
                                                    username,
                                                    password,
                                                    apiKey,
                                                    apiSecret,
                                                    null,
                                                    null, DEFAULT_CONNECT_TIMEOUT_SEC * 1000,
                                                    DEFAULT_READ_TIMEOUT_SEC * 1000);

        accountApi = new AccountApi(killBillHttpClient);
        catalogApi = new CatalogApi(killBillHttpClient);
        invoiceApi = new InvoiceApi(killBillHttpClient);
        subscriptionApi = new SubscriptionApi(killBillHttpClient);
        tenantApi = new TenantApi(killBillHttpClient);
    }

}
