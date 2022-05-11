/*
 * Copyright 2010-2014 Ning, Inc.
 * Copyright 2014-2020 Groupon, Inc
 * Copyright 2020-2020 Equinix, Inc
 * Copyright 2014-2020 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
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

        // As an example, this plugin registers a PaymentPluginApi (this could be changed to any other plugin api)
        final PaymentControlPluginApi paymentControlPluginApi = new InvgrpPaymentControlPluginApi(killbillAPI);
        registerPaymentControlPluginApi(context, paymentControlPluginApi);

        final EntitlementPluginApi entitlementPluginApi = new InvgrpEntitlementPluginApi(killbillAPI, clock);
        registerEntitlementPluginApi(context, entitlementPluginApi);

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
