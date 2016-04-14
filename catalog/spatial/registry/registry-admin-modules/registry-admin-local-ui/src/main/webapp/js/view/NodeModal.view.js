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
define([
        'icanhaz',
        'marionette',
        'backbone',
        'wreqr',
        'underscore',
        'jquery',
        'js/model/Node.js',
        'js/model/FieldDescriptors.js',
        'text!templates/nodeModal.handlebars',
        'text!templates/associationList.handlebars',
        'text!templates/segmentRow.handlebars',
        'text!templates/field.handlebars',
        'text!templates/segmentList.handlebars',
        'text!templates/associationRow.handlebars'
    ],
    function (ich, Marionette, Backbone, wreqr, _, $, Node, FieldDescriptors, nodeModal, associationList, segmentRow, field, segmentList, associationRow) {

        ich.addTemplate('modalNode', nodeModal);
        ich.addTemplate('associationList', associationList);
        ich.addTemplate('segmentRow', segmentRow);
        ich.addTemplate('field', field);
        ich.addTemplate('segmentList', segmentList);
        ich.addTemplate('associationRow', associationRow);

        var NodeModal = {};

        NodeModal.View = Marionette.Layout.extend({
            template: 'modalNode',
            className: 'modal',
            /**
             * Button events, right now there's a submit button
             * I do not know where to go with the cancel button.
             */
            events: {
                "click .submit-button": "submitData",
                "click .cancel-button": "cancel"
            },
            regions: {
                generalInfo: '#generalInfo',
                organizationInfo: '#organizationInfo',
                contactInfo: '#contactInfo',
                serviceInfo: '#serviceInfo',
                contentInfo: '#contentInfo'
            },

            /**
             * Initialize  the binder with the ManagedServiceFactory model.
             * @param options
             */
            initialize: function (options) {
                this.mode = options.mode;
                this.descriptors = FieldDescriptors.retrieveFieldDescriptors();
                this.model.refreshData();
            },
            serializeData: function () {
                var data = {};

                if (this.model) {
                    data = this.model.toJSON();
                }
                data.mode = this.mode;

                return data;
            },
            onRender: function () {
                this.$el.attr('role', "dialog");
                this.$el.attr('aria-hidden', "true");

                this.generalInfo.show(new NodeModal.SegmentCollectionView({
                    model: this.model.generalInfo,
                    collection: this.model.generalInfo.get('segments'),
                    showHeader: true
                }));
                this.organizationInfo.show(new NodeModal.SegmentCollectionView({
                    model: this.model.organizationInfo,
                    collection: this.model.organizationInfo.get('segments'),
                    showHeader: true
                }));
                this.contactInfo.show(new NodeModal.SegmentCollectionView({
                    model: this.model.contactInfo,
                    collection: this.model.contactInfo.get('segments'),
                    showHeader: true
                }));
                this.serviceInfo.show(new NodeModal.SegmentCollectionView({
                    model: this.model.serviceInfo,
                    collection: this.model.serviceInfo.get('segments'),
                    showHeader: true
                }));
                this.contentInfo.show(new NodeModal.SegmentCollectionView({
                    model: this.model.contentInfo,
                    collection: this.model.contentInfo.get('segments'),
                    showHeader: true
                }));
            },
            /**
             * Submit to the backend.
             */
            submitData: function () {
                wreqr.vent.trigger('beforesave');
                var view = this;
                view.model.saveData();
                view.model.save();
                view.closeAndUnbind();
                wreqr.vent.trigger('nodeUpdated');
            },
            cancel: function () {
                this.closeAndUnbind();
            },
            closeAndUnbind: function () {
                this.$el.modal("hide");
            }
        });

        NodeModal.SegmentView = Marionette.Layout.extend({
            template: 'segmentRow',
            regions: {
                formFields: '.form-fields',
                formSegments: '.form-segments'
            },
            events: {
                "click .add-custom-field": 'addField',
                "click .remove-segment": 'removeSegment',
                "click .add-segment": 'addSegment'
            },
            initialize: function (options) {
                this.editableSegment = options.editableSegment;
                this.listenTo(wreqr.vent, 'removeField:' + this.model.get('segmentId'), this.removeField);

                this.addRegions({
                    associations: '#associations-'+this.model.get('simpleId'),
                    customFields: '#custom-fields-'+this.model.get('simpleId')
                });
            },
            onRender: function () {
                var knownFields = [];
                var customFields = [];
                var allFields = this.model.get('fields').models;
                _.each(allFields, function (field) {
                    if (!field.get('custom')) {
                        knownFields.push(field);
                    } else {
                        customFields.push(field);
                    }
                });
                this.formFields.show(new NodeModal.FieldCollectionView({collection: new Backbone.Collection(knownFields)}));
                this.formSegments.show(new NodeModal.SegmentCollectionView({
                    model: this.model,
                    collection: this.model.get('segments'),
                    showHeader: knownFields.length > 0 ? false : true
                }));
                if (FieldDescriptors.isCustomizableSegment(this.model.get('segmentType'))) {
                    this.customFields.show(new NodeModal.FieldCollectionView({
                        collection: new Backbone.Collection(customFields),
                        parentId: this.model.get("segmentId")
                    }));

                    if (this.model.get('fields').models.length > 0) {
                        var associationModel = this.model.get('associationModel');
                        this.associations.show(new NodeModal.AssociationCollectionView({
                            parentId: this.model.get("segmentId"),
                            model: associationModel,
                            collection: new Backbone.Collection(associationModel.getAssociationsForId(this.model.get('segmentId')))
                        }));
                    }
                }
                this.setupPopOvers();
            },
            addField: function (event) {
                event.stopImmediatePropagation();
                var fieldName = this.$('.field-name-'+this.model.get('simpleId')).val();
                var fieldType = this.$('.field-type-selector-'+this.model.get('simpleId')).val();
                var addedField = this.model.addField(fieldName, fieldType);
                wreqr.vent.trigger('addedField:' + this.model.get("segmentId"), addedField);
            },
            removeField: function (key) {
                var removedField = this.model.removeField(key);
                wreqr.vent.trigger('removedField:' + this.model.get("segmentId"), removedField);
            },
            removeSegment: function () {
                wreqr.vent.trigger('removeSegment:' + this.model.get('parentId'), this.model.get('segmentId'));
            },
            addSegment: function () {
                this.model.addSegment();
                this.render();
            },
            serializeData: function () {
                var data = {};

                if (this.model) {
                    data = this.model.toJSON();
                }
                data.editableSegment = this.editableSegment;
                if (!this.model.get('multiValued') && this.model.constructTitle) {
                    data.title = this.model.constructTitle();
                } else {
                    data.title = this.model.get('segmentName');
                }
                data.customizable = FieldDescriptors.isCustomizableSegment(this.model.get('segmentType')) && !this.model.get('multiValued');
                data.showFields = this.model.get('fields').models.length > 0;
                data.showAssociations = FieldDescriptors.isCustomizableSegment(this.model.get('segmentType')) && this.model.get('fields').models.length > 0;
                return data;
            },
            /**
             * Set up the popovers based on if the selector has a description.
             */
            setupPopOvers: function () {
                var view = this;
                this.model.get('fields').models.forEach(function (each) {
                    if (!_.isUndefined(each.get("desc"))) {
                        var options,
                            selector = ".description[data-title='" + each.get('key') + "']";
                        options = {
                            title: each.get("name"),
                            content: each.get("desc"),
                            trigger: 'hover'
                        };
                        view.$(selector).popover(options);
                    }
                });
            }
        });

        NodeModal.SegmentCollectionView = Marionette.CompositeView.extend({
            template: 'segmentList',
            itemView: NodeModal.SegmentView,
            events: {
                "click .add-segment": 'addSegment'
            },
            initialize: function () {
                this.listenTo(wreqr.vent, 'removeSegment:' + this.model.get('segmentId'), this.removeSegment);
            },
            buildItemView: function (item, ItemViewType, itemViewOptions) {
                var options = _.extend({
                    model: item,
                    editableSegment: this.model.get('multiValued')
                }, itemViewOptions);
                return new ItemViewType(options);
            },
            addSegment: function () {
                this.model.addSegment();
                this.render();
            },
            removeSegment: function (id) {
                this.model.removeSegment(id);
                this.render();
            },
            serializeData: function () {
                var data = {};

                if (this.model) {
                    data = this.model.toJSON();
                }

                data.showHeader = this.options.showHeader;
                data.segmentName = this.model.get('segmentName');
                return data;
            }
        });

        NodeModal.FieldView = Marionette.ItemView.extend({
            template: 'field',
            events: {
                "click .remove-field": 'removeField',
                "click .add-value": 'addValue',
                "click .remove-value": 'removeValue'
            },
            initialize: function () {
                this.modelBinder = new Backbone.ModelBinder();
            },
            onRender: function () {
                var bindings = Backbone.ModelBinder.createDefaultBindings(this.el, 'name');
                this.modelBinder.bind(this.model, this.$el, bindings);
            },
            addValue: function () {
                this.model.addValue('');
                this.render();
            },
            removeValue: function (event) {
                this.model.removeValue(event.target.getAttribute('name'));
                this.render();
            },
            removeField: function () {
                wreqr.vent.trigger('removeField:' + this.options.parentId, this.model.get('key'));
            }
        });

        NodeModal.FieldCollectionView = Marionette.CollectionView.extend({
            itemView: NodeModal.FieldView,
            tagName: 'tr',
            initialize: function(options){
                this.listenTo(wreqr.vent, 'addedField:' + options.parentId, this.addedField);
                this.listenTo(wreqr.vent, 'removedField:' + options.parentId, this.removedField);

            },
            buildItemView: function (item, ItemViewType, itemViewOptions) {
                var options = _.extend({
                    model: item,
                    parentId: this.options.parentId
                }, itemViewOptions);
                return new ItemViewType(options);
            },
            addedField: function(field){
                this.collection.add(field);
                this.render();
            },
            removedField: function(field){
                this.collection.remove(field);
                this.render();
            }
        });

        NodeModal.AssociationView = Marionette.ItemView.extend({
            template: 'associationRow',
            events: {
                "click .remove-association": 'removeAssociation'
            },
            removeAssociation: function(){
                wreqr.vent.trigger('removeAssociation:' + this.model.get('sourceId'), this.model.get('id'));
            }
        });
        NodeModal.AssociationCollectionView = Marionette.CompositeView.extend({
            template: 'associationList',
            itemView: NodeModal.AssociationView,
            events: {
                "click .add-association": 'addAssociation'
            },
            initialize: function(options){
                this.parentId = options.parentId;
                this.simpleId = this.parentId.split(':').join('-');
                this.listenTo(wreqr.vent, 'removeAssociation:' + this.parentId, this.removeAssociation);

            },
            serializeData: function () {
                var data = {};
                data.availableAssociation = this.model.getAvailableAssociationSegments(this.parentId);
                data.simpleId = this.simpleId;
                return data;
            },
            removeAssociation: function(associationId){
                this.collection.remove(this.model.removeAssociation(associationId));
                this.render();
            },
            addAssociation: function(){
                var selectedOption = this.$('.association-selector').find(':selected');
                var addedItem = this.model.addAssociation(this.parentId, selectedOption.attr('name'), 'AssociatedWith');
                this.collection.add(addedItem);
                this.render();
            }
        });

        
        return NodeModal;

    });