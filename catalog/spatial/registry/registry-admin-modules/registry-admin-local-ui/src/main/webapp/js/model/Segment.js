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
    'jquery',
    'backboneassociation'


], function (Backbone, _, FieldDescriptors) {

    var counter = 0;

    function addValueFields(field, values) {
        field.set('value', values);
        if (_.isArray(values)) {
            for (var i = 0; i < values.length; i++) {
                field.set('value' + i, values[i]);
            }
        } else if (values && field.get('type') === 'date') {
            var normalizedDateTime = new Date(values).toISOString();
            var dateTimeParts = normalizedDateTime.split('T');
            field.set('valueDate', dateTimeParts[0]);
            if (dateTimeParts.length === 2) {
                field.set('valueTime', dateTimeParts[1].substring(0,dateTimeParts[1].length-1));
            }
        } else if (values && field.get('type') === 'point') {
            field.set('valueLat', values.coords[0]);
            field.set('valueLon', values.coords[1]);
        }
        else if (values && field.get('type') === 'bounds') {
            field.set('valueUpperLat', values.coords[0]);
            field.set('valueUpperLon', values.coords[1]);
            field.set('valueLowerLat', values.coords[2]);
            field.set('valueLowerLon', values.coords[3]);
        }
    }

    function addSlotFields(array, dataModel, descriptors) {

        var addedSlots = [];
        _.each(_.keys(descriptors), function (name) {
            var entry = descriptors[name];
            if (entry.isSlot) {
                var field = new Segment.FormField({
                    key: name,
                    name: entry.displayName,
                    desc: entry.description,
                    type: entry.type,
                    isSlot: true,
                    multiValued: entry.multiValued ? entry.multiValued : false
                });
                _.each(dataModel.Slot, function (slot) {
                    if (slot.name === name) {
                        var values = getSlotValue(slot, entry.type, entry.multiValued);
                        addValueFields(field, values);
                        addedSlots.push(name);
                    }
                });
                array.push(field);
            }
        });

        //add custom slots
        _.each(dataModel.Slot, function (slot) {
            if (!_.contains(addedSlots, slot.name)) {
                var type = FieldDescriptors.getFieldType(slot.slotType);
                if (!type) {
                    type = slot.slotType;
                }
                var field = new Segment.FormField({
                    key: slot.name,
                    name: slot.name,
                    type: type,
                    custom: true,
                    isSlot: true,
                    multiValued: type === 'string'
                });
                addValueFields(field, getSlotValue(slot, type, field.get('multiValued')));
                array.push(field);
            }
        });
    }

    function getSlotValue(slot, type, multiValued) {
        if (_.isArray(slot.value)) { //ebrim value list
            if (multiValued) {
                return slot.value;
            }
            if(type === 'boolean') {
                return slot.value[0] === 'true'?true:false;
            }
            return slot.value[0];
        } else { //csw AnyValue
            if (type === 'point') {
                return {coords: slot.value.Point.pos.split(/[ ]+/)};
            }
            else if (type === 'bounds') {
                var lowerCorner = slot.value.Envelope.lowerCorner.split(/[ ]+/);
                var upperCorner = slot.value.Envelope.upperCorner.split(/[ ]+/);
                return {coords: upperCorner.concat(lowerCorner)};
            } else {
                return slot.value;
            }
        }
    }

    function generateId() {
        counter++;
        return 'urn:segment:id:' + new Date().getTime() + "-" + counter;

    }

    function getSlot(slots, key) {
        for (var index = 0; index < slots.length; index++) {
            if (slots[index].name === key) {
                return slots[index];
            }
        }
    }

    function getSegment(data, key) {
        var dataModel = data;
        if (!_.isArray(data)) {
            dataModel = [data];
        }
        for (var index = 0; index < dataModel.length; index++) {
            var foundModel;

            if (dataModel[index].id === key) {
                return dataModel[index];
            }

            for (var prop in dataModel[index]) {
                if (dataModel[index].hasOwnProperty(prop) && typeof dataModel[index][prop] === 'object') {
                    foundModel = getSegment(dataModel[index][prop], key);
                    if (foundModel) {
                        return foundModel;
                    }
                }
            }
        }
    }

    var Segment = {};

    Segment.FormField = Backbone.AssociatedModel.extend({
        defaults: {
            key: '',
            name: 'unknown',
            desc: 'none',
            type: 'string',  //string,boolean,decimal,integer,data,time,point,bounds,custom
            value: undefined,
            custom: false,
            isSlot: false,
            multiValued: false

        },
        addValue: function (val) {
            if (!this.get('value')) {
                this.set('value', [val]);
            } else if (_.isArray(this.get('value'))) {
                this.get('value').push(val);
            } else {
                this.set('value', [this.get('value'), val]);
            }
            this.set('value' + (this.get('value').length - 1), val);
        },
        removeValue: function (index) {
            var values = this.get('value');
            values.splice(index, 1);
            for (index = 0; index < values.length; index++) {
                this.set('value' + index, values[index]);
            }
            this.unset('value' + values.length);
        }
    });

    Segment.FormFields = Backbone.Collection.extend({
        model: Segment.FormField
    });

    Segment.Segment = Backbone.AssociatedModel.extend({
        relations: [
            {
                type: Backbone.Many,
                key: 'segments',
                collectionType: function () {
                    return Segment.Segments;
                },
                relatedModel: function () {
                    return Segment.Segment;
                }
            },
            {
                type: Backbone.Many,
                key: 'fields',
                collectionType: function () {
                    return Segment.FormFields;
                },
                relatedModel: function () {
                    return Segment.FormField;
                }
            }
        ],
        defaults: {
            parentId: undefined,
            simpleId: undefined,
            segmentId: undefined,
            segmentName: undefined,
            segmentType: undefined,
            containerOnly: false,
            multiValued: false,
            nestedLevel: 0,
            fields: [],
            segments: [],
            associationModel: undefined
        },
        populateFromModel: function (dataModel, descriptors) {
            var model = this;
            var segs = [];
            var seg;
            var segType = model.get('segmentType');
            model.dataModel = dataModel;
            model.descriptors = descriptors;
            if (_.isArray(dataModel)) {
                model.set('segmentId', generateId());
                model.set('simpleId', model.get('segmentId').split(':').join('-'));

                for (var index = 0; index < dataModel.length; index++) {
                    seg = new Segment.Segment({
                        segmentName: segType,
                        multiValued: false,
                        parentId: model.get('segmentId'),
                        segmentType: segType,
                        nestedLevel: model.get('nestedLevel') + 1,
                        associationModel: model.get('associationModel')
                    });
                    seg.constructTitle = model.constructTitle;
                    seg.populateFromModel(dataModel[index] ? dataModel[index] : {}, descriptors);
                    segs.push(seg);
                }
                model.set('segments', segs);
            } else if (dataModel) {
                if (dataModel.id) {
                    model.set('segmentId', dataModel.id);
                } else {
                    model.set('segmentId', generateId()); //this id will be replaced on the server with a real uuid
                    dataModel.id = model.get('segmentId');
                }
                model.set('simpleId', model.get('segmentId').split(':').join('-'));

                var fieldList = [];
                var prop;

                for (prop in descriptors[segType]) {
                    if (descriptors[segType].hasOwnProperty(prop)) {
                        var obj = descriptors[segType][prop];
                        if (obj.isGroup) {
                            seg = new Segment.Segment({
                                segmentName: obj.displayName,
                                multiValued: obj.multiValued,
                                parentId: model.get('segmentId'),
                                segmentType: prop,
                                containerOnly: FieldDescriptors.isContainerOnly(prop),
                                nestedLevel: model.get('nestedLevel') + 1,
                                associationModel: model.get('associationModel')
                            });
                            seg.constructTitle = obj.constructTitle;
                            var passedModel = dataModel[prop];
                            if (!passedModel) {
                                passedModel = seg.get('containerOnly') || obj.multiValued ? [] : {};
                                dataModel[prop] = passedModel;
                            }
                            seg.populateFromModel(passedModel, descriptors);
                            segs.push(seg);
                        } else if (!obj.isSlot) {
                            fieldList.push(new Segment.FormField({
                                key: prop,
                                name: obj.displayName,
                                desc: obj.description,
                                type: obj.type,
                                isSlot: false,
                                multiValued: obj.multiValued,
                                value: dataModel[prop] ? dataModel[prop] : obj.values
                            }));
                        }
                    }
                }
                addSlotFields(fieldList, dataModel, descriptors[segType]);

                model.set('segments', segs);
                model.set('fields', fieldList);
            } else {
                model.set('segmentId', generateId());
                model.set('simpleId', model.get('segmentId').split(':').join('-'));
            }
        },
        addField: function (key, type) {
            var newField = new Segment.FormField({
                key: key,
                name: key,
                type: type,
                custom: true,
                isSlot: true,
                multiValued: type === 'string',
                value: []
            });
            this.get('fields').add(newField);
            return newField;
        },
        removeField: function (key) {
            var removedField = _.find(this.get('fields').models, function (field) {
                return field.get('key') === key;
            });
            this.get('fields').remove(removedField);
            return removedField;
        },
        addSegment: function () {
            var seg = new Segment.Segment({
                segmentName: this.get('segmentName'),
                multiValued: false,
                segmentType: this.get('segmentType'),
                parentId: this.get('segmentId'),
                nestedLevel: this.get('nestedLevel') + 1,
                associationModel: this.get('associationModel')
            });
            seg.constructTitle = this.constructTitle;
            seg.populateFromModel({}, this.descriptors);
            this.get('segments').add(seg);
            var segTitle = seg.constructTitle ? seg.constructTitle() : seg.getField('Name').get('value');
            this.get('associationModel').addAssociationSegment(seg.get('segmentId'),seg.get('segmentType'),segTitle);
            
            return seg;
        },
        removeSegment: function (id) {
            var seg = _.find(this.get('segments').models, function (seg) {
                return seg.get('segmentId') === id;
            });
            this.get('segments').remove(seg);
            return seg;
        },
        getField: function (key) {
            var fields = this.get("fields").models;
            var foundField;
            _.each(fields, function (field) {
                if (field.get('key') === key) {
                    foundField = field;
                }
            });
            return foundField;
        },
        saveData: function () {
            var model = this;
            var fields = this.get("fields").models;
            var segments = this.get('segments').models;
            var ebrimTypes = FieldDescriptors.getSlotTypes();
            //update and add values
            _.each(fields, function (field) {
                if (!field.get('isSlot')) {
                    if( field.get('value') && !(_.isArray( field.get('value')) &&  field.get('value').length === 0)) {
                        model.dataModel[field.get('key')] = field.get('value');
                    }
                } else {
                    if (!model.dataModel.Slot) {
                        model.dataModel.Slot = [];
                    }
                    var slot = getSlot(model.dataModel.Slot, field.get('key'));
                    var newSlot = false;
                    if (!slot) {
                        slot = {
                            slotType: ebrimTypes[field.get('type')],
                            name: field.get('key')
                        };
                        newSlot = true;
                    }

                    if (field.get('type') === 'date') {
                        if(field.get('valueDate')) {
                            var time = '00:00';
                            if(field.get('valueTime')){
                                time = field.get('valueTime');
                            }
                            slot.value = [field.get('valueDate') + 'T' + time + 'Z'];
                        }
                    } else if (field.get('type') === 'point') {
                        if(field.get('valueLat') && field.get('valueLon')) {
                            slot.value = {
                                Point: {
                                    srsName: 'urn:ogc:def:crs:EPSG::4326',
                                    srsDimension: 2,
                                    pos: field.get('valueLat') + ' ' + field.get('valueLon')
                                }
                            };
                        }
                    } else if (field.get('type') === 'bounds') {
                        if(field.get('valueLowerLat') && field.get('valueLowerLon') && field.get('valueUpperLon') && field.get('valueUpperLat')) {
                            slot.value = {
                                Envelope: {
                                    srsName: '',
                                    lowerCorner: field.get('valueLowerLat') + ' ' + field.get('valueLowerLon'),
                                    upperCorner: field.get('valueUpperLat') + ' ' + field.get('valueUpperLon')
                                }
                            };
                        }
                    } else if (field.get('type') === 'boolean'){
                      slot.value = field.get('value')? 'true': 'false';
                    } else if (field.get('multiValued')) {
                        var values = [];
                        for (var index = 0; field.get('value' + index); index++) {
                            values.push(field.get('value' + index));
                        }
                        slot.value = values;
                    } else {
                        if(field.get('value')){
                            slot.value = [field.get('value')];
                        }
                    }

                    if(newSlot && slot.value){
                        model.dataModel.Slot.push(slot);
                    }
                }

            });

            if(!model.dataModel) {
                console.log('whattheheck');
            }
            //remove slots
            if (model.dataModel.Slot) {
                var slots = [];
                for (var index = 0; index < model.dataModel.Slot.length; index++) {
                    if (model.getField(model.dataModel.Slot[index].name)) {
                        slots.push(model.dataModel.Slot[index]);
                    }
                }
                model.dataModel.Slot = slots;
            }

            //now handle the segments
            _.each(segments, function (segment) {
                if (!segment.get('containerOnly') && !segment.get('multiValued')) {
                    var segObj = getSegment(model.dataModel, segment.get('segmentId'));
                    if (!segObj) {
                        if (!_.isArray(model.dataModel)) {
                            model.dataModel[segment.get('segmentType')] = segment.dataModel;
                        }
                    }
                }
                segment.saveData();
            });
            if(_.isArray(model.dataModel)){
                model.dataModel.length = 0;
                _.each(segments, function(segment){
                    model.dataModel.push(segment.dataModel);
                });
            }
        }
    });

    Segment.Segments = Backbone.Collection.extend({
        model: Segment.Segment
    });

    return Segment;
});