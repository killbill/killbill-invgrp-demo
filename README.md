# killbill-invgrp-plugin


This plugin is a 'demo' plugin that shows how we can instruct the system to split 1 invoice into N invoices and 
then leverage the N invoices generated to make individual payments using different payment methods.

It is not meant to be used in production 'as-is'.

## Kill Bill compatibility
-----------------------

| Plugin version | Kill Bill version |
| -------------: | ----------------: |
| 1.x.y          | 0.24.z            |


## Plugin Internals and Logic


The plugin implements the following Kill Bill plugin apis:
* [EntitlementPluginApi](https://github.com/killbill/killbill-plugin-api/blob/master/entitlement/src/main/java/org/killbill/billing/entitlement/plugin/api/EntitlementPluginApi.java):
The goal is to intercept the subscription creation call to extract custom properties and create a mapping between the subscription being created and the payment method that should be used to make payments.
* [InvoicePluginApi](https://github.com/killbill/killbill-plugin-api/blob/master/invoice/src/main/java/org/killbill/billing/invoice/plugin/api/InvoicePluginApi.java):
  We are leveraging the new plugin api `InvoicePluginApi#getInvoiceGrouping` to be able to group certain items into their own specific invoices. We are therefore able to control how to split one incoming invoice into N invoices.
* [PaymentControlPluginApi](https://github.com/killbill/killbill-plugin-api/blob/master/control/src/main/java/org/killbill/billing/control/plugin/api/PaymentControlPluginApi.java):
We are using the plugin api to be able to dynamically update which payment method should be used against a given invoice.

The custom logic implemented in this plugin is to group each subscription on its own invoice, but of course this could be changed accordingly as the grouping logic lives inside the plugin.

There are 2 main differences on the client side:
1. When creating the subscription, one needs to specify the payment method to use by specifying the `PM_ID` plugin property.
2. When triggering an invoice run, one needs to use the new api [InvoiceUserApi#triggerInvoiceGroupGeneration](https://github.com/killbill/killbill-api/blob/work-for-release-0.23.x/src/main/java/org/killbill/billing/invoice/api/InvoiceUserApi.java#L192). 
This api is very similar to `InvoiceUserApi#triggerInvoiceGeneration` except it may return N invoices if there is a plugin configured and doing the splitting.

## Installation

This should be installed as any other plugin

To build, run `mvn clean install`. You can then install the plugin locally:

```
kpm install_java_plugin kb:invgrp --from-source-file target/invgrp-plugin-*-SNAPSHOT.jar --destination /var/tmp/bundles
```

## Setup

Kill Bill should be configured with the following additional properties:

```
org.killbill.payment.invoice.plugin=invgrp-plugin
org.killbill.payment.method.overwrite=true
```

# Test

We have created a test scenario to highlight the behavior of the plugin and to provide a starting point to integrate against it.

The [TestIntegration#testBasic](https://github.com/killbill/killbill-invgrp-demo/blob/f2f6d89b9676de7a00f73694e1d78374b31dfbd9/src/test/java/org/killbill/billing/plugin/invgrp/TestIntegration.java#L133) demonstrates (and validates) the scenario where we create each subscription with its own payment method, 
and where we end up with one separate invoice and one separate payment (with its own payment method) for each subscription.

In order to run the test, the assumption is that there is an instance of Kill Bill with the plugin installed and running one `127.0.0.1:8080`.


## About

Kill Bill is the leading Open-Source Subscription Billing & Payments Platform. For more information about the project, go to https://killbill.io/.
