/**
 * Copyright (c) Codice Foundation
 * <p/>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.registry.report.action.provider;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.codice.ddf.configuration.SystemBaseUrl;
import org.codice.ddf.registry.common.RegistryConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ddf.action.Action;
import ddf.action.ActionProvider;
import ddf.action.impl.ActionImpl;
import ddf.catalog.data.Metacard;

public class RegistryMetacardActionProvider implements ActionProvider {

    public static final String TITLE = "Export Registry Metacard Information";

    public static final String DESCRIPTION = "Provides a URL to the metacard";

    private static final String UNKNOWN_TARGET = "0.0.0.0";

    private static final String REGISTRY_PATH = "/registries";

    private static final String REPORT_PATH = "/report";

    private static final String FORMAT = ".html";

    private static final String SOURCE_ID_QUERY_PARAM = "?sourceId=";

    private final String actionProviderId;

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RegistryMetacardActionProvider.class);

    public RegistryMetacardActionProvider(String id) {
        this.actionProviderId = id;
    }

    public <T> List<Action> getActions(T subject) {

        if (canHandle(subject)) {
            Metacard metacard = (Metacard) subject;

            if (StringUtils.isBlank(metacard.getId())) {
                LOGGER.info("No id given. No action to provide.");
                return Collections.emptyList();
            }

            if (isHostUnset(SystemBaseUrl.getHost())) {
                LOGGER.info("Host name/ip not set. Cannot create link for metacard.");
                return Collections.emptyList();
            }

            String metacardId;
            String sourceId;

            try {
                metacardId = URLEncoder.encode(metacard.getId(), (StandardCharsets.UTF_8).toString());
                sourceId = URLEncoder.encode(getSource(metacard), (StandardCharsets.UTF_8).toString());
            } catch (UnsupportedEncodingException e) {
                LOGGER.info("Unsupported Encoding exception", e);
                return Collections.emptyList();
            }

            Action action = getAction(metacardId, sourceId);
            if (action == null) {
                return Collections.emptyList();
            }
            return Arrays.asList(action);
        }

        return Collections.emptyList();
    }

    private Action getAction(String metacardId, String sourceId) {

        if(StringUtils.isNotBlank(sourceId)) {
            sourceId = SOURCE_ID_QUERY_PARAM + sourceId;
        }

        URL url;
        try {

            URI uri = new URI(SystemBaseUrl.constructUrl(REGISTRY_PATH + "/" + metacardId + REPORT_PATH + FORMAT + sourceId,
                    true));
            url = uri.toURL();

        } catch (MalformedURLException e) {
            LOGGER.info("Malformed URL exception", e);
            return null;
        } catch (URISyntaxException e) {
            LOGGER.info("URI Syntax exception", e);
            return null;
        }

        return new ActionImpl(getId(), TITLE, DESCRIPTION, url);
    }

    private boolean isHostUnset(String host) {
        return (host == null || host.trim()
                .equals(UNKNOWN_TARGET));
    }

    public String getId() {
        return this.actionProviderId;
    }

    public <T> boolean canHandle(T subject) {

        return subject instanceof Metacard && ((Metacard) subject).getTags()
                .contains(RegistryConstants.REGISTRY_TAG);

    }

    protected String getSource(Metacard metacard) {

        if (StringUtils.isNotBlank(metacard.getSourceId())) {
            return metacard.getSourceId();
        }

        return "";
    }
}
