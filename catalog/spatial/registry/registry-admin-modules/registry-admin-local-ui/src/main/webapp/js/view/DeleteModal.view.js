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
        'text!templates/deleteModal.handlebars',
        'text!templates/deleteNode.handlebars'
    ],
    function (ich,Marionette,Backbone,wreqr,_,$,deleteModal,deleteNode) {

        ich.addTemplate('deleteModal', deleteModal);
        ich.addTemplate('deleteNode', deleteNode);

        var DeleteModal = {};
        DeleteModal.DeleteItem = Marionette.ItemView.extend({
            template: "deleteNode",
            serializeData: function () {
                var data = {};

                if (this.model) {
                    data = this.model.toJSON();
                }
                var node = this.model.getObjectOfType('urn:registry:federation:node');
                var name = this.model.get('id');
                if(node && node.length === 1){
                    name = node[0].Name;
                }
                data.Name = name;

                return data;
            }
        });

        DeleteModal.DeleteModal  = Marionette.CompositeView.extend({
            template: 'deleteModal',
            className: 'modal',
            itemView: DeleteModal.DeleteItem,
            itemViewContainer: '.modal-body',
            events: {
                'click .submit-button' : 'deleteNodes'
            },

            deleteNodes: function() {

                var view = this;
                var toDelete = [];
                view.$(".deleteNode").each(function(index, content) {
                    if (content.checked) {
                        toDelete.push(content.id);
                    }
                });
                wreqr.vent.trigger('deleteNodes', toDelete);
                this.$el.modal("hide");
            }

        });

        return DeleteModal;

    });