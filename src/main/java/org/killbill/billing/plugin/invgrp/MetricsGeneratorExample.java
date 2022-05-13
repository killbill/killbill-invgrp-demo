/*
 * Copyright 2022 The Billing Project, LLC - All Rights Reserved
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package org.killbill.billing.plugin.invgrp;

import org.killbill.billing.osgi.libs.killbill.OSGIMetricRegistry;
import org.killbill.billing.osgi.libs.killbill.OSGIServiceNotAvailable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsGeneratorExample {

    private static final Logger logger = LoggerFactory.getLogger(MetricsGeneratorExample.class);

    private final Thread thread;

    private volatile boolean stopMetrics;

    public MetricsGeneratorExample(final OSGIMetricRegistry metricRegistry) {
        this.thread = new Thread(new Runnable() {
            public void run() {
                while (!stopMetrics) {
                    try {
                        Thread.sleep(1000L);
                    } catch (final InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        logger.info("MetricsGenerator shutting down");
                        break;
                    }

                    try {
                        metricRegistry.getMetricRegistry().counter("hello_counter").inc(1);
                    } catch (final OSGIServiceNotAvailable ignored) {
                        // No MetricRegistry available
                        logger.warn("No MetricRegistry available");
                    }
                }
            }
        });
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        stopMetrics = true;
    }
}
