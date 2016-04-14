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
/*global define*/
/*jshint -W024*/
define([
    'backbone',
    'underscore',
    'js/model/FieldDescriptors.js',
    'js/model/Segment.js',
    'js/model/Association.js',
    'jquery',
    'wreqr',
    'backboneassociation'


], function (Backbone, _, FieldDescriptors, Segment, Association, $, wreqr) {

    var Node = {};

    Node.Model = Backbone.Model.extend({
        createUrl: '/jolokia/exec/org.codice.ddf.registry:type=FederationAdminMBean/createLocalEntry',
        updateUrl: '/jolokia/exec/org.codice.ddf.registry:type=FederationAdminMBean/updateLocalEntry',

        initialize: function () {
            this.descriptors = FieldDescriptors.retrieveFieldDescriptors();
            var model = this;
            if (!model.get('id')) {
                model.set('id', 'temp-id'); //this id will be replaced on the server with a real uuid
            }

            if (!model.get('objectType')) {
                model.set('objectType', 'urn:registry:federation:node');
            }

            if (!model.get('RegistryObjectList')) {
                model.set('RegistryObjectList', {});
            }

            if (!model.get('RegistryObjectList').ExtrinsicObject) {
                model.get('RegistryObjectList').ExtrinsicObject = [];
            }

            var nodeDef = this.getObjectOfType('urn:registry:federation:node');
            if (!nodeDef || nodeDef.length === 0) {
                model.get('RegistryObjectList').ExtrinsicObject.push({
                    id: 'urn:registry:node',
                    objectType: 'urn:registry:federation:node',
                    Name: ''
                });
            }

            if (!model.get('RegistryObjectList').Service) {
                model.get('RegistryObjectList').Service = [];
            }


            if (!model.get('RegistryObjectList').Organization) {
                model.get('RegistryObjectList').Organization = [];
            }

            if (!model.get('RegistryObjectList').Person) {
                model.get('RegistryObjectList').Person = [];
            }

            if (!model.get('RegistryObjectList').Association) {
                model.get('RegistryObjectList').Association = [];
            }


            this.refreshData();

        },
        refreshData: function () {

            this.topLevelSegment = new Segment.Segment();
            this.allAssociationSegments = new Association.SegmentIds();
            this.associations = new Association.Associations();
            this.associationModel = new Association.AssociationModel({
                associations: this.associations,
                associationSegments: this.allAssociationSegments,
                topSegment: this.topLevelSegment
            });


            this.generalInfo = new Segment.Segment({
                segmentName: 'General Information',
                multiValued: false,
                segmentType: 'General',
                associationModel: this.associationModel
            });
            this.generalInfo.populateFromModel(this.getObjectOfType('urn:registry:federation:node'), this.descriptors);

            this.serviceInfo = new Segment.Segment({
                segmentName: 'Services',
                multiValued: true,
                segmentType: 'Service',
                associationModel: this.associationModel
            });
            this.serviceInfo.constructTitle = FieldDescriptors.constructNameTitle;
            this.serviceInfo.populateFromModel(this.get('RegistryObjectList').Service, this.descriptors);

            this.organizationInfo = new Segment.Segment({
                segmentName: 'Organizations',
                multiValued: true,
                segmentType: 'Organization',
                associationModel: this.associationModel
            });
            this.organizationInfo.constructTitle = FieldDescriptors.constructNameTitle;
            this.organizationInfo.populateFromModel(this.get('RegistryObjectList').Organization, this.descriptors);

            this.contactInfo = new Segment.Segment({
                segmentName: 'Contacts',
                multiValued: true,
                segmentType: 'Person',
                associationModel: this.associationModel
            });
            this.contactInfo.constructTitle = FieldDescriptors.constructPersonNameTitle;
            this.contactInfo.populateFromModel(this.get('RegistryObjectList').Person, this.descriptors);

            this.contentInfo = new Segment.Segment({
                segmentName: 'Content Collections',
                multiValued: true,
                segmentType: 'Content',
                associationModel: this.associationModel
            });
            this.contentInfo.constructTitle = FieldDescriptors.constructNameTitle;
            var extrinsics = this.get('RegistryObjectList').ExtrinsicObject;
            var contentOnly = _.without(extrinsics, _.findWhere(extrinsics, {objectType: 'urn:registry:federation:node'}));
            this.contentInfo.populateFromModel(contentOnly, this.descriptors);

            this.topLevelSegment.set('segments', new Backbone.Collection([this.generalInfo, this.serviceInfo, this.organizationInfo, this.contactInfo, this.contentInfo]));
            this.associationModel.populateFromModel(this.get('RegistryObjectList').Association);
        },
        saveData: function () {
            this.generalInfo.saveData();
            this.serviceInfo.saveData();
            this.organizationInfo.saveData();
            this.contactInfo.saveData();
            this.contentInfo.saveData();
            var model = this;
            model.get('RegistryObjectList').ExtrinsicObject = [this.generalInfo.dataModel[0]];
            _.each(this.contentInfo.dataModel, function (content) {
                model.get('RegistryObjectList').ExtrinsicObject.push(content);
            });
            this.associationModel.saveData();
        },

        save: function () {
            var deferred = $.Deferred();
            var model = this;
            var mbean = 'org.codice.ddf.registry:type=FederationAdminMBean';
            var operation = 'updateLocalEntry(java.util.Map)';
            var url = this.updateUrl;
            var curId = model.get('id');
            if (curId === 'temp-id') {
                operation = 'createLocalEntry(java.util.Map)';
                url = this.createUrl;
                model.unset("id", {silent: true});
            }

            var data = {
                type: 'EXEC',
                mbean: mbean,
                operation: operation
            };

            data.arguments = [model];
            data = JSON.stringify(data);
            return $.ajax({
                type: 'POST',
                contentType: 'application/json',
                data: data,
                url: url
            }).done(function (result) {
                console.log(result);
                if (curId === 'temp-id') {
                    wreqr.vent.trigger('nodeAdded');
                }
                deferred.resolve(result);
            }).fail(function (error) {
                console.log(error);
                deferred.fail(error);
            });

        },
        getObjectOfType: function (type) {
            var foundObjects = [];
            var prop;
            var registryList = this.get('RegistryObjectList');
            for (prop in registryList) {
                if (registryList.hasOwnProperty(prop)) {
                    var objArray = registryList[prop];
                    for (var i = 0; i < objArray.length; i++) {
                        if (objArray[i].objectType === type) {
                            foundObjects.push(objArray[i]);
                        }
                    }
                }
            }
            return foundObjects.length > 0 ? foundObjects : [];
        }
    });

    Node.Models = Backbone.Collection.extend({
        model: Node.Model,
        url: '/jolokia/read/org.codice.ddf.registry:type=FederationAdminMBean/LocalNodes',
        deleteUrl: '/jolokia/exec/org.codice.ddf.registry:type=FederationAdminMBean/deleteLocalEntry',

        parse: function (raw) {
            return raw.value.localNodes;
        },
        deleteNodes: function (nodes) {
            var deferred = $.Deferred();
            var mbean = 'org.codice.ddf.registry:type=FederationAdminMBean';
            var operation = 'deleteLocalEntry';

            var data = {
                type: 'EXEC',
                mbean: mbean,
                operation: operation
            };

            data.arguments = [nodes];
            data = JSON.stringify(data);

            return $.ajax({
                type: 'POST',
                contentType: 'application/json',
                data: data,
                url: this.deleteUrl
            }).done(function (result) {
                wreqr.vent.trigger('nodeDeleted');
                deferred.resolve(result);
            }).fail(function (error) {
                deferred.fail(error);
            });

        }

    });

    return Node;
});