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
    'marionette',
    'icanhaz',
    'underscore',
    'backbone',
    'wreqr',
    'text!templates/configuration/configurationEdit.handlebars',
    'text!templates/configuration/configurationItem.handlebars',
    'text!templates/configuration/textTypeListHeader.handlebars',
    'text!templates/configuration/textTypeList.handlebars',
    'text!templates/configuration/checkboxType.handlebars',
    'text!templates/accordion.hbs',
    'js/view/Accordion.view.js'

        ], function (Marionette, ich, _, Backbone, wreqr, configurationEdit, configurationItem, textTypeListHeader, textTypeList, checkboxType, accordion, AccordionView ) {

var AccordionEditView = {};

    if(!ich['configuration.configurationItem']) {
        ich.addTemplate('configuration.configurationItem', configurationItem);
    }
    if(!ich['configuration.configurationEdit']) {
        ich.addTemplate('configuration.configurationEdit', configurationEdit);
    }
    if(!ich['configuration.textTypeListHeader']) {
        ich.addTemplate('configuration.textTypeListHeader', textTypeListHeader);
    }
    if(!ich['configuration.textTypeList']) {
        ich.addTemplate('configuration.textTypeList', textTypeList);
    }
    if (!ich.accordion) {
        ich.addTemplate('accordion', accordion);
    }
    if(!ich['configuration.checkboxType']) {
        ich.addTemplate('configuration.checkboxType', checkboxType);
    }
    //WHY DOES THIS NEXT CODE BREAK THE VIEW??
//    if(!ich['configuration.checkboxType']) {
//        ich.addPartial('configuration.checkboxType', checkboxType);
//    }


    AccordionEditView.ConfigurationMultiValuedEntry = Marionette.ItemView.extend({
        template: 'configuration.textTypeList',
        tagName: 'tr',
        initialize: function() {
            this.modelBinder = new Backbone.ModelBinder();
        },
        events: {
            "click .minus-button": "minusButton"
        },
        minusButton: function() {
            this.model.collection.remove(this.model);
        },
        onRender: function() {
            var bindings = Backbone.ModelBinder.createDefaultBindings(this.el, 'name');
            this.modelBinder.bind(this.model, this.$el, bindings);
        },
        onClose: function() {
            this.modelBinder.unbind();
        }
    });


    AccordionEditView.ConfigurationMultiValueCollection = Marionette.CollectionView.extend({
        itemView: AccordionEditView.ConfigurationMultiValuedEntry,
        tagName: 'table'
    });



    AccordionEditView.ConfigurationMultiValuedItem = Marionette.Layout.extend({
        template: 'configuration.textTypeListHeader',
        itemView: AccordionEditView.ConfigurationMultiValueCollection,
        tagName: 'div',
        regions: {
            listItems: '#listItems'
        },
        /**
         * Button events, right now there's a submit button
         * I do not know where to go with the cancel button.
         */
        events: {
            "click .plus-button": "plusButton"
        },
        modelEvents: {
            "change": "updateValues"
        },
        initialize: function(options) {
            _.bindAll(this);
            this.configuration = options.configuration;
            this.collectionArray = new Backbone.Collection();
            this.listenTo(wreqr.vent, 'refresh', this.updateValues);
            //this.listenTo(wreqr.vent, 'beforesave', this.saveValues);
        },
        updateValues: function() {
            var csvVal, view = this;
            if(this.configuration.get('properties') && this.configuration.get('properties').get(this.model.get('id'))) {
                csvVal = this.configuration.get('properties').get(this.model.get('id'));
            } else {
                csvVal = this.model.get('defaultValue');
            }
            this.collectionArray.reset();
            if(csvVal && csvVal !== '') {
                if(_.isArray(csvVal)) {
                    _.each(csvVal, function (item) {
                        view.addItem(item);
                    });
                } else {
                    _.each(csvVal.split(/[,]+/), function (item) {
                        view.addItem(item);
                    });
                }
            }
        },
        saveValues: function() {
            var values = [];
            _.each(this.collectionArray.models, function(model) {
                values.push(model.get('value'));
            });
            this.configuration.get('properties').set(this.model.get('id'), values);
        },
        onRender: function() {
            this.listItems.show(new AccordionEditView.ConfigurationMultiValueCollection({
                collection: this.collectionArray
            }));

            this.updateValues();
        },
        addItem: function(value) {
            this.collectionArray.add(new Backbone.Model({value: value}));
        },
        /**
         * Creates a new text field for the properties collection.
         */
        plusButton: function() {
            this.addItem('');
        }
    });




     AccordionEditView.ConfigurationCollection = Marionette.CollectionView.extend({
        itemView: configurationEdit.ConfigurationItem,
        initialize: function(options) {
            this.service = options.service;
            this.listenTo(wreqr.vent, 'poller:start', this.render);
        },
        onRender: function() {
            this.setupPopOvers();
        },
        buildItemView: function(item, ItemViewType, itemViewOptions){
            var view;
            var configuration = this.options.configuration;
            this.collection.forEach(function(property) {
                if(item.get('id') === property.id) {
                    if(property.description) {
                        item.set({ 'description': property.description });
                    }
                    if(property.note) {
                        item.set({ 'note': property.note});
                    }
                    var options = _.extend({model: item, configuration: configuration}, itemViewOptions);
                    view = new ItemViewType(options);
                 }
            });
            if(view) {
                return view;
            }
                return new Marionette.ItemView();
            },
            /**
            * Set up the popovers based on if the selector has a description.
            */
            setupPopOvers: function() {
                var view = this;
                this.service.get('metatype').forEach(function(each) {
                    if(!_.isUndefined(each.get("description"))) {
                       var options,
                       selector = ".description[data-title='" + each.id + "']";
                       options = {
                           title: each.get("name"),
                           content: each.get("description"),
                           trigger: 'hover'
                       };
                       view.$(selector).popover(options);
                    }
                });
            },
            getItemView: function( item ) {
                if(item.get('cardinality') > 0 || item.get('cardinality') < 0) {
                    return AccordionEditView.ConfigurationMultiValuedItem;
                } else {
                    return AccordionView;
                }
            }
    });

    return AccordionEditView;
});