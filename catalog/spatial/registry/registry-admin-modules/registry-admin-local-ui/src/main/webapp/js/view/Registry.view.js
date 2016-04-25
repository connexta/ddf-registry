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
        'backbone',
        'marionette',
        'underscore',
        'jquery',
        'q',
        'wreqr',
        'js/model/Node.js',
        'js/view/NodeModal.view.js',
        'js/view/DeleteModal.view.js',
        'text!templates/registryPage.handlebars',
        'text!templates/nodeList.handlebars',
        'text!templates/nodeRow.handlebars'
    ],
    function (ich,Backbone,Marionette,_,$,Q,wreqr,Node,NodeModal,DeleteModal,registryPage, nodeList, nodeRow) {

        var RegistryView = {};

        ich.addTemplate('registryPage', registryPage);
        ich.addTemplate('nodeList', nodeList);
        ich.addTemplate('nodeRow', nodeRow);

        RegistryView.RegistryPage = Marionette.Layout.extend({
            template: 'registryPage',
            events: {
                'click .remove-node-link' : 'showRemoveNode',
                'click .add-node-link' : 'showAddNode',
                'click .refresh-button' : 'addDeleteNode'
            },
            initialize: function () {
                this.listenTo(wreqr.vent, 'editNode', this.showEditNode);
                this.listenTo(wreqr.vent, "deleteNodes", this.deleteNodes);
                this.listenTo(wreqr.vent, "nodeUpdated", this.onRender);
                this.listenTo(wreqr.vent, "nodeAdded", this.addDeleteNode);
                this.listenTo(wreqr.vent, "nodeDeleted", this.addDeleteNode);

                new RegistryView.ModalController({
                    application: this
                });
                this.listenTo(this.model, 'add', this.render);
                this.listenTo(this.model, 'remove', this.render);
            },
            regions: {
                identityRegion: '#localIdentityNodeRegion',
                additionalRegion: '#localAdditionalNodeRegion',
                modalRegion: '#registry-modal'
            },
            onRender: function() {
                var view = this;
                view.secondaryNodes = [];
                var identityNode;
                _.each(view.model.models, function(model){
                    if(model.get('TransientValues') && model.get('TransientValues')['registry-identity-node']){
                        identityNode = model;
                    } else {
                        view.secondaryNodes.push(model);
                    }
                });
                this.identityRegion.show(new RegistryView.NodeTable({collection: new Backbone.Collection(identityNode)}));
                this.additionalRegion.show(new RegistryView.NodeTable({collection: new Backbone.Collection(this.secondaryNodes), multiValued: true}));
            },
            showEditNode: function(node) {
                wreqr.vent.trigger("showModal",
                    new NodeModal.View({
                        model: node,
                        mode: 'edit'
                    })
                );
            },
            showRemoveNode: function() {
                if(this.model) {
                    wreqr.vent.trigger("showModal",
                        new DeleteModal.DeleteModal({
                            collection: new Backbone.Collection(this.secondaryNodes)
                        })
                    );
                }
            },
            showAddNode: function() {
                if(this.model) {
                    wreqr.vent.trigger("showModal",
                        new NodeModal.View({
                            model: new Node.Model(),
                            mode: 'add'
                        })
                    );
                }
            },
            addDeleteNode: function () {
                var view = this;
                var button = view.$('.refresh-button');
                if(!button.hasClass('fa-spin')) {
                    button.addClass('fa-spin');
                }
                this.model.fetch({
                    reset: true,
                    success: function () {
                        view.fetchComplete(view);
                    }
                });
            },
            fetchComplete: function(view){
                var button = view.$('.refresh-button');
                button.removeClass('fa-spin');
                view.render();
            },
            deleteNodes: function(nodeList) {
                this.model.deleteNodes(nodeList);
            }
        });
        RegistryView.ModalController = Marionette.Controller.extend({
            initialize: function (options) {
                this.application = options.application;
                this.listenTo(wreqr.vent, "showModal", this.showModal);
            },
            showModal: function(modalView) {

                var region = this.application.getRegion('modalRegion');
                var iFrameModalDOM = $('#IframeModalDOM');
                modalView.$el.on('hidden.bs.modal', function () {
                    iFrameModalDOM.hide();
                });
                modalView.$el.on('shown.bs.modal', function () {
                    var extraHeight = modalView.el.firstChild.clientHeight - $('#localNode').height();
                    if(extraHeight > 0) {
                        iFrameModalDOM.height(extraHeight);
                        iFrameModalDOM.show();
                    }
                });
                region.show(modalView);
                region.currentView.$el.modal();
            }
        });


        RegistryView.NodeRow= Marionette.ItemView.extend({
            template: "nodeRow",
            tagName: "tr",
            className: "highlight-on-hover",
            events: {
                'click td' : 'editNode'
            },
            editNode: function(evt) {
                evt.stopPropagation();
                var node = this.model;
                //node.refreshData();
                wreqr.vent.trigger('editNode', node);
            },
            serializeData: function(){
                var data = {};

                if(this.model) {
                    data = this.model.toJSON();
                }

                var extrinsicData = this.model.getObjectOfType('urn:registry:federation:node');
                if(extrinsicData.length === 1) {
                    data.name = extrinsicData[0].Name;
                    data.slots = extrinsicData[0].Slot;
                }
                return data;
            }
        });

        RegistryView.NodeTable = Marionette.CompositeView.extend({
            template: 'nodeList',
            itemView: RegistryView.NodeRow,
            itemViewContainer: 'tbody',
            serializeData: function(){
                var data = {};

                if(this.model) {
                    data = this.model.toJSON();
                }
                data.multiValued = this.options.multiValued;

                return data;
            }
        });


        return RegistryView;

    });