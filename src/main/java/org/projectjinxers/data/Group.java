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

import static org.projectjinxers.util.ObjectUtility.isEqual;

import java.util.HashMap;
import java.util.Map;

import org.projectjinxers.config.Config;
import org.projectjinxers.config.SecretConfig;
import org.projectjinxers.controller.ModelController;

/**
 * @author ProjectJinxers
 *
 */
public class Group implements Comparable<Group> {

    private String name;
    private String address;
    private Long timestampTolerance;
    private Integer secretObfuscationParam;
    private Map<String, Document> standaloneDocuments;
    private boolean main;

    private transient boolean save;
    private transient Config config;
    private transient SecretConfig secretConfig;
    private transient ModelController controller;

    Group() {
        this.save = true;
    }

    public Group(String name, String address, Long timestampTolerance, boolean save) {
        this.name = name;
        this.address = address;
        this.timestampTolerance = timestampTolerance;
        this.main = true;
        this.save = save;
    }

    public Group(String name, String address, Long timestampTolerance, Integer secretObfuscationParam, boolean save) {
        this.name = name;
        this.address = address;
        this.timestampTolerance = timestampTolerance;
        this.secretObfuscationParam = secretObfuscationParam;
        this.save = save;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public Long getTimestampTolerance() {
        return timestampTolerance;
    }

    public Integer getSecretObfuscationParam() {
        return secretObfuscationParam;
    }

    public Map<String, Document> getStandaloneDocuments() {
        return standaloneDocuments;
    }

    public void addStandaloneDocument(Document document) {
        if (standaloneDocuments == null) {
            standaloneDocuments = new HashMap<>();
        }
        standaloneDocuments.put(document.getMultihash(), document);
    }

    public void removeStandaloneDocument(String multihash) {
        standaloneDocuments.remove(multihash);
    }

    public boolean isMain() {
        return main;
    }

    public boolean isSave() {
        return save;
    }

    public void setSave(boolean save) {
        this.save = save;
    }

    public Config getConfig() {
        if (config == null) {
            config = Config.getSharedInstance().subConfig(address,
                    timestampTolerance == null ? Config.DEFAULT_TIMESTAMP_TOLERANCE : timestampTolerance);
        }
        return config;
    }

    public SecretConfig getSecretConfig() {
        if (secretConfig == null) {
            secretConfig = SecretConfig.getSharedInstance();
            if (secretObfuscationParam != null) {
                secretConfig = secretConfig.subConfig(secretObfuscationParam);
            }
        }
        return secretConfig;
    }

    public ModelController getController() throws Exception {
        if (controller == null) {
            controller = ModelController.getModelController(getConfig(), getSecretConfig());
        }
        return controller;
    }

    public void update(Group newValues) {
        this.name = newValues.name;
        Long timestampTolerance = newValues.timestampTolerance;
        if (!isEqual(timestampTolerance, this.timestampTolerance, Config.DEFAULT_TIMESTAMP_TOLERANCE)) {
            this.timestampTolerance = timestampTolerance;
            this.config = null;
        }
        Integer secretObfuscationParam = newValues.secretObfuscationParam;
        if (!isEqual(secretObfuscationParam, this.secretObfuscationParam,
                SecretConfig.getSharedInstance().getObfuscationParam())) {
            this.secretObfuscationParam = secretObfuscationParam;
            this.secretConfig = null;
        }
        if (controller != null && (config == null || secretConfig == null)) {
            ModelController.removeModelController(address);
            this.controller = null;
        }
        this.save = newValues.save;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(Group o) {
        if (main != o.main) {
            return main ? -1 : 1;
        }
        return name.compareTo(o.name);
    }

}
