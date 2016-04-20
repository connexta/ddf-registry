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
 **/
package org.codice.ddf.registry.schemabindings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;

import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.InternationalStringType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.LocalizedStringType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ObjectFactory;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ServiceBindingType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ServiceType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ValueListType;

public final class RegistryPackageUtils {

    private static final ObjectFactory RIM_FACTORY = new ObjectFactory();

    private RegistryPackageUtils() {
    }

    public static List<ServiceBindingType> getBindingTypes(RegistryPackageType registryPackage) {
        List<ServiceBindingType> bindingTypes = new ArrayList<>();
        if (registryPackage == null) {
            return bindingTypes;
        }

        for (ServiceType service : getServices(registryPackage)) {
            bindingTypes.addAll(service.getServiceBinding());
        }

        return bindingTypes;
    }

    public static List<ServiceBindingType> getBindingTypes(
            RegistryObjectListType registryObjectList) {
        List<ServiceBindingType> bindingTypes = new ArrayList<>();
        if (registryObjectList == null) {
            return bindingTypes;
        }

        for (ServiceType service : getServices(registryObjectList)) {
            bindingTypes.addAll(service.getServiceBinding());
        }

        return bindingTypes;
    }

    public static List<ServiceType> getServices(RegistryPackageType registryPackage) {
        List<ServiceType> services = new ArrayList<>();

        if (registryPackage == null) {
            return services;
        }

        if (registryPackage.isSetRegistryObjectList()) {
            services = getServices(registryPackage.getRegistryObjectList());
        }

        return services;
    }

    public static List<ServiceType> getServices(RegistryObjectListType registryObjectList) {
        List<ServiceType> services = new ArrayList<>();

        if (registryObjectList == null) {
            return services;
        }

        services.addAll(registryObjectList.getIdentifiable()
                .stream()
                .filter(identifiable -> identifiable.getValue() instanceof ServiceType)
                .map(identifiable -> (ServiceType) identifiable.getValue())
                .collect(Collectors.toList()));

        return services;
    }

    public static List<ExtrinsicObjectType> getExtrinsicObjects(RegistryPackageType registryPackage) {
        List<ExtrinsicObjectType> extrinsicObjects = new ArrayList<>();

        if (registryPackage == null) {
            return extrinsicObjects;
        }

        if (registryPackage.isSetRegistryObjectList()) {
            extrinsicObjects = getExtrinsicObjects(registryPackage.getRegistryObjectList());
        }

        return extrinsicObjects;
    }

    public static List<ExtrinsicObjectType> getExtrinsicObjects(RegistryObjectListType registryObjectList) {
        List<ExtrinsicObjectType> extrinsicObject = new ArrayList<>();

        if (registryObjectList == null) {
            return extrinsicObject;
        }

        extrinsicObject.addAll(registryObjectList.getIdentifiable()
                .stream()
                .filter(identifiable -> identifiable.getValue() instanceof ExtrinsicObjectType)
                .map(identifiable -> (ExtrinsicObjectType) identifiable.getValue())
                .collect(Collectors.toList()));

        return extrinsicObject;
    }

    public static SlotType1 getSlotByName(String name, List<SlotType1> slots) {
        for (SlotType1 slot : slots) {
            if (slot.getName()
                    .equals(name)) {
                return slot;
            }
        }

        return null;
    }

    public static Map<String, SlotType1> getNameSlotMap(List<SlotType1> slots) {
        Map<String, SlotType1> slotMap = new HashMap<>();

        for (SlotType1 slot : slots) {
            slotMap.put(slot.getName(), slot);
        }
        return slotMap;
    }

    public static List<String> getSlotStringAttributes(SlotType1 slot) {
        List<String> slotAttributes = new ArrayList<>();

        if (slot.isSetValueList()) {
            ValueListType valueList = slot.getValueList()
                    .getValue();
            if (valueList.isSetValue()) {
                slotAttributes = valueList.getValue();
            }
        }

        return slotAttributes;
    }

    public static String getStringFromIST(InternationalStringType internationalString) {
        String stringValue = "";
        List<LocalizedStringType> localizedStrings = internationalString.getLocalizedString();
        if (CollectionUtils.isNotEmpty(localizedStrings)) {
            LocalizedStringType localizedString = localizedStrings.get(0);
            if (localizedString != null) {
                stringValue = localizedString.getValue();
            }
        }

        return stringValue;
    }

    public static InternationalStringType getInternationalStringTypeFromString(
            String internationalizeThis) {
        InternationalStringType ist = RIM_FACTORY.createInternationalStringType();
        LocalizedStringType lst = RIM_FACTORY.createLocalizedStringType();
        lst.setValue(internationalizeThis);
        ist.setLocalizedString(Collections.singletonList(lst));

        return ist;
    }
}
