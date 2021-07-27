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
import java.util.Set;

import org.projectjinxers.config.Config;
import org.projectjinxers.config.SecretConfig;
import org.projectjinxers.controller.ModelController;
import org.projectjinxers.controller.ModelController.ModelControllerListener;

/**
 * @author ProjectJinxers
 *
 */
public class Group implements ModelControllerListener, Comparable<Group> {

    public interface GroupListener {

        void onGroupUpdated(Group group);

    }

    private String name;
    private String address;
    private Long timestampTolerance;
    private long[] secretObfuscationParams;
    private Map<String, Document> standaloneDocuments;
    private boolean main;

    private transient boolean save;
    private transient Config config;
    private transient SecretConfig secretConfig;
    private transient ModelController controller;

    private transient GroupListener listener;
    private transient boolean initializingController;
    private transient boolean failedInitialization;

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

    public Group(String name, String address, Long timestampTolerance, long[] secretObfuscationParams, boolean save) {
        this.name = name;
        this.address = address;
        this.timestampTolerance = timestampTolerance;
        this.secretObfuscationParams = secretObfuscationParams;
        this.save = save;
    }

    @Override
    public void initialized() {
        initializingController = false;
    }

    @Override
    public void failedInitialization() {
        initializingController = false;
        controller = null;
        this.failedInitialization = true;
        if (listener != null) {
            listener.onGroupUpdated(this);
        }
    }

    @Override
    public void onModelStateValidated() {
        if (listener != null) {
            listener.onGroupUpdated(this);
        }
    }

    @Override
    public void handleInvalidSettlement(Set<String> invalidHashes) {

    }

    @Override
    public void handleRemoved() {

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

    public long[] getSecretObfuscationParams() {
        return secretObfuscationParams;
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
            if (secretObfuscationParams != null) {
                secretConfig = secretConfig.subConfig(secretObfuscationParams);
            }
        }
        return secretConfig;
    }

    public ModelController getController() {
        return controller;
    }

    public ModelController getOrCreateController() {
        if (controller == null) {
            controller = ModelController.getModelController(getConfig(), getSecretConfig());
            if (controller.initialize(this)) {
                initializingController = true;
            }
        }
        return controller;
    }

    public boolean isInitializingController() {
        return initializingController;
    }

    public boolean isFailedInitialization() {
        return failedInitialization;
    }

    public void setListener(GroupListener listener) {
        this.listener = listener;
    }

    public void update(Group newValues) {
        this.name = newValues.name;
        Long timestampTolerance = newValues.timestampTolerance;
        if (!isEqual(timestampTolerance, this.timestampTolerance, Config.DEFAULT_TIMESTAMP_TOLERANCE)) {
            this.timestampTolerance = timestampTolerance;
            this.config = null;
        }
        long[] secretObfuscationParams = newValues.secretObfuscationParams;
        if (!isEqual(secretObfuscationParams, this.secretObfuscationParams,
                SecretConfig.getSharedInstance().getObfuscationParams())) {
            this.secretObfuscationParams = secretObfuscationParams;
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
