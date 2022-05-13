/*
 * Copyright 2022 The Billing Project, LLC - All Rights Reserved
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package org.killbill.billing.plugin.invgrp;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.killbill.billing.ObjectType;
import org.killbill.billing.control.plugin.api.OnFailurePaymentControlResult;
import org.killbill.billing.control.plugin.api.OnSuccessPaymentControlResult;
import org.killbill.billing.control.plugin.api.PaymentControlApiException;
import org.killbill.billing.control.plugin.api.PaymentControlContext;
import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.control.plugin.api.PriorPaymentControlResult;
import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceApiException;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.control.PluginOnFailurePaymentControlResult;
import org.killbill.billing.plugin.api.control.PluginOnSuccessPaymentControlResult;
import org.killbill.billing.plugin.api.control.PluginPriorPaymentControlResult;
import org.killbill.billing.util.callcontext.TenantContext;
import org.killbill.billing.util.customfield.CustomField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvgrpPaymentControlPluginApi implements PaymentControlPluginApi {

    private static final String PROP_IPCD_INVOICE_ID = "IPCD_INVOICE_ID";

    private static final Logger logger = LoggerFactory.getLogger(InvgrpPaymentControlPluginApi.class);

    private final OSGIKillbillAPI killbillAPI;

    public InvgrpPaymentControlPluginApi(final OSGIKillbillAPI killbillAPI) {
        this.killbillAPI = killbillAPI;
    }

    @Override
    public PriorPaymentControlResult priorCall(final PaymentControlContext context, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
        //
        // Adjust the PM to match the per-subscription setting
        //
        final Invoice invoice = getInvoice(properties, context);
        if (invoice == null) {
            logger.warn("No invoice for payment {}", context.getPaymentId());
            return new PluginPriorPaymentControlResult(false);
        }

        final UUID pmId = getPaymentMethodId(invoice, context);
        if (pmId == null) {
            logger.info("No payment method configured for invoice {}, skip", invoice.getId());
            return new PluginPriorPaymentControlResult(false);
        }

        logger.info("Adjusting payment method for payment external key = {}: invoice={}, pmId={}",
                    context.getPaymentExternalKey(), invoice.getId(), pmId);

        return new PluginPriorPaymentControlResult(false, null, null, pmId, null, null);
    }

    @Override
    public OnSuccessPaymentControlResult onSuccessCall(final PaymentControlContext context, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
        return new PluginOnSuccessPaymentControlResult();
    }

    @Override
    public OnFailurePaymentControlResult onFailureCall(final PaymentControlContext context, final Iterable<PluginProperty> properties) throws PaymentControlApiException {
        return new PluginOnFailurePaymentControlResult();
    }

    private UUID getPaymentMethodId(final Invoice invoice, final TenantContext context) throws PaymentControlApiException {
        // We expect at most one subscription per invoice
        final UUID subscriptionId = invoice.getInvoiceItems()
                                           .stream()
                                           .filter(ii -> ii.getSubscriptionId() != null)
                                           .map(InvoiceItem::getSubscriptionId)
                                           .findFirst()
                                           .orElse(null);
        if (subscriptionId != null) {
            final List<CustomField> fields = killbillAPI.getCustomFieldUserApi().getCustomFieldsForObject(subscriptionId, ObjectType.SUBSCRIPTION, context);
            final String paymentMethodId = fields.stream()
                                                 .filter(f -> f.getFieldName().equals(InvgrpEntitlementPluginApi.PM_ID))
                                                 .map(CustomField::getFieldValue)
                                                 .findFirst()
                                                 .orElse(null);
            return paymentMethodId != null ? UUID.fromString(paymentMethodId) : null;
        }
        return null;
    }


    private Invoice getInvoice(final Iterable<PluginProperty> properties, final TenantContext context) throws PaymentControlApiException {
        final PluginProperty prop = StreamSupport.stream(properties.spliterator(), false)
                                                 .filter(p -> p.getKey().equals(PROP_IPCD_INVOICE_ID))
                                                 .findFirst()
                                                 .orElse(null);
        if (prop == null) {
            return null;
        }

        final UUID invoiceId = UUID.fromString((String) prop.getValue());
        try {
            return killbillAPI.getInvoiceUserApi().getInvoice(invoiceId, context);
        } catch (final InvoiceApiException e) {
            throw new PaymentControlApiException("Failed to find invoice", e);
        }
    }
}
