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
    'wreqr',
    'js/model/Service.js',
    'backbone',
    'underscore',
    'poller',
    'js/model/Status.js',
    'backboneassociation'
],
function (wreqr, Service, Backbone, _, poller, Status) {
    var Registry = {};


    Registry.ConfigurationList = Backbone.Collection.extend({
        model: Service.Configuration
    });

    Registry.Model = Backbone.Model.extend({
        configUrl: "/jolokia/exec/org.codice.ddf.ui.admin.api.ConfigurationAdmin:service=ui",
        idAttribute: 'name',
        pullAttribute: 'pullAllowed',
        pushAttribute: 'pushAllowed',
        pidAttributer: 'pid',
        initialize: function() {
            this.set('registryConfiguration', new Registry.ConfigurationList());
        },
        addRegistryConfiguration: function(registry) {
            this.get("registryConfiguration").add(registry);

            var pid = registry.id;
            var statusModel = new Status.Model(pid);
            statusModel.on('sync', function() {
                wreqr.vent.trigger('status:update-'+pid, statusModel);
            });

            var options = {
                delay: 30000
            };

            var statusPoller = poller.get(statusModel, options);
            statusPoller.start();
        },
        removeRegistry: function(registry) {
            this.stopListening(registry);
            this.get("registryConfigurations").remove(registry);
        },
        size: function() {
            return this.get('registryConfiguration').length;
        }
    });

    Registry.Collection = Backbone.Collection.extend({
        model: Registry.Model,
        addRegistry: function(configuration) {
            var registry;
            var registryId = configuration.get("properties").get('shortname');
            if(!registryId){
                registryId = configuration.get("properties").get('id');
            }
            var allowPull = configuration.get("properties").get('pullAllowed');
            var allowPush = configuration.get("properties").get('pushAllowed');
            var id = configuration.get('id');
            if(this.get(registryId)) {
                registry = this.get(registryId);
            } else {
                registry = new Registry.Model({name: registryId, pullAllowed: allowPull, pushAllowed: allowPush, pid: id});
                this.add(registry);
            }

            registry.addRegistryConfiguration(configuration);

            registry.trigger('change');
        },
        removeRegistry: function(registry) {
            this.stopListening(registry);
            this.remove(registry);
        },
        comparator: function(model) {
            var str = model.get('name') || '';
            return str.toLowerCase();
        }
    });

    Registry.Response = Backbone.Model.extend({
        initialize: function(options) {
            if(options.model) {
                this.model = options.model;
                var collection = new Registry.Collection();
                this.set({collection: collection});
                this.listenTo(this.model, 'change', this.parseServiceModel);
            }
        },
        parseServiceModel: function() {
            var resModel = this;
            var collection = resModel.get('collection');
            collection.reset();
            if(this.model.get("value")) {
                this.model.get("value").each(function(service) {
                    if(!_.isEmpty(service.get("configurations"))) {
                        service.get("configurations").each(function(configuration) {
                            var allowPull = configuration.get('pullAllowed');
                            var allowPush = configuration.get('pushAllowed');
                            collection.addRegistry(configuration, allowPull, allowPush);
                        });
                    }
                });
            }
            collection.sort();
            collection.trigger('reset');
        },
        getRegistryMetatypes: function() {
            var resModel = this;
            var metatypes = [];
            if(resModel.model.get('value')) {
                resModel.model.get('value').each(function(service) {
                var id = service.get('id');
                var name = service.get('name');
                if (this.isRegistryName(id) || this.isRegistryName(name)) {
                    metatypes.push(service);
                }
                });
            }
            return metatypes;
        },
        isRegistryName: function(val) {
            return val && val.indexOf('Registry') !== -1;
        },
        getRegistryModel: function(initialModel) {
            var resModel = this;
            var serviceCollection = resModel.model.get('value');
            if (!initialModel) {
                initialModel = new Registry.Model();
            }

            if(serviceCollection) {
                serviceCollection.each(function(service) {
                    var config = new Service.Configuration({service: service});
                    config.set('fpid', config.get('fpid'));
                    initialModel.addRegistryConfiguration(config);
                });
            }
            return initialModel;
        }
    });


    return Registry;
});
