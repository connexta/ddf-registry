/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/

//get descriptors for well known registry slot fields.
/*global define*/
define(['underscore'], function (_) {
    var FieldDescriptors = {
        isCustomizableSegment: function (name) {
            return _.contains(['General', 'Person', 'Service', 'ServiceBinding', 'Content', 'Organization'], name);
        },
        isContainerOnly: function (name) {
            return _.contains(['Address', 'TelephoneNumber', 'EmailAddress'], name);
        },

        getSlotTypes: function () {
            return {
                string: 'xs:string',
                date: 'xs:dateTime',
                number: 'xs:decimal',
                boolean: 'xs:boolean',
                point: 'urn:ogc:def:dataType:ISO-19107:2003:GM_Point',
                bounds: 'urn:ogc:def:dataType:ISO-19107:2003:GM_Envelope'

            };
        },
        getFieldType: function (name) {
            var types = this.getSlotTypes();
            var fieldType;
            _.each(_.keys(types), function (key) {
                if (types[key] === name) {
                    fieldType = key;
                }
            });
            return fieldType;
        },
        retrieveFieldDescriptors: function () {
            var descriptors = {
                General: {
                    Name: {
                        displayName: 'Node Name',
                        description: 'This node\'s name',
                        values: [],
                        type: 'string'
                    },
                    Description: {
                        displayName: 'Node Description',
                        description: 'Short description for this node',
                        values: [],
                        type: 'string'
                    },
                    VersionInfo: {
                        displayName: 'Node Version',
                        description: 'This node\'s Version',
                        values: [],
                        type: 'string'
                    },
                    liveDate: {
                        displayName: 'Live Date',
                        description: 'Date indicating when this node went live or operational',
                        values: [],
                        type: 'date',
                        isSlot: true
                    },
                    dataStartDate: {
                        displayName: 'Data Start Date',
                        description: 'Date indicating the earliest data sets available in this node',
                        values: [],
                        type: 'date',
                        isSlot: true
                    },
                    dataEndDate: {
                        displayName: 'Data End Date',
                        description: 'Date indicating when data stopped being added to this instance',
                        values: [],
                        type: 'date',
                        isSlot: true
                    },
                    lastUpdated: {
                        displayName: 'Last Updated',
                        description: 'Date this entry\'s data was last updated',
                        values: [],
                        type: 'date',
                        isSlot: true
                    },
                    links: {
                        displayName: 'Associated Links',
                        description: 'Any links that might be associated with this node like wiki pages',
                        values: [],
                        type: 'string',
                        multiValued: true,
                        isSlot: true
                    },
                    location: {
                        displayName: 'Geographic Location',
                        description: 'Geographic location of this node described by a gml:Point in decimal degrees. Format "lat lon"',
                        values: [],
                        type: 'point',
                        isSlot: true
                    },
                    region: {
                        displayName: 'Region',
                        description: 'Region of this instance described by a UNSD region. The location should be within this region',
                        values: [],
                        type: 'string',
                        isSlot: true
                    },
                    inputDataSources: {
                        displayName: 'Data Sources',
                        description: 'Sources of information that contribute to this node\'s data',
                        values: [],
                        type: 'string',
                        multiValued: true,
                        isSlot: true
                    },
                    dataTypes: {
                        displayName: 'Data Types',
                        description: 'Types of data that this node contains',
                        values: [],
                        type: 'string',
                        multiValued: true,
                        isSlot: true
                    },
                    securityLevel: {
                        displayName: 'Security Attributes',
                        description: 'Security attributes associated with this node. Format "attribute=val1,val2"',
                        values: [],
                        type: 'string',
                        multiValued: true,
                        isSlot: true
                    }
                },
                Organization: {
                    Name: {
                        displayName: 'Organization Name',
                        description: 'This organization\'s name',
                        values: [],
                        type: 'string'
                    },
                    Address: {
                        isGroup: true,
                        displayName: "Address",
                        multiValued: true,
                        constructTitle: this.constructAddressTitle
                    },
                    TelephoneNumber: {
                        isGroup: true,
                        displayName: "Phone Number",
                        multiValued: true,
                        constructTitle: this.constructPhoneTitle
                    },
                    EmailAddress: {
                        isGroup: true,
                        displayName: "Email",
                        multiValued: true,
                        constructTitle: this.constructEmailTitle
                    }
                },
                Person: {
                    Name: {
                        displayName: 'Contact Designation',
                        description: 'Contact Designation',
                        values: [],
                        type: 'string'
                    },
                    PersonName: {
                        isGroup: true,
                        multiValued: false
                    },
                    Address: {
                        isGroup: true,
                        displayName: "Address",
                        multiValued: true,
                        constructTitle: this.constructAddressTitle
                    },
                    TelephoneNumber: {
                        isGroup: true,
                        displayName: "Phone Number",
                        multiValued: true,
                        constructTitle: this.constructPhoneTitle
                    },
                    EmailAddress: {
                        isGroup: true,
                        displayName: "Email",
                        multiValued: true,
                        constructTitle: this.constructEmailTitle
                    }
                },
                Service: {
                    Name: {
                        displayName: 'Service Name',
                        description: 'This service name',
                        values: [],
                        type: 'string'
                    },
                    Description: {
                        displayName: 'Service Description',
                        description: 'Short description for this service',
                        values: [],
                        type: 'string'
                    },
                    VersionInfo: {
                        displayName: 'Service Version',
                        description: 'This service version',
                        values: [],
                        type: 'string'
                    },
                    objectType: {
                        displayName: 'Service Type',
                        description: 'Identifies the type of service this is by a urn',
                        values: 'urn:registry:federation:service',
                        type: 'string',
                        multiValued: false
                    },
                    ServiceBinding: {
                        isGroup: true,
                        displayName: "Bindings",
                        multiValued: true,
                        constructTitle: this.constructNameVersionTitle
                    }
                },
                ServiceBinding: {
                    Name: {
                        displayName: 'Binding Name',
                        description: 'This binding name',
                        values: [],
                        type: 'string'
                    },
                    Description: {
                        displayName: 'Binding Description',
                        description: 'Short description for this binding',
                        values: [],
                        type: 'string'
                    },
                    VersionInfo: {
                        displayName: 'Binding Version',
                        description: 'This binding version',
                        values: [],
                        type: 'string'
                    },
                    bindingType: {
                        displayName: 'Service Binding Type',
                        description: 'The binding type for the service.',
                        values: [],
                        type: 'string',
                        multiValued: true,
                        isSlot: true

                    },
                    serviceType: {
                        displayName: 'Service Type',
                        description: 'The service type, usually SOAP or REST',
                        values: [],
                        type: 'string',
                        isSlot: true
                    },
                    endpointDocumentation: {
                        displayName: 'Service Documentation',
                        description: 'Set of links pointing to the documentation for this service',
                        values: [],
                        type: 'string',
                        multiValued: true,
                        isSlot: true
                    }
                },
                Content: {
                    Name: {
                        displayName: 'Content Name',
                        description: 'Content collection name',
                        values: [],
                        type: 'string'
                    },
                    Description: {
                        displayName: 'Content Description',
                        description: 'Short description for this content collection',
                        values: [],
                        type: 'string'
                    },
                    objectType: {
                        displayName: 'Content Object Type',
                        description: 'The kind of content object this will be. Default value should be used in most cases.',
                        type: 'string',
                        values: 'urn:registry:content:collection',
                        multiValued: false
                    },
                    //bounds aren't processed on the backend yet
                    // bounds: {
                    //     displayName: 'Content Bounds',
                    //     description: 'Lower corner of a gml:Envelop',
                    //     values: [],
                    //     type: 'bounds',
                    //     isSlot: true
                    // },
                    mimeTypes: {
                        displayName: 'Mime Types',
                        description: 'Mime Types',
                        values: [],
                        type: 'string',
                        multiValued: true,
                        custom: false,
                        isSlot: true
                    },
                    recordCount: {
                        displayName: 'Number of records',
                        description: 'Number of records in content collection',
                        values: [],
                        type: 'number',
                        multiValued: false,
                        custom: false,
                        isSlot: true
                    },
                    startDate: {
                        displayName: 'Data Start Date',
                        description: 'Date indicating the earliest data sets available in this contentCollection',
                        values: [],
                        type: 'date',
                        multiValued: false,
                        custom: false,
                        isSlot: true
                    },
                    endDate: {
                        displayName: 'Data End Date',
                        description: 'Date indicating when data stopped being added to this content collection',
                        values: [],
                        type: 'date',
                        multiValued: false,
                        custom: false,
                        isSlot: true
                    },
                    lastUpdated: {
                        displayName: 'Last Updated',
                        description: 'Date this content collections data was last updated',
                        values: [],
                        type: 'date',
                        multiValued: false,
                        custom: false,
                        isSlot: true
                    },
                    types: {
                        displayName: 'Content Types',
                        description: 'Content types in the content collection',
                        values: [],
                        type: 'string',
                        multiValued: true,
                        custom: false,
                        isSlot: true
                    }
                },
                PersonName: {
                    firstName: {
                        displayName: 'First Name',
                        description: 'First name',
                        values: [],
                        type: 'string'
                    },
                    lastName: {
                        displayName: 'Last Name',
                        description: 'Last name',
                        values: [],
                        type: 'string'
                    }
                },
                TelephoneNumber: {
                    phoneType: {
                        displayName: 'Phone Type',
                        description: 'Phone type',
                        values: [],
                        type: 'string'
                    },
                    countryCode: {
                        displayName: 'Country Code',
                        description: 'Country code',
                        values: [],
                        type: 'number'
                    },
                    areaCode: {
                        displayName: 'Area Code',
                        description: 'Area Code',
                        values: [],
                        type: 'number'
                    },
                    number: {
                        displayName: 'Number',
                        description: 'Number',
                        values: [],
                        type: 'string'
                    },
                    extension: {
                        displayName: 'Extension',
                        description: 'Extension',
                        values: [],
                        type: 'number'
                    }
                },
                EmailAddress: {
                    type: {
                        displayName: 'Email Type',
                        description: 'Email Type',
                        values: [],
                        type: 'string'
                    },
                    address: {
                        displayName: 'Address',
                        description: 'Email Address',
                        values: [],
                        type: 'string'
                    }
                },
                Address: {
                    street: {
                        displayName: 'Street',
                        description: 'Street',
                        values: [],
                        type: 'string'
                    },
                    city: {
                        displayName: 'City',
                        description: 'City',
                        values: [],
                        type: 'string'
                    },
                    country: {
                        displayName: 'Country',
                        description: 'Country',
                        values: [],
                        type: 'string'
                    },
                    stateOrProvince: {
                        displayName: 'State or Province',
                        description: 'State or Province',
                        values: [],
                        type: 'string'
                    },
                    postalCode: {
                        displayName: 'Postal Code',
                        description: 'Postal Code',
                        values: [],
                        type: 'string'
                    }
                }
            };
            return descriptors;
        },
        constructEmailTitle: function () {
            var title = this.getField('type').get('value') + ' ' + this.getField('address').get('value');
            title = title.replace(/undefined/g, '').trim();
            if (!title) {
                title = 'Empty Email';
            }
            return title;
        },
        constructPhoneTitle: function () {
            var title = this.getField('phoneType').get('value');
            if (this.getField('areaCode').get('value')) {
                title = title + ' (' + this.getField('areaCode').get('value') + ') ';
            }
            title = title + this.getField('number').get('value');
            if (this.getField('extension').get('value')) {
                title = title + ' x' + this.getField('extension').get('value');
            }
            title = title.replace(/undefined/g, '').trim();
            if (!title || title === '()  x') {
                title = 'Empty Phone Number';
            }
            return title;
        },
        constructAddressTitle: function () {
            var title = this.getField('street').get('value') + '  ' + this.getField('city').get('value') + ' ' + this.getField('stateOrProvince').get('value');
            title = title.replace(/undefined/g, '').trim();
            if (!title) {
                title = 'Empty Address';
            }
            return title;
        },
        constructNameTitle: function () {
            var title = this.getField('Name').get('value');
            if (!title || (_.isArray(title) && title.length === 0)) {
                title = this.get('segmentType');
            }
            return title;
        },
        constructNameVersionTitle: function () {
            var name = this.getField('Name').get('value');
            var version = this.getField('VersionInfo').get('value');
            var title;
            if (!name || (_.isArray(name) && name.length === 0)) {
                title = this.get('segmentType');
            } else {
                title = name + '  Version: ' + version;
            }
            return title;
        },
        constructPersonNameTitle: function () {
            var personName;
            for (var index = 0; index < this.get('segments').models.length; index++) {
                if (this.get('segments').models[index].get('segmentType') === 'PersonName') {
                    personName = this.get('segments').models[index];
                    break;
                }
            }
            var title = 'Unknown';
            if (personName) {
                title = personName.getField('firstName').get('value') + '  ' + personName.getField('lastName').get('value');
                if (this.getField('Name').get('value').length > 0) {
                    title = title + '  [ ' + this.getField('Name').get('value') + ' ]';
                }
            }

            title = title.replace(/undefined/g, '').trim();
            if (!title) {
                title = this.get('segmentType');
            }
            return title;
        }
    };
    return FieldDescriptors;
});