package org.killbill.billing.plugin.invgrp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.killbill.billing.invoice.api.Invoice;
import org.killbill.billing.invoice.api.InvoiceItem;
import org.killbill.billing.invoice.plugin.api.InvoiceContext;
import org.killbill.billing.invoice.plugin.api.InvoiceGroup;
import org.killbill.billing.invoice.plugin.api.InvoiceGroupingResult;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.invoice.plugin.api.OnFailureInvoiceResult;
import org.killbill.billing.invoice.plugin.api.OnSuccessInvoiceResult;
import org.killbill.billing.invoice.plugin.api.PriorInvoiceResult;
import org.killbill.billing.payment.api.PluginProperty;
import org.killbill.billing.plugin.api.invoice.PluginOnFailureInvoiceResult;
import org.killbill.billing.plugin.api.invoice.PluginOnSuccessInvoiceResult;
import org.killbill.billing.plugin.api.invoice.PluginPriorInvoiceResult;
import org.killbill.billing.util.callcontext.CallContext;

public class InvgrpInvoicePluginApi implements InvoicePluginApi {

    public InvgrpInvoicePluginApi() {
    }

    @Override
    public PriorInvoiceResult priorCall(final InvoiceContext context, final Iterable<PluginProperty> properties) {
        return new PluginPriorInvoiceResult();
    }

    @Override
    public List<InvoiceItem> getAdditionalInvoiceItems(final Invoice invoice, final boolean dryRun, final Iterable<PluginProperty> properties, final CallContext context) {
        return Collections.emptyList();
    }

    private static class InvgrpInvoiceGroupingResult implements InvoiceGroupingResult {

        private List<InvoiceGroup> invoiceGroups;

        public InvgrpInvoiceGroupingResult(final Map<UUID, List<UUID>> groups) {
            this.invoiceGroups = initGroups(groups);
        }

        private List<InvoiceGroup> initGroups(final Map<UUID, List<UUID>> groups) {
            final List<InvoiceGroup> tmp = new ArrayList<>();
            groups.values()
                  .stream()
                  .forEach(v -> tmp.add(new PluginInvoiceGroup(v)));
            return tmp;
        }

        @Override
        public List<InvoiceGroup> getInvoiceGroups() {
            return invoiceGroups;
        }

        private static class PluginInvoiceGroup implements InvoiceGroup {

            private final List<UUID> invoiceItemIds;

            public PluginInvoiceGroup(final List<UUID> invoiceItemIds) {
                this.invoiceItemIds = invoiceItemIds;
            }

            @Override
            public List<UUID> getInvoiceItemIds() {
                return invoiceItemIds;
            }
        }
    }

    @Override
    public InvoiceGroupingResult getInvoiceGrouping(final Invoice invoice, final boolean dryRun, final Iterable<PluginProperty> properties, final CallContext context) {

        final Map<UUID, List<UUID>> groups = new HashMap<UUID, List<UUID>>();
        for (InvoiceItem ii : invoice.getInvoiceItems()) {
            final UUID groupId = findGroup(invoice, ii);
            if (groups.get(groupId) == null) {
                groups.put(groupId, new ArrayList<>());
            }
            groups.get(groupId).add(ii.getId());
        }
        return new InvgrpInvoiceGroupingResult(groups);
    }

    private UUID findGroup(Invoice inv, InvoiceItem item) {
        if (item.getSubscriptionId() != null) {
            return item.getSubscriptionId();
        } else if (item.getLinkedItemId() != null) {
            final InvoiceItem target = inv.getInvoiceItems()
                                          .stream()
                                          .filter(ii -> ii.getId().equals(item.getLinkedItemId()))
                                          .findAny()
                                          .orElseThrow();
            return target.getSubscriptionId();
        } else {
            throw new IllegalStateException("Unexpected item not related to subscription ii=" + item);
        }
    }

    @Override
    public OnSuccessInvoiceResult onSuccessCall(final InvoiceContext context, final Iterable<PluginProperty> properties) {
        return new PluginOnSuccessInvoiceResult();
    }

    @Override
    public OnFailureInvoiceResult onFailureCall(final InvoiceContext context, final Iterable<PluginProperty> properties) {
        return new PluginOnFailureInvoiceResult();
    }
}
