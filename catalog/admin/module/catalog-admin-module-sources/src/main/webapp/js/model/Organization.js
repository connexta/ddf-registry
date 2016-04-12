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
define(['backbone'],
function (Backbone) {

    var Organization = {};

    Organization.Model = Backbone.Model.extend({

         name : 'name',
         address : 'address',
         phoneNumber : '555-555-5555',
         emailAddress : 'unknown@user.com',

        initializeFromOrganization: function(org){
            this.name = org.name;
            this.address = org.address;
            this.phoneNumber = org.phoneNumber;
            this.emailAddress = org.emailAddress;
        },

            initialize: function(pid) {
                this.url += pid;

            }
        });

    return Organization;
});