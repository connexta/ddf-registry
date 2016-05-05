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

package org.codice.ddf.registry.report.viewer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBElement;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.registry.common.RegistryConstants;
import org.codice.ddf.registry.federationadmin.service.FederationAdminException;
import org.codice.ddf.registry.federationadmin.service.FederationAdminService;
import org.codice.ddf.registry.schemabindings.RegistryPackageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;

import oasis.names.tc.ebxml_regrep.xsd.rim._3.EmailAddressType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ExtrinsicObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.OrganizationType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.PersonNameType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.PersonType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.PostalAddressType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryPackageType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ServiceBindingType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ServiceType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.TelephoneNumberType;

@Path("/")
public class RegistryReportViewer {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegistryReportViewer.class);

    private String registryName = "";

    private static final String BINDING_TYPE = "bindingType";

    private FederationAdminService federationAdminService;

    private ClassPathTemplateLoader templateLoader;

    public RegistryReportViewer() {
        templateLoader = new ClassPathTemplateLoader();
        templateLoader.setPrefix("/templates");
        templateLoader.setSuffix(".hbt");
    }

    @Path("/{metacardId}/report")
    @Produces(MediaType.TEXT_HTML)
    @GET
    public Response viewRegInfoHtml(@PathParam("metacardId") final String metacardId,
            @QueryParam("sourceId") final List<String> sourceIds) {
        Handlebars handlebars = new Handlebars(templateLoader);
        String html = "";
        if (StringUtils.isBlank(metacardId)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Metacard Id cannot be blank")
                    .build();
        }

        RegistryPackageType registryPackage;
        try {
            registryPackage = federationAdminService.getRegistryObjectByMetacardId(metacardId,
                    sourceIds);
        } catch (FederationAdminException e) {
            String message = "Error getting registry package.";
            LOGGER.error("{} For metacard id: '{}', optional sources: {}",
                    message,
                    metacardId,
                    sourceIds);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(message)
                    .build();
        }

        if (registryPackage == null) {
            String message = "No registry package was found.";
            LOGGER.error("{} For metacard id: '{}', optional source ids: {}.",
                    message,
                    metacardId,
                    sourceIds);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(message)
                    .build();
        }

        Map<String, Object> registryMap = new HashMap<>();

        Map<String, Object> extrinsicInfo = getExtrinsicInfo(registryPackage);
        Map<String, Object> generalMap = (Map<String, Object>) extrinsicInfo.get("General");
        Map<String, Object> collectionMap = (Map<String, Object>) extrinsicInfo.get("Collections");

        registryMap.put("Name", registryName);
        registryMap.put("General", generalMap);
        registryMap.put("Collections", collectionMap);

        Map<String, Object> serviceMap = getServiceInfo(registryPackage.getRegistryObjectList());
        registryMap.put("Services", serviceMap);

        Map<String, Object> organizationMap = getOrganizationInfo(registryPackage);
        registryMap.put("Organizations", organizationMap);

        Map<String, Object> contactMap = getContactInfo(registryPackage);
        registryMap.put("Contacts", contactMap);

        try {
            Template template = handlebars.compile("report");
            html = template.apply(registryMap);
        } catch (IOException e) {
        }

        return Response.ok(html)
                .build();
    }

    private Map<String, Object> getServiceInfo(RegistryObjectListType registryObjectListType) {
        Map<String, Object> serviceInfo = new HashMap<>();
        for (JAXBElement id : registryObjectListType.getIdentifiable()) {
            RegistryObjectType registryObjectType = (RegistryObjectType) id.getValue();
            if (registryObjectType instanceof ServiceType
                    && RegistryConstants.REGISTRY_SERVICE_OBJECT_TYPE.equals(registryObjectType.getObjectType())) {
                ServiceType service = (ServiceType) registryObjectType;

                String serviceName = "Service";
                if (service.isSetName()) {
                    serviceName = RegistryPackageUtils.getStringFromIST(service.getName());
                }
                Map<String, Object> serviceProperties = new HashMap<>();
                for (ServiceBindingType binding : service.getServiceBinding()) {
                    Map<String, Object> bindingProperties;
                    String bindingType;
                    bindingProperties = getCustomSlots(binding.getSlot());
                    if (!bindingProperties.containsKey(BINDING_TYPE) || bindingProperties.get(
                            BINDING_TYPE) == null) {
                        continue;
                    }
                    bindingType = bindingProperties.get(BINDING_TYPE)
                            .toString();
                    bindingProperties.remove(BINDING_TYPE);
                    serviceProperties.put(bindingType, bindingProperties);
                }
                serviceInfo.put(serviceName, serviceProperties);
            }
        }
        return serviceInfo;
    }

    private Map<String, Object> getExtrinsicInfo(RegistryPackageType registryPackage) {
        Map<String, Object> generalMap = new HashMap<>();
        Map<String, Object> collectionMap = new HashMap<>();

        List<ExtrinsicObjectType> extrinsicObjectTypes = RegistryPackageUtils.getExtrinsicObjects(
                registryPackage);
        for (ExtrinsicObjectType extrinsicObjectType : extrinsicObjectTypes) {
            if (extrinsicObjectType.isSetObjectType()) {
                if (extrinsicObjectType.getObjectType()
                        .equals(RegistryConstants.REGISTRY_NODE_OBJECT_TYPE)) {
                    if (extrinsicObjectType.isSetName()) {
                        registryName =
                                RegistryPackageUtils.getStringFromIST(extrinsicObjectType.getName());

                    }
                    if (extrinsicObjectType.isSetVersionInfo()) {
                        generalMap.put("Version Info",
                                extrinsicObjectType.getVersionInfo()
                                        .getVersionName());
                    }
                    if (extrinsicObjectType.isSetDescription()) {
                        generalMap.put("Description", RegistryPackageUtils.getStringFromIST(
                                extrinsicObjectType.getDescription()));
                    }
                    Map<String, Object> customSlots = getCustomSlots(extrinsicObjectType.getSlot());
                    generalMap.putAll(customSlots);
                } else {
                    Map<String, Object> collectionValues = new HashMap<>();

                    collectionValues.putAll(getCustomSlots(extrinsicObjectType.getSlot()));

                    String collectionName = "";
                    if (extrinsicObjectType.isSetName()) {
                        collectionName =
                                RegistryPackageUtils.getStringFromIST(extrinsicObjectType.getName());
                    }

                    collectionMap.put(collectionName, collectionValues);
                }
            } else {
                Map<String, Object> collectionValues = new HashMap<>();

                collectionValues.putAll(getCustomSlots(extrinsicObjectType.getSlot()));

                String collectionName = "";
                if (extrinsicObjectType.isSetName()) {
                    collectionName =
                            RegistryPackageUtils.getStringFromIST(extrinsicObjectType.getName());
                }

                collectionMap.put(collectionName, collectionValues);
            }
        }

        Map<String, Object> extrinsicInfoMap = new HashMap<>();
        extrinsicInfoMap.put("General", generalMap);
        extrinsicInfoMap.put("Collections", collectionMap);

        return extrinsicInfoMap;
    }

    private Map<String, Object> getOrganizationInfo(RegistryPackageType registryPackage) {
        Map<String, Object> organizationMap = new HashMap<>();

        List<OrganizationType> organizationTypes = RegistryPackageUtils.getOrganizations(
                registryPackage);
        for (OrganizationType organizationType : organizationTypes) {
            Map<String, Object> organizationInfo = new HashMap<>();
            Map<String, Object> contactInfo = new HashMap<>();

            String orgName = "";

            if (organizationType.isSetName()) {
                orgName = RegistryPackageUtils.getStringFromIST(organizationType.getName());
            }
            if (organizationType.isSetAddress()) {
                List<String> orgAddresses = getAddresses(organizationType.getAddress());
                contactInfo.put("Addresses", orgAddresses);
            }
            if (organizationType.isSetTelephoneNumber()) {
                List<String> phoneNumbers = getPhoneNumbers(organizationType.getTelephoneNumber());
                contactInfo.put("Phone Numbers", phoneNumbers);
            }
            if (organizationType.isSetEmailAddress()) {
                List<String> emailAddresses = getEmailAddresses(organizationType.getEmailAddress());
                contactInfo.put("Email Addresses", emailAddresses);
            }

            organizationInfo.put("ContactInfo", contactInfo);
            organizationInfo.put("CustomSlots", getCustomSlots(organizationType.getSlot()));
            organizationMap.put(orgName, organizationInfo);
        }

        return organizationMap;
    }

    private Map<String, Object> getContactInfo(RegistryPackageType registryPackage) {
        Map<String, Object> contactMap = new HashMap<>();
        List<PersonType> persons = RegistryPackageUtils.getPersons(registryPackage);
        for (PersonType person : persons) {
            Map<String, Object> personMap = new HashMap<>();
            Map<String, Object> contactInfo = new HashMap<>();

            PersonNameType personName = person.getPersonName();
            String firstName = "";
            String middleName = "";
            String lastName = "";
            if (personName.isSetFirstName()) {
                firstName = personName.getFirstName() + " ";
            }
            if (personName.isSetMiddleName()) {
                middleName = personName.getMiddleName() + " ";
            }
            if (personName.isSetLastName()) {
                lastName = personName.getLastName();
            }
            String name = firstName + middleName + lastName;

            if (person.isSetEmailAddress()) {
                List<String> emailAddresses = getEmailAddresses(person.getEmailAddress());
                contactInfo.put("Email Addresses", emailAddresses);
            }

            if (person.isSetTelephoneNumber()) {
                List<String> phoneNumbers = getPhoneNumbers(person.getTelephoneNumber());
                contactInfo.put("Phone Numbers", phoneNumbers);
            }
            if (person.isSetAddress()) {
                List<String> personAddresses = getAddresses(person.getAddress());
                contactInfo.put("Addresses", personAddresses);
            }
            personMap.put("ContactInfo", contactInfo);
            personMap.put("CustomSlots", getCustomSlots(person.getSlot()));
            contactMap.put(name, personMap);
        }

        return contactMap;
    }

    private List<String> getAddresses(List<PostalAddressType> addressTypes) {
        List<String> addresses = new ArrayList<>();
        for (PostalAddressType address : addressTypes) {
            String city = "";
            String street = "";
            String stateOrProvince = "";
            String postalCode = "";
            String country = "";
            if (address.isSetCity()) {
                city = address.getCity() + " ";
            }
            if (address.isSetCountry()) {
                country = address.getCountry();
            }
            if (address.isSetPostalCode()) {
                postalCode = address.getPostalCode() + " ";
            }
            if (address.isSetStateOrProvince()) {
                stateOrProvince = address.getStateOrProvince() + " ";
            }
            if (address.isSetStreet()) {
                street = address.getStreet() + " ";
            }

            String addressString = street + city + stateOrProvince + postalCode + country;
            addresses.add(addressString);
        }

        return addresses;
    }

    private List<String> getPhoneNumbers(List<TelephoneNumberType> telephoneNumberTypes) {
        List<String> phoneNumbers = new ArrayList<>();
        for (TelephoneNumberType telephoneNumberType : telephoneNumberTypes) {
            String areaCode = "";
            String countryCode = "";
            String extension = "";
            String number = "";
            String phoneType = "";
            if (telephoneNumberType.isSetAreaCode()) {
                areaCode = telephoneNumberType.getAreaCode() + "-";
            }
            if (telephoneNumberType.isSetCountryCode()) {
                countryCode = telephoneNumberType.getCountryCode() + "-";
            }
            if (telephoneNumberType.isSetExtension()) {
                extension = " ext. " + telephoneNumberType.getExtension();
            }
            if (telephoneNumberType.isSetNumber()) {
                number = telephoneNumberType.getNumber();
            }
            if (telephoneNumberType.isSetPhoneType()) {
                phoneType = " (" + telephoneNumberType.getPhoneType() + ")";
            }
            String phoneNumber = countryCode + areaCode + number + extension + phoneType;
            phoneNumbers.add(phoneNumber);
        }
        return phoneNumbers;
    }

    private List<String> getEmailAddresses(List<EmailAddressType> emailAddressTypes) {
        List<String> emailAddresses = new ArrayList<>();

        for (EmailAddressType emailAddressType : emailAddressTypes) {
            emailAddresses.add(emailAddressType.getAddress());
        }
        return emailAddresses;
    }

    private Map<String, Object> getCustomSlots(List<SlotType1> slots) {
        Map<String, Object> customSlotMap = new HashMap<>();

        for (SlotType1 slot : slots) {
            String key = slot.getName();
            List<String> values = slot.getValueList()
                    .getValue()
                    .getValue();
            if (CollectionUtils.isEmpty(values)) {
                continue;
            }
            customSlotMap.put(key, StringUtils.join(values, ", "));
        }
        return customSlotMap;
    }

    public void setFederationAdminService(FederationAdminService federationAdminService) {
        this.federationAdminService = federationAdminService;
    }
}
