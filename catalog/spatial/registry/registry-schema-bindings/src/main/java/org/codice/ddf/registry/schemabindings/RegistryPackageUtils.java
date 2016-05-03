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
package org.codice.ddf.registry.schemabindings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBElement;

import org.apache.commons.collections.CollectionUtils;
import org.codice.ddf.registry.common.RegistryConstants;

import oasis.names.tc.ebxml_regrep.xsd.rim._3.AssociationType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.InternationalStringType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.LocalizedStringType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ObjectFactory;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.OrganizationType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.PersonType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ServiceBindingType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ServiceType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ValueListType;

public final class RegistryPackageUtils {

    private static final ObjectFactory RIM_FACTORY = new ObjectFactory();

    private static final String BINDING_TYPE = "bindingType";

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
        return getObjectsFromRegistryObjectList(registryPackage, ServiceType.class);
    }

    public static List<ServiceType> getServices(RegistryObjectListType registryObjectList) {
        return getObjectsFromRegistryObjectList(registryObjectList, ServiceType.class);
    }

    public static List<ExtrinsicObjectType> getExtrinsicObjects(
            RegistryPackageType registryPackage) {
        return getObjectsFromRegistryObjectList(registryPackage, ExtrinsicObjectType.class);
    }

    public static List<ExtrinsicObjectType> getExtrinsicObjects(
            RegistryObjectListType registryObjectList) {
        return getObjectsFromRegistryObjectList(registryObjectList, ExtrinsicObjectType.class);
    }

    public static List<OrganizationType> getOrganizations(RegistryPackageType registryPackage) {
        return getObjectsFromRegistryObjectList(registryPackage, OrganizationType.class);
    }

    public static List<OrganizationType> getOrganizations(
            RegistryObjectListType registryObjectList) {
        return getObjectsFromRegistryObjectList(registryObjectList, OrganizationType.class);
    }

    public static List<PersonType> getPersons(RegistryPackageType registryPackage) {
        return getObjectsFromRegistryObjectList(registryPackage, PersonType.class);
    }

    public static List<PersonType> getPersons(RegistryObjectListType registryObjectList) {
        return getObjectsFromRegistryObjectList(registryObjectList, PersonType.class);
    }

    public static List<AssociationType1> getAssociations(RegistryPackageType registryPackage) {
        return getObjectsFromRegistryObjectList(registryPackage, AssociationType1.class);
    }

    public static List<AssociationType1> getAssociations(RegistryObjectListType registryObjectList) {
        return getObjectsFromRegistryObjectList(registryObjectList, AssociationType1.class);
    }

    public static <T extends RegistryObjectType> List<T> getObjectsFromRegistryObjectList(RegistryPackageType registryPackage, Class<T> type) {
        List<T> registryObjects = new ArrayList<>();

        if (registryPackage == null) {
            return registryObjects;
        }

        if (registryPackage.isSetRegistryObjectList()) {
            registryObjects = getObjectsFromRegistryObjectList(registryPackage.getRegistryObjectList(), type);
        }

        return registryObjects;
    }

    public static <T extends RegistryObjectType> List<T> getObjectsFromRegistryObjectList(
            RegistryObjectListType registryObjectList, Class<T> type) {
        List<T> registryObjects = new ArrayList<>();

        if (registryObjectList == null) {
            return registryObjects;
        }

        registryObjects.addAll(registryObjectList.getIdentifiable()
                .stream()
                .filter(identifiable -> type.isInstance(identifiable.getValue()))
                .map(identifiable -> (T) identifiable.getValue())
                .collect(Collectors.toList()));

        return registryObjects;
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

    public static Map<String, List<SlotType1>> getNameSlotMapWithMultipleValues(List<SlotType1> slots) {
        Map<String, List<SlotType1>> slotMap = new HashMap<>();

        for (SlotType1 slot : slots) {
            slotMap.putIfAbsent(slot.getName(), new ArrayList<>());
            slotMap.get(slot.getName()).add(slot);
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
