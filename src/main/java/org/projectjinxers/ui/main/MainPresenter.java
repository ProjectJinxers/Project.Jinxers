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
import java.net.URL;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Map;

import org.projectjinxers.config.Config;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.ModelController;
import org.projectjinxers.data.Data;
import org.projectjinxers.data.Group;
import org.projectjinxers.data.Settings;
import org.projectjinxers.data.User;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.DataPresenter.DataListener;
import org.projectjinxers.ui.common.PJPresenter;
import org.projectjinxers.ui.common.PJPresenter.View;
import org.projectjinxers.ui.editor.EditorPresenter;
import org.projectjinxers.ui.editor.EditorView;
import org.projectjinxers.ui.group.GroupPresenter;
import org.projectjinxers.ui.group.GroupView;

import com.overzealous.remark.Remark;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * @author ProjectJinxers
 *
 */
public class MainPresenter extends PJPresenter<MainPresenter.MainView> implements DataListener<Group> {

    interface MainView extends View {

        void didAddGroup(Group group);

        void didEditGroup(Group group);

        void didDeleteGroup(Group group);

    }

    private Data data;

    private boolean editing;

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
                return;
            }
        }
        Group mainGroup = new Group("Main", mainAddress, config.getConfiguredTimestampTolerance(),
                data.getSettings().isSaveGroups());
        data.addGroup(mainGroup);
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

    @Override
    public boolean didConfirmData(Group data) {
        saveGroup(data);
        if (editing) {
            getView().didEditGroup(data);
        }
        else {
            getView().didAddGroup(data);
        }
        return true;
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
        groupPresenter.setListener(this);
        Stage stage = new Stage();
        stage.initOwner(getStage());
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setScene(groupPresenter.getScene());
        stage.show();
    }

    void createDocument(String url) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Remark remark = new Remark();
                final String markdown = remark.convert(new URL(url), 15000);
                System.out.println("Converted to Markdown: " + markdown);
                Platform.runLater(() -> {
                    EditorPresenter editorPresenter = EditorView
                            .createEditorPresenter(markdown == null ? "Duh" : markdown, getApplication());
                    Scene scene = editorPresenter.getScene();
                    getStage().setScene(scene);
                });
                return null;
            }
        };
        new Thread(task).start();
    }

    public void saveData() {
        try {
            data.save();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

}
