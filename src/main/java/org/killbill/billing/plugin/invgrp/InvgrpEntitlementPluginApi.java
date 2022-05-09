package org.killbill.billing.plugin.invgrp;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.killbill.billing.ObjectType;
import org.killbill.billing.entitlement.api.BaseEntitlementWithAddOnsSpecifier;
import org.killbill.billing.entitlement.api.EntitlementSpecifier;
import org.killbill.billing.entitlement.api.Subscription;
import org.killbill.billing.entitlement.api.SubscriptionApiException;
import org.killbill.billing.entitlement.plugin.api.EntitlementContext;
import org.killbill.billing.entitlement.plugin.api.EntitlementPluginApi;
import org.killbill.billing.entitlement.plugin.api.EntitlementPluginApiException;
import org.killbill.billing.entitlement.plugin.api.OnFailureEntitlementResult;
import org.killbill.billing.entitlement.plugin.api.OnSuccessEntitlementResult;
import org.killbill.billing.entitlement.plugin.api.OperationType;
import org.killbill.billing.entitlement.plugin.api.PriorEntitlementResult;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillClock;
import org.killbill.billing.payment.api.PaymentApiException;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.core.PluginCustomField;
import org.killbill.billing.util.api.CustomFieldApiException;
import org.killbill.billing.util.callcontext.CallContext;
import org.killbill.billing.util.customfield.CustomField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InvgrpEntitlementPluginApi implements EntitlementPluginApi {

    private static final String PM_ID = "PM_ID";
    private static final String USER = "admin";
    private static final String PWD = "password";

    public static final Logger logger = LoggerFactory.getLogger(InvgrpEntitlementPluginApi.class);

    private final OSGIKillbillAPI killbillAPI;
    private final OSGIKillbillClock clock;

    public InvgrpEntitlementPluginApi(final OSGIKillbillAPI killbillAPI, final OSGIKillbillClock clock) {
        this.killbillAPI = killbillAPI;
        this.clock = clock;
    }

    @Override
    public PriorEntitlementResult priorCall(final EntitlementContext context, final Iterable<PluginProperty> properties) {
        return null;
    }

    @Override
    public OnSuccessEntitlementResult onSuccessCall(final EntitlementContext context, final Iterable<PluginProperty> properties) throws EntitlementPluginApiException {
        if (context.getOperationType() == OperationType.CREATE_SUBSCRIPTION) {

            logger.info("OnSuccess call for CREATE_SUBSCRIPTION");

            final UUID subId = getSubscriptionId(context);
            if (subId == null) {
                logger.warn("Failed to find subscription from context");
                return null;
            }

            final UUID pmId = getPaymentMethodId(properties);
            if (pmId == null) {
                logger.warn("Failed to find property for paymentMethod");
                return null;
            }

            validatePaymentMethod(pmId, context);

            addCustomField(subId, pmId, context);
        }
        return null;
    }

    private void validatePaymentMethod(final UUID pmId, final EntitlementContext context) throws EntitlementPluginApiException {

        try {
            killbillAPI.getPaymentApi().getPaymentMethodById(pmId, false, false, Collections.emptyList(), context);
        } catch (final PaymentApiException e) {
            throw new EntitlementPluginApiException("Failed to find payment method",  e);
        }
    }

    @Override
    public OnFailureEntitlementResult onFailureCall(final EntitlementContext context, final Iterable<PluginProperty> properties) throws EntitlementPluginApiException {
        return null;
    }

    private void addCustomField(final UUID subscriptionId, final UUID paymentMethodId, final CallContext context) throws EntitlementPluginApiException {

        final CustomField field = new PluginCustomField(subscriptionId, ObjectType.SUBSCRIPTION, PM_ID, paymentMethodId.toString(), clock.getClock().getUTCNow());
        final List<CustomField> existings = killbillAPI.getCustomFieldUserApi().getCustomFieldsForObject(subscriptionId, ObjectType.SUBSCRIPTION, context);
        final CustomField found = existings.stream()
                                           .filter(f -> f.getFieldName().equals(PM_ID))
                                           .findFirst()
                                           .orElse(null);
        if (found != null) {
            logger.warn("Custom field for subscription {} already exists, skip...", subscriptionId);
            return;
        }

        try {
            killbillAPI.getSecurityApi().login(USER, PWD);
            killbillAPI.getCustomFieldUserApi().addCustomFields(Collections.singletonList(field), context);
        } catch (final CustomFieldApiException e) {
            throw new EntitlementPluginApiException("Failed to add custom field", e);
        } finally {
            killbillAPI.getSecurityApi().logout();
        }
    }

    private UUID getSubscriptionId(final EntitlementContext context) {

        if (context.getBaseEntitlementWithAddOnsSpecifiers().iterator().hasNext()) {
            final BaseEntitlementWithAddOnsSpecifier bundleSpec = context.getBaseEntitlementWithAddOnsSpecifiers().iterator().next();
            if (bundleSpec.getEntitlementSpecifier().iterator().hasNext()) {
                final EntitlementSpecifier spec = bundleSpec.getEntitlementSpecifier().iterator().next();
                try {
                    final Subscription sub = killbillAPI.getSubscriptionApi().getSubscriptionForExternalKey(spec.getExternalKey(), context);
                    return sub.getId();
                } catch (final SubscriptionApiException e) {
                    logger.warn("Failed to get subscription for key {}", spec.getExternalKey(), e);
                }
            }
        }
        return null;
    }

    private UUID getPaymentMethodId(final Iterable<PluginProperty> properties) {
        final PluginProperty prop = StreamSupport.stream(properties.spliterator(), false)
                                                 .filter(p -> p.getKey().equals(PM_ID))
                                                 .findFirst()
                                                 .orElse(null);
        return prop != null ? UUID.fromString((String) prop.getValue()) : null;
    }
}
