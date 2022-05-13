/*
 * Copyright 2022 The Billing Project, LLC - All Rights Reserved
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package org.killbill.billing.plugin.invgrp;

import java.util.Properties;

import org.killbill.billing.osgi.libs.killbill.OSGIKillbillAPI;
import org.killbill.billing.plugin.api.notification.PluginTenantConfigurableConfigurationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When per-tenant config changes are made, the plugin automatically gets notified (and prints a log trace)
 * <pre>
 * {@code
 * curl -v \
 *      -X POST \
 *      -u admin:password \
 *      -H "Content-Type: text/plain" \
 *      -H "X-Killbill-ApiKey: bob" \
 *      -H "X-Killbill-ApiSecret: lazar" \
 *      -H "X-Killbill-CreatedBy: demo" \
 *      -d 'key1=foo1
 * key2=foo2' \
 *      "http://127.0.0.1:8080/1.0/kb/tenants/uploadPluginConfig/invgrp-plugin"
 * }
 * </pre>
 */
public class InvgrpConfigurationHandler extends PluginTenantConfigurableConfigurationHandler<Properties> {

    private static final Logger logger = LoggerFactory.getLogger(InvgrpConfigurationHandler.class);

    private final String region;

    public InvgrpConfigurationHandler(final String region,
                                          final String pluginName,
                                          final OSGIKillbillAPI osgiKillbillAPI) {
        super(pluginName, osgiKillbillAPI);
        this.region = region;
    }

    @Override
    protected Properties createConfigurable(final Properties properties) {
        logger.info("New properties for region {}: {}", region, properties);
        return properties;
    }
}
