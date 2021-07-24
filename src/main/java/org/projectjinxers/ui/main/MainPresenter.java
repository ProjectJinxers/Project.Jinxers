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
package org.projectjinxers.ui.main;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.projectjinxers.config.Config;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.ModelController;
import org.projectjinxers.data.Data;
import org.projectjinxers.data.Document;
import org.projectjinxers.data.Group;
import org.projectjinxers.data.Group.GroupListener;
import org.projectjinxers.data.Settings;
import org.projectjinxers.data.User;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.cell.ObjectStatusView.StatusChangeListener;
import org.projectjinxers.ui.common.DataPresenter.DataListener;
import org.projectjinxers.ui.common.PJPresenter;
import org.projectjinxers.ui.common.PJPresenter.View;
import org.projectjinxers.ui.document.DocumentPresenter;
import org.projectjinxers.ui.document.DocumentView;
import org.projectjinxers.ui.group.GroupPresenter;
import org.projectjinxers.ui.group.GroupView;
import org.projectjinxers.ui.signing.SigningPresenter;
import org.projectjinxers.ui.signing.SigningView;

import javafx.application.Platform;
import javafx.scene.Scene;

/**
 * @author ProjectJinxers
 *
 */
public class MainPresenter extends PJPresenter<MainPresenter.MainView> implements GroupListener {

    private static final long TIME_REFRESH_INTERVAL = 1000L * 60;

    public interface MainView extends View, StatusChangeListener {

        void didUpdateGroup(Group group);

        void didAddGroup(Group group);

        void didEditGroup(Group group);

        void didDeleteGroup(Group group);

        void didAddDocument(Document document);

        void didUpdateDocument(Document document);

        void didReplaceDocument(Document old, Document updated);

        void didRemoveStandaloneDocument(Document document);

        void refreshTime();

    }

    private Data data;
    private Map<String, Document> allDocuments;

    private boolean editing;

    private DataListener<Group> groupListener;

    private Timer timeRefreshTimer;
    private TimerTask timeRefreshTask;

    public MainPresenter(MainView view, ProjectJinxers application) {
        super(view, application);
        try {
            this.data = Data.load();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        Config config = Config.getSharedInstance();
        String mainAddress = config.getIOTAAddress();
        if (data == null) {
            this.data = new Data(new Settings());
        }
        else {
            Group mainGroup = data.getGroup(mainAddress);
            if (mainGroup != null && mainGroup.isMain()) {
                mainGroup.setListener(this);
                return;
            }
        }
        Group mainGroup = new Group("Main", mainAddress, config.getConfiguredTimestampTolerance(),
                data.getSettings().isSaveGroups());
        mainGroup.setListener(this);
        data.addGroup(mainGroup);
    }

    @Override
    public void onGroupUpdated(Group group) {
        getView().didUpdateGroup(group);
    }

    public Settings getSettings() {
        return data.getSettings();
    }

    public Map<String, Group> getGroups() {
        return data.getGroups();
    }

    public Collection<User> getUsers() {
        return data.getUsers();
    }

    public Map<String, Document> getAllDocuments() {
        return allDocuments;
    }

    public void saveGroup(Group group) {
        Group replaced = data.addGroup(group);
        boolean saveGroup = group.isSave();
        if (saveGroup || replaced != null && replaced.isSave() || data.getSettings().isSaveGroups()) {
            data.getSettings().setSaveGroups(saveGroup);
            saveData();
        }
    }

    public void removeGroup(String address) {
        Group removed = data.removeGroup(address);
        if (removed != null && removed.isSave()) {
            saveData();
        }
    }

    public void addUser(User user) {
        data.addUser(user);
        boolean saveUser = user.isSave();
        if (saveUser || data.getSettings().isSaveUsers()) {
            data.getSettings().setSaveUsers(saveUser);
            saveData();
        }
    }

    public void removeUser(User user) {
        if (data.removeUser(user)) {
            saveData();
        }
    }

    @Override
    protected Scene createScene() {
        Scene res = new MainScene(this);
        return res;
    }

    public String getTimestampToleranceInfo(Group group) {
        Long timestampTolerance = group.getTimestampTolerance();
        if (timestampTolerance == null) {
            return "Default timestamp tolerance (" + Config.DEFAULT_TIMESTAMP_TOLERANCE / 1000 + " seconds)";
        }
        return "Timestamp tolerance: " + timestampTolerance / 1000 + " seconds";
    }

    public String getValidModelStateInfo(Group group) {
        ModelController controller;
        try {
            controller = group.getController();
        }
        catch (Exception e) {
            return "Loading error";
        }
        IPLDObject<ModelState> currentValidatedState = controller.getCurrentValidatedState();
        if (currentValidatedState == null) {
            return "No valid state";
        }
        ModelState state = currentValidatedState.getMapped();
        long version = state.getVersion();
        long timestamp = state.getTimestamp();
        String date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(timestamp);
        String time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(timestamp);
        StringBuilder sb = new StringBuilder();
        sb.append(version).append(" - ").append(date).append(" ").append(time);
        return sb.toString();
    }

    public String getMultihash(Group group) {
        ModelController controller;
        try {
            controller = group.getController();
        }
        catch (Exception e) {
            return "Failed to create model controller";
        }
        IPLDObject<ModelState> currentValidatedState = controller.getCurrentValidatedState();
        if (currentValidatedState == null) {
            return "The controller hasn't trusted or validated any model states, yet.";
        }
        return "Multihash: " + currentValidatedState.getMultihash();
    }

    void createGroup() {
        editing = false;
        showGroupScene(null);
    }

    public void editGroup(Group group) {
        editing = true;
        showGroupScene(group);
    }

    public void deleteGroup(Group group) {
        removeGroup(group.getAddress());
        getView().didDeleteGroup(group);
    }

    private void showGroupScene(Group group) {
        GroupPresenter groupPresenter = GroupView.createGroupPresenter(group, data.getSettings(), getApplication());
        if (groupListener == null) {
            groupListener = new DataListener<Group>() {
                @Override
                public boolean didConfirmData(Group data) {
                    saveGroup(data);
                    if (editing) {
                        getView().didEditGroup(data);
                    }
                    else {
                        data.setListener(MainPresenter.this);
                        getView().didAddGroup(data);
                    }
                    return true;
                }
            };
        }
        groupPresenter.setListener(groupListener);
        presentModally(groupPresenter, group == null ? "Add group" : "Edit group", false);
    }

    void createDocument() {
        editing = false;
        showDocumentScene(null, null, false, null);
    }

    public void editDocument(Document document) {
        editing = true;
        showDocumentScene(document, null, false, null);
    }

    public void removeStandaloneDocument(Document document) {
        String multihash = document.getMultihash();
        allDocuments.remove(multihash);
        Group group = document.getGroup();
        group.removeStandaloneDocument(multihash);
        if (group.isSave()) {
            saveData();
        }
        getView().didRemoveStandaloneDocument(document);
    }

    public void deleteDocument(Document document) {
        SigningPresenter signingPresenter = SigningView.createSigningPresenter(document.getUser(), getApplication());
        signingPresenter.setListener((signer) -> document.delete(signer));
        presentModally(signingPresenter, "Sign removal", false);
    }

    public void createReview(Document reviewed, Boolean approval) {
        editing = false;
        showDocumentScene(null, reviewed, false, approval);
    }

    public void createTruthInversion(Document lie) {
        editing = false;
        showDocumentScene(null, lie, true, Boolean.FALSE);
    }

    private void showDocumentScene(final Document document, Document reviewed, boolean truthInversion,
            Boolean approval) {
        try {
            DocumentPresenter documentPresenter = DocumentView.createDocumentPresenter(document, reviewed, data,
                    truthInversion, approval, getApplication());
            documentPresenter.setListener((data) -> {
                if (editing) {
                    handleUpdatedDocument(document, data);
                }
                else {
                    handleNewDocument(data);
                }
                return true;
            });
            String title;
            if (reviewed == null) {
                title = document == null ? "Add document" : "Edit document";
            }
            else {
                title = truthInversion ? "Invert truth" : "Review document";
            }
            presentModally(documentPresenter, title, false);
        }
        catch (Exception e) {
            getView().showError("Error showing document details", e);
        }
    }

    void handleNewDocument(Document document) {
        // TODO: new reviews might not have an import URL (multhihash is null until saved, as well)
        String multihash = document.getMultihash();
        String key = multihash == null ? document.getImportURL() : multihash;
        if (allDocuments == null) {
            allDocuments = new HashMap<>();
        }
        allDocuments.put(key, document);
        getView().didAddDocument(document);
        ensureTimeRefresh();
    }

    void handleUpdatedDocument(Document old, Document updated) {
        String oldHash = old.getMultihash();
        String newHash = updated.getMultihash();
        if (newHash == null) {
            if (oldHash == null) {
                if (old == updated) {
                    getView().didUpdateDocument(updated);
                }
                else {
                    String oldKey = old.getImportURL();
                    String newKey = updated.getImportURL();
                    allDocuments.remove(oldKey);
                    allDocuments.put(newKey, updated);
                    getView().didReplaceDocument(old, updated);
                }
            }
            else {
                allDocuments.put(oldHash, updated);
                getView().didReplaceDocument(old, updated);
            }
        }
        else if (old == updated) {
            getView().didUpdateDocument(updated);
        }
        else {
            allDocuments.remove(oldHash);
            allDocuments.put(newHash, updated);
            getView().didReplaceDocument(old, updated);
        }
    }

    public void saveData() {
        try {
            data.save();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void ensureTimeRefresh() {
        if (timeRefreshTimer == null) {
            timeRefreshTimer = new Timer(true);
            timeRefreshTask = new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> getView().refreshTime());
                }
            };
            timeRefreshTimer.schedule(timeRefreshTask, TIME_REFRESH_INTERVAL, TIME_REFRESH_INTERVAL);
        }
    }

}
