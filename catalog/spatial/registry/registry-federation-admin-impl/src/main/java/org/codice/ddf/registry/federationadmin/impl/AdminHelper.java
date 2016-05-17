/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.registry.federationadmin.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.codice.ddf.registry.api.RegistryStore;
import org.codice.ddf.ui.admin.api.ConfigurationAdminExt;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.metatype.ObjectClassDefinition;

import ddf.catalog.service.ConfiguredService;
import ddf.catalog.source.Source;

public class AdminHelper {
    private static final String REGISTRY_FILTER =
            "(|(service.factoryPid=*Registry*Store*)(service.factoryPid=*registry*store*))";

    private static final String MAP_ENTRY_ID = "id";

    private static final String DISABLED = "_disabled";

    private ConfigurationAdmin configurationAdmin;

    public AdminHelper(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    private BundleContext getBundleContext() {
        Bundle bundle = FrameworkUtil.getBundle(this.getClass());
        if (bundle != null) {
            return bundle.getBundleContext();
        }
        return null;
    }

    public List<Source> getRegistrySources() throws org.osgi.framework.InvalidSyntaxException {
        List<Source> sources = new ArrayList<>();
        List<ServiceReference<? extends Source>> refs = new ArrayList<>();
        refs.addAll(getBundleContext().getServiceReferences(RegistryStore.class, null));

        for (ServiceReference<? extends Source> ref : refs) {
            sources.add(getBundleContext().getService(ref));
        }

        return sources;
    }

    public List<Map<String, Object>> getMetatypes() {
        ConfigurationAdminExt configAdminExt = new ConfigurationAdminExt(configurationAdmin);
        return configAdminExt.addMetaTypeNamesToMap(configAdminExt.getFactoryPidObjectClasses(),
                REGISTRY_FILTER,
                "service.factoryPid");
    }

    public List getConfigurations(Map metatype) throws InvalidSyntaxException, IOException {
        return org.apache.shiro.util.CollectionUtils.asList(configurationAdmin.listConfigurations(
                "(|(service.factoryPid=" + metatype.get(MAP_ENTRY_ID) + ")(service.factoryPid="
                        + metatype.get(MAP_ENTRY_ID) + DISABLED + "))"));
    }

    public Configuration getConfiguration(ConfiguredService cs) throws IOException {
        return configurationAdmin.getConfiguration(cs.getConfigurationPid());
    }

    public String getBundleName(Configuration config) {
        ConfigurationAdminExt configAdminExt = new ConfigurationAdminExt(configurationAdmin);
        return configAdminExt.getName(getBundleContext().getBundle(config.getBundleLocation()));
    }

    public long getBundleId(Configuration config) {
        return getBundleContext().getBundle(config.getBundleLocation())
                .getBundleId();
    }

    public String getName(Configuration config) {
        ConfigurationAdminExt configAdminExt = new ConfigurationAdminExt(configurationAdmin);
        return ((ObjectClassDefinition) configAdminExt.getFactoryPidObjectClasses()
                .get(config.getFactoryPid())).getName();
    }
}
