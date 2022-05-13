/*
 * Copyright 2022 The Billing Project, LLC - All Rights Reserved
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package org.killbill.billing.plugin.invgrp;

import java.util.Hashtable;
import java.util.Properties;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;

import org.killbill.billing.control.plugin.api.PaymentControlPluginApi;
import org.killbill.billing.entitlement.plugin.api.EntitlementPluginApi;
import org.killbill.billing.invoice.plugin.api.InvoicePluginApi;
import org.killbill.billing.osgi.api.Healthcheck;
import org.killbill.billing.osgi.api.OSGIPluginProperties;
import org.killbill.billing.osgi.libs.killbill.KillbillActivatorBase;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillEventDispatcher;
import org.killbill.billing.osgi.libs.killbill.OSGIKillbillEventDispatcher.OSGIFrameworkEventHandler;
import org.killbill.billing.payment.plugin.api.PaymentPluginApi;
import org.killbill.billing.plugin.api.notification.PluginConfigurationEventHandler;
import org.killbill.billing.plugin.core.config.PluginEnvironmentConfig;
import org.killbill.billing.plugin.core.resources.jooby.PluginApp;
import org.killbill.billing.plugin.core.resources.jooby.PluginAppBuilder;
import org.osgi.framework.BundleContext;

public class InvgrpActivator extends KillbillActivatorBase {

    //
    // Ideally that string should match the pluginName on the filesystem, but there is no enforcement
    //
    public static final String PLUGIN_NAME = "invgrp-plugin";

    private InvgrpConfigurationHandler invgrpConfigurationHandler;
    private OSGIKillbillEventDispatcher.OSGIKillbillEventHandler killbillEventHandler;
    private MetricsGeneratorExample metricsGenerator;

    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);

        final String region = PluginEnvironmentConfig.getRegion(configProperties.getProperties());

        // Register an event listener for plugin configuration (optional)
        invgrpConfigurationHandler = new InvgrpConfigurationHandler(region, PLUGIN_NAME, killbillAPI);
        final Properties globalConfiguration = invgrpConfigurationHandler.createConfigurable(configProperties.getProperties());
        invgrpConfigurationHandler.setDefaultConfigurable(globalConfiguration);

        // Register an event listener (optional)
        killbillEventHandler = new InvgrpListener(killbillAPI);

        final InvoicePluginApi invoicePluginApi = new InvgrpInvoicePluginApi();
        registerInvoicePluginApi(context, invoicePluginApi);

        final PaymentControlPluginApi paymentControlPluginApi = new InvgrpPaymentControlPluginApi(killbillAPI);
        registerPaymentControlPluginApi(context, paymentControlPluginApi);

        final EntitlementPluginApi entitlementPluginApi = new InvgrpEntitlementPluginApi(killbillAPI, clock);
        registerEntitlementPluginApi(context, entitlementPluginApi);

        // This is not required for the logic of the plugin but useful to run the integration test
        final PaymentPluginApi paymentPluginApi = new InvgrpPaymentPluginApi();
        registerPaymentPluginApi(context, paymentPluginApi);

        // Expose metrics (optional)
        metricsGenerator = new MetricsGeneratorExample(metricRegistry);
        metricsGenerator.start();

        // Expose a healthcheck (optional), so other plugins can check on the plugin status
        final Healthcheck healthcheck = new InvgrpHealthcheck();
        registerHealthcheck(context, healthcheck);

        // Register a servlet (optional)
        final PluginApp pluginApp = new PluginAppBuilder(PLUGIN_NAME,
                                                         killbillAPI,
                                                         dataSource,
                                                         super.clock,
                                                         configProperties).withRouteClass(InvgrpServlet.class)
                                                                          .withRouteClass(InvgrpHealthcheckServlet.class)
                                                                          .withService(healthcheck)
                                                                          .build();
        final HttpServlet httpServlet = PluginApp.createServlet(pluginApp);
        registerServlet(context, httpServlet);

        registerHandlers();
    }


    @Override
    public void stop(final BundleContext context) throws Exception {
        // Do additional work on shutdown (optional)
        metricsGenerator.stop();
        super.stop(context);
    }

    private void registerHandlers() {
        final PluginConfigurationEventHandler configHandler = new PluginConfigurationEventHandler(invgrpConfigurationHandler);

        dispatcher.registerEventHandlers(configHandler,
                                         (OSGIFrameworkEventHandler) () -> dispatcher.registerEventHandlers(killbillEventHandler));
    }

    private void registerServlet(final BundleContext context, final Servlet servlet) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, Servlet.class, servlet, props);
    }

    private void registerPaymentControlPluginApi(final BundleContext context, final PaymentControlPluginApi api) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, PaymentControlPluginApi.class, api, props);
    }

    private void registerPaymentPluginApi(final BundleContext context, final PaymentPluginApi api) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, PaymentPluginApi.class, api, props);
    }

    private void registerInvoicePluginApi(final BundleContext context, final InvoicePluginApi api) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, InvoicePluginApi.class, api, props);
    }

    private void registerEntitlementPluginApi(final BundleContext context, final EntitlementPluginApi api) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, EntitlementPluginApi.class, api, props);
    }

    private void registerHealthcheck(final BundleContext context, final Healthcheck healthcheck) {
        final Hashtable<String, String> props = new Hashtable<String, String>();
        props.put(OSGIPluginProperties.PLUGIN_NAME_PROP, PLUGIN_NAME);
        registrar.registerService(context, Healthcheck.class, healthcheck, props);
    }
}
