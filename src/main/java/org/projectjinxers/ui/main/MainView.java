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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.data.Document;
import org.projectjinxers.data.Group;
import org.projectjinxers.data.OwnershipRequest;
import org.projectjinxers.data.ProgressObserver;
import org.projectjinxers.model.UnbanRequest;
import org.projectjinxers.model.Voting;
import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.cell.DocumentCell;
import org.projectjinxers.ui.cell.GroupCell;
import org.projectjinxers.ui.cell.OwnershipRequestCell;
import org.projectjinxers.ui.cell.UnbanRequestCell;
import org.projectjinxers.ui.cell.VotingCell;
import org.projectjinxers.ui.common.PJView;
import org.projectjinxers.ui.document.DocumentDetailsPresenter;
import org.projectjinxers.ui.document.DocumentDetailsView;

import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.layout.BorderPane;

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
    private ListView<OwnershipRequest> ownershipRequestsList;

    @FXML
    private ListView<IPLDObject<UnbanRequest>> unbanRequestsList;

    @FXML
    private ListView<IPLDObject<Voting>> votingsList;

    @FXML
    private BorderPane detailViewContainer;

    private DocumentDetailsPresenter documentDetailsPresenter;

    @Override
    public MainPresenter getPresenter() {
        return mainPresenter;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        groupsList.setCellFactory(param -> new GroupCell(mainPresenter));
        documentsList.setCellFactory(param -> new DocumentCell<Document>(mainPresenter, false));
        ownershipRequestsList.setCellFactory(param -> new OwnershipRequestCell(mainPresenter));
        unbanRequestsList.setCellFactory(param -> new UnbanRequestCell());
        votingsList.setCellFactory(param -> new VotingCell());

        documentsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                if (documentDetailsPresenter != null) {
                    documentDetailsPresenter.setDocument(null);
                }
            }
            else {
                if (documentDetailsPresenter == null) {
                    documentDetailsPresenter = DocumentDetailsView.createDocumentDetailsPresenter(mainPresenter,
                            detailViewContainer);
                }
                documentDetailsPresenter.setDocument(newVal);
            }
        });

        ownershipRequestsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                if (documentDetailsPresenter != null) {
                    documentDetailsPresenter.setDocument(null);
                }
            }
            else {
                if (documentDetailsPresenter == null) {
                    documentDetailsPresenter = DocumentDetailsView.createDocumentDetailsPresenter(mainPresenter,
                            detailViewContainer);
                }
                documentDetailsPresenter.setDocument(newVal);
            }
        });
    }

    @Override
    public void updateView() {
        updateGroups();
    }

    @Override
    public void didUpdateGroup(Group group) {
        if (groupsList != null) {
            groupsList.refresh();
            documentsList.refresh();
            ownershipRequestsList.refresh();
            unbanRequestsList.refresh();
            votingsList.refresh();
        }
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
        updateDocuments(document, -1);
    }

    @Override
    public void didUpdateDocument(Document document) {
        documentsList.refresh();
    }

    @Override
    public void didReplaceDocument(Document old, Document updated) {
        updateDocuments(updated, -1);
    }

    @Override
    public void didRemoveStandaloneDocument(Document document) {
        updateDocuments(null, -1);
    }

    @Override
    public void selectDocument(int index) {
        documentsList.scrollTo(index);
        documentsList.getSelectionModel().select(index);
    }

    @Override
    public void updatedReviews(Document document) {
        if (documentDetailsPresenter != null && document == documentDetailsPresenter.getDocument()) {
            documentDetailsPresenter.getView().updateReviews();
        }
    }

    @Override
    public void refreshTime() {
        documentsList.refresh();
        if (documentDetailsPresenter != null) {
            documentDetailsPresenter.getView().refreshTime();
        }
    }

    @Override
    public void statusChanged(ProgressObserver progressObserver) {
        if (documentDetailsPresenter != null && progressObserver == documentDetailsPresenter.getDocument()) {
            documentDetailsPresenter.getView().updateView();
        }
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

    private void updateDocuments(Document toSelect, int indexToSelect) {
        Collection<Document> allDocuments = mainPresenter.getAllDocuments();
        if (allDocuments == null) {
            documentsList.getItems().clear();
        }
        else {
            documentsList.getItems().setAll(allDocuments);
            MultipleSelectionModel<Document> selectionModel = documentsList.getSelectionModel();
            if (toSelect != null) {
                selectionModel.select(toSelect);
                int selectedIndex = selectionModel.getSelectedIndex();
                if (selectedIndex >= 0) {
                    documentsList.scrollTo(selectedIndex);
                }
            }
            else if (indexToSelect >= 0) {
                selectionModel.select(indexToSelect);
            }
        }
    }

}
