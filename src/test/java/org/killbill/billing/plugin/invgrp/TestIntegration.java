package org.killbill.billing.plugin.invgrp;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
import org.killbill.billing.client.api.gen.AdminApi;
import org.killbill.billing.client.api.gen.BundleApi;
import org.killbill.billing.client.api.gen.CatalogApi;
import org.killbill.billing.client.api.gen.CreditApi;
import org.killbill.billing.client.api.gen.CustomFieldApi;
import org.killbill.billing.client.api.gen.ExportApi;
import org.killbill.billing.client.api.gen.InvoiceApi;
import org.killbill.billing.client.api.gen.InvoiceItemApi;
import org.killbill.billing.client.api.gen.InvoicePaymentApi;
import org.killbill.billing.client.api.gen.NodesInfoApi;
import org.killbill.billing.client.api.gen.OverdueApi;
import org.killbill.billing.client.api.gen.PaymentApi;
import org.killbill.billing.client.api.gen.PaymentGatewayApi;
import org.killbill.billing.client.api.gen.PaymentMethodApi;
import org.killbill.billing.client.api.gen.PaymentTransactionApi;
import org.killbill.billing.client.api.gen.PluginInfoApi;
import org.killbill.billing.client.api.gen.SecurityApi;
import org.killbill.billing.client.api.gen.SubscriptionApi;
import org.killbill.billing.client.api.gen.TagApi;
import org.killbill.billing.client.api.gen.TagDefinitionApi;
import org.killbill.billing.client.api.gen.TenantApi;
import org.killbill.billing.client.api.gen.UsageApi;
import org.killbill.billing.client.model.Invoices;
import org.killbill.billing.client.model.Tags;
import org.killbill.billing.client.model.gen.Account;
import org.killbill.billing.client.model.gen.AuditLog;
import org.killbill.billing.client.model.gen.PaymentMethod;
import org.killbill.billing.client.model.gen.PaymentMethodPluginDetail;
import org.killbill.billing.client.model.gen.Subscription;
import org.killbill.billing.client.model.gen.Tenant;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.Files;
import com.google.common.io.Resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestIntegration {

    // Context information to be passed around
    private static final String createdBy = "Integration test";
    private static final String reason = "test";
    private static final String comment = "no comment";

    private final int DEFAULT_CONNECT_TIMEOUT_SEC = 10;
    private final int DEFAULT_READ_TIMEOUT_SEC = 60;

    private final long DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC = 60;
    protected static final String PLUGIN_NAME = "__EXTERNAL_PAYMENT__";

    private static final ImmutableList<String> NULL_PLUGIN_NAMES = null;
    private static final ImmutableMap<String, String> NULL_PLUGIN_PROPERTIES = null;
    private static final ImmutableList<AuditLog> EMPTY_AUDIT_LOGS = ImmutableList.<AuditLog>of();

    private final String SERVER_HOST = "127.0.0.1";
    private final int SERVER_PORT = 8080;

    private final String USERNAME = "admin";
    private final String PASSWORD = "password";

    private final String DEFAULT_API_KEY = UUID.randomUUID().toString();
    private final String DEFAULT_API_SECRET = UUID.randomUUID().toString();

    private static RequestOptions requestOptions = RequestOptions.builder()
                                                                 .withCreatedBy(createdBy)
                                                                 .withReason(reason)
                                                                 .withComment(comment)
                                                                 .build();

    private KillBillHttpClient killBillHttpClient;

    private AccountApi accountApi;
    private AdminApi adminApi;
    private BundleApi bundleApi;
    private CatalogApi catalogApi;
    private CreditApi creditApi;
    private CustomFieldApi customFieldApi;
    private ExportApi exportApi;
    private InvoiceApi invoiceApi;
    private InvoiceItemApi invoiceItemApi;
    private InvoicePaymentApi invoicePaymentApi;
    private NodesInfoApi nodesInfoApi;
    private OverdueApi overdueApi;
    private PaymentApi paymentApi;
    private PaymentGatewayApi paymentGatewayApi;
    private PaymentMethodApi paymentMethodApi;
    private PaymentTransactionApi paymentTransactionApi;
    private PluginInfoApi pluginInfoApi;
    private SecurityApi securityApi;
    private SubscriptionApi subscriptionApi;
    private TagApi tagApi;
    private TagDefinitionApi tagDefinitionApi;
    private TenantApi tenantApi;
    private UsageApi usageApi;

    @BeforeClass(groups = "integration")
    public void beforeClass() throws Exception {
        createTenant(DEFAULT_API_KEY, DEFAULT_API_SECRET, true);
        uploadTenantCatalog("org/killbill/billing/plugin/invgrp/Catalog.xml", true);

    }

    @AfterClass(groups = "integration")
    public void afterClass() throws Exception {
        logoutTenant();
    }

    @BeforeMethod(groups = "integration")
    public void beforeMethod() throws Exception {
    }

    @Test(groups = "integration")
    public void testBasic() throws Exception {

        final Account account = createAccount();
        assertNotNull(account);


        // PM_ID

        // AUTO_PAY_OFF, AUTO_INVOICING_OFF
        final Tags tags = accountApi.createAccountTags(account.getAccountId(), ImmutableList.<UUID>of(new UUID(0L, 1L), new UUID(0L, 2L)), requestOptions);
        assertEquals(tags.get(0).getTagDefinitionName(), "AUTO_PAY_OFF");
        assertEquals(tags.get(1).getTagDefinitionName(), "AUTO_INVOICING_OFF");

        final PaymentMethodPluginDetail info = new PaymentMethodPluginDetail();
        final PaymentMethod paymentMethodJson = new PaymentMethod(null, UUID.randomUUID().toString(), account.getAccountId(), true, PLUGIN_NAME, info, EMPTY_AUDIT_LOGS);
        final PaymentMethod pm  = accountApi.createPaymentMethod(account.getAccountId(), paymentMethodJson, false, false, NULL_PLUGIN_NAMES, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertNotNull(pm);


        final Subscription subscription = createSubscription(account.getAccountId(), UUID.randomUUID().toString(), "Gold", ProductCategory.BASE, BillingPeriod.MONTHLY, pm.getPaymentMethodId());
        assertNotNull(subscription);

        final Invoices invoices2 = invoiceApi.createFutureInvoiceGroup(account.getAccountId(), new LocalDate(), requestOptions.extend()
                                                                                                                              .withQueryParamsForFollow(ImmutableMultimap.of(JaxrsResource.QUERY_ACCOUNT_ID, account.getAccountId().toString()))
                                                                                                                              .withFollowLocation(true).build());
        assertEquals(invoices2.size(), 1);

        final Invoices paidInvoices = accountApi.payAllInvoices(account.getAccountId(), null, false, null, null, NULL_PLUGIN_PROPERTIES, requestOptions);
        assertEquals(paidInvoices.size(), 1);


    }

    private Subscription createSubscription(final UUID accountId, final String externalKey, final String productName, final ProductCategory productCategory, final BillingPeriod billingPeriod, final UUID pmId) throws KillBillClientException {

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
        final Subscription subscription = subscriptionApi.createSubscription(input, null, null, true, false, false, true, DEFAULT_WAIT_COMPLETION_TIMEOUT_SEC, properties, requestOptions);
        return subscription;
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
        adminApi = new AdminApi(killBillHttpClient);
        bundleApi = new BundleApi(killBillHttpClient);
        catalogApi = new CatalogApi(killBillHttpClient);
        creditApi = new CreditApi(killBillHttpClient);
        customFieldApi = new CustomFieldApi(killBillHttpClient);
        exportApi = new ExportApi(killBillHttpClient);
        invoiceApi = new InvoiceApi(killBillHttpClient);
        invoiceItemApi = new InvoiceItemApi(killBillHttpClient);
        invoicePaymentApi = new InvoicePaymentApi(killBillHttpClient);
        nodesInfoApi = new NodesInfoApi(killBillHttpClient);
        overdueApi = new OverdueApi(killBillHttpClient);
        paymentApi = new PaymentApi(killBillHttpClient);
        paymentGatewayApi = new PaymentGatewayApi(killBillHttpClient);
        paymentMethodApi = new PaymentMethodApi(killBillHttpClient);
        paymentTransactionApi = new PaymentTransactionApi(killBillHttpClient);
        pluginInfoApi = new PluginInfoApi(killBillHttpClient);
        securityApi = new SecurityApi(killBillHttpClient);
        subscriptionApi = new SubscriptionApi(killBillHttpClient);
        tagApi = new TagApi(killBillHttpClient);
        tagDefinitionApi = new TagDefinitionApi(killBillHttpClient);
        tenantApi = new TenantApi(killBillHttpClient);
        usageApi = new UsageApi(killBillHttpClient);
    }

    protected String uploadTenantCatalog(final String catalog, final boolean fetch) throws IOException, KillBillClientException {

        final String resourcePath = Resources.getResource(catalog).getPath();
        final File catalogFile = new File(resourcePath);
        final String body = Files.toString(catalogFile, Charset.forName("UTF-8"));
        catalogApi.uploadCatalogXml(body, requestOptions);
        return fetch ? catalogApi.getCatalogXml(null, null, requestOptions) : null;
    }

    protected Tenant createTenant(final String apiKey, final String apiSecret, final boolean useGlobalDefault) throws KillBillClientException {
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
        return createdTenant;
    }

    protected void loginTenant(final String apiKey, final String apiSecret) {
        setupClient(USERNAME, PASSWORD, apiKey, apiSecret);
    }

    protected void logoutTenant() {
        setupClient(USERNAME, PASSWORD, null, null);
    }

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

}
