/*
 * Copyright (C) 2021 ProjectJinxers
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package org.projectjinxers.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

/**
 * @author ProjectJinxers
 *
 */
public class Data {

    private static final String FILENAME = "data.json";
    private static final Gson GSON = new GsonBuilder().create();

    public static Data load() throws JsonSyntaxException, JsonIOException, IOException {
        File f = new File(FILENAME);
        if (f.exists()) {
            BufferedReader br = new BufferedReader(new FileReader(f));
            try {
                Data res = GSON.fromJson(br, Data.class);
                res.allGroups = res.groups;
                res.allUsers = res.users;
                res.groups = null;
                res.users = null;
                return res;
            }
            finally {
                if (br != null) {
                    br.close();
                }
            }
        }
        return null;
    }

    private Settings settings;
    private Map<String, Group> groups;
    private Collection<User> users;

    private transient Map<String, Group> allGroups;
    private transient Collection<User> allUsers;

    Data() {

    }

    public Data(Settings settings) {
        this.settings = settings;
    }

    public Settings getSettings() {
        return settings;
    }

    public Map<String, Group> getGroups() {
        return allGroups;
    }

    public Group getGroup(String address) {
        return allGroups == null ? null : allGroups.get(address);
    }

    public Group addGroup(Group group) {
        if (allGroups == null) {
            allGroups = new HashMap<>();
        }
        return allGroups.put(group.getAddress(), group);
    }

    public Group removeGroup(String address) {
        return allGroups.remove(address);
    }

    public Collection<User> getUsers() {
        return allUsers;
    }

    public void addUser(User user) {
        if (allUsers == null) {
            allUsers = new ArrayList<>();
        }
        allUsers.add(user);
    }

    public boolean removeUser(User user) {
        return allUsers.remove(user);
    }

    public void save() throws IOException {
        if (allGroups != null) {
            Map<String, Group> groups = new HashMap<>();
            for (Entry<String, Group> entry : allGroups.entrySet()) {
                Group value = entry.getValue();
                if (value.isSave()) {
                    groups.put(entry.getKey(), value);
                }
            }
            if (groups.size() > 0) {
                this.groups = groups;
            }
            else {
                this.groups = null;
            }
        }
        if (allUsers != null) {
            Collection<User> users = new ArrayList<>();
            for (User user : allUsers) {
                if (user.isSave()) {
                    users.add(user);
                }
            }
            if (users.size() > 0) {
                this.users = users;
            }
            else {
                this.users = null;
            }
        }
        File f = new File(FILENAME);
        FileWriter writer = new FileWriter(f);
        try {
            GSON.toJson(this, writer);
            writer.flush();
        }
        finally {
            writer.close();
        }
    }

}
