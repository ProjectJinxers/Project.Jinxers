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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.data.Document;
import org.projectjinxers.data.Group;
import org.projectjinxers.model.OwnershipRequest;
import org.projectjinxers.model.UnbanRequest;
import org.projectjinxers.model.Voting;
import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.cell.DocumentCell;
import org.projectjinxers.ui.cell.GroupCell;
import org.projectjinxers.ui.cell.OwnershipRequestCell;
import org.projectjinxers.ui.cell.UnbanRequestCell;
import org.projectjinxers.ui.cell.VotingCell;
import org.projectjinxers.ui.common.PJView;

import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;

/**
 * @author ProjectJinxers
 *
 */
public class MainView implements PJView<MainPresenter.MainView, MainPresenter>, MainPresenter.MainView, Initializable {

    public static MainPresenter createMainPresenter(ProjectJinxers application) {
        MainView mainView = new MainView();
        MainPresenter mainPresenter = new MainPresenter(mainView, application);
        mainView.mainPresenter = mainPresenter;
        return mainPresenter;
    }

    private MainPresenter mainPresenter;

    @FXML
    private ListView<Group> groupsList;

    @FXML
    private ListView<Document> documentsList;

    @FXML
    private ListView<IPLDObject<OwnershipRequest>> ownershipRequestsList;

    @FXML
    private ListView<IPLDObject<UnbanRequest>> unbanRequestsList;

    @FXML
    private ListView<IPLDObject<Voting>> votingsList;

    @FXML
    private VBox detailViewContainer;

    @Override
    public MainPresenter getPresenter() {
        return mainPresenter;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        groupsList.setCellFactory(param -> new GroupCell(mainPresenter));
        documentsList.setCellFactory(param -> new DocumentCell());
        ownershipRequestsList.setCellFactory(param -> new OwnershipRequestCell());
        unbanRequestsList.setCellFactory(param -> new UnbanRequestCell());
        votingsList.setCellFactory(param -> new VotingCell());
    }

    @Override
    public void updateView() {
        updateGroups();
    }

    @Override
    public void didAddGroup(Group group) {
        updateGroups();
    }

    @Override
    public void didEditGroup(Group group) {
        groupsList.refresh();
    }

    @Override
    public void didDeleteGroup(Group group) {
        updateGroups();
    }

    @Override
    public void didAddDocument(Document document) {
        updateDocuments();
    }

    @Override
    public void didUpdateDocument(Document document) {
        documentsList.refresh();
    }

    @FXML
    void onAddGroup(Event e) {
        mainPresenter.createGroup();
    }

    @FXML
    void onAddDocument(Event e) {
        mainPresenter.createDocument();
    }

    private void updateGroups() {
        Map<String, Group> groups = mainPresenter.getGroups();
        List<Group> groupsList = new ArrayList<>(groups.values());
        Collections.sort(groupsList);
        this.groupsList.setItems(FXCollections.observableList(groupsList));
    }

    private void updateDocuments() {
        Map<String, Document> allDocuments = mainPresenter.getAllDocuments();
        if (allDocuments == null) {
            documentsList.setItems(null);
        }
        else {
            List<Document> documentsList = new ArrayList<>(allDocuments.values());
            this.documentsList.setItems(FXCollections.observableList(documentsList));
        }
    }

}
