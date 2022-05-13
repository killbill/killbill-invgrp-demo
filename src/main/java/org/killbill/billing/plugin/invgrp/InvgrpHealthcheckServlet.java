/*
 * Copyright 2022 The Billing Project, LLC - All Rights Reserved
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package org.killbill.billing.plugin.invgrp;

import java.util.Optional;

import javax.inject.Named;
import javax.inject.Singleton;

import org.jooby.Result;
import org.jooby.mvc.GET;
import org.jooby.mvc.Local;
import org.jooby.mvc.Path;
import org.killbill.billing.plugin.core.resources.PluginHealthcheck;
import org.killbill.billing.tenant.api.Tenant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Inject;

@Singleton
@Path("/healthcheck")
public class InvgrpHealthcheckServlet extends PluginHealthcheck {

    private final InvgrpHealthcheck healthcheck;

    @Inject
    public InvgrpHealthcheckServlet(final InvgrpHealthcheck healthcheck) {
        this.healthcheck = healthcheck;
    }

    @GET
    public Result check(@Local @Named("killbill_tenant") final Optional<Tenant> tenant) throws JsonProcessingException {
        return check(healthcheck, tenant.orElse(null), null);
    }
}
