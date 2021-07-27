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
package org.projectjinxers.ui.cell;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ResourceBundle;

import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.data.Document;
import org.projectjinxers.data.OwnershipRequest;
import org.projectjinxers.data.User;
import org.projectjinxers.model.UserState;
import org.projectjinxers.model.Votable;
import org.projectjinxers.model.Voting;
import org.projectjinxers.ui.main.MainPresenter;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.layout.Region;

/**
 * @author ProjectJinxers
 *
 */
public class OwnershipRequestCell extends DocumentCell<OwnershipRequest> {

    @FXML
    private ChoiceBox<User> activeBox;
    @FXML
    private Button showActiveUserButton;
    @FXML
    private Button deactivateRequestButton;
    @FXML
    private Region inactiveRequestsPanel;
    @FXML
    private ChoiceBox<User> inactiveBox;
    @FXML
    private Button showInactiveUserButton;
    @FXML
    private Region votingPanel;

    private BooleanProperty anonymousVotingRequested = new SimpleBooleanProperty();
    private BooleanProperty anonymousVoting = new SimpleBooleanProperty();
    private StringProperty activeUserDetails = new SimpleStringProperty();
    private StringProperty inactiveUserDetails = new SimpleStringProperty();

    public OwnershipRequestCell(MainPresenter mainPresenter) {
        super("OwnershipRequestCell.fxml", mainPresenter, true);
    }

    public BooleanProperty anonymousVotingProperty() {
        return anonymousVoting;
    }

    public boolean isAnonymousVoting() {
        return anonymousVoting.get();
    }

    public BooleanProperty anonymousVotingRequestedProperty() {
        return anonymousVotingRequested;
    }

    public boolean isAnonymousVotingRequested() {
        return anonymousVotingRequested.get();
    }

    public StringProperty activeUserDetailsProperty() {
        return activeUserDetails;
    }

    public String getActiveUserDetails() {
        return activeUserDetails.get();
    }

    public StringProperty inactiveUserDetailsProperty() {
        return inactiveUserDetails;
    }

    public String getInactiveUserDetails() {
        return inactiveUserDetails.get();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        super.initialize(location, resources);
        activeBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean disable = newVal == null;
            showActiveUserButton.setDisable(disable);
            deactivateRequestButton.setDisable(disable);
        });
        deactivateRequestButton.managedProperty().bind(deactivateRequestButton.visibleProperty());
        inactiveRequestsPanel.managedProperty().bind(inactiveRequestsPanel.visibleProperty());
        inactiveBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            showInactiveUserButton.setDisable(newVal == null);
        });
        votingPanel.managedProperty().bind(votingPanel.visibleProperty());
    }

    @Override
    protected void update(OwnershipRequest item) {
        IPLDObject<org.projectjinxers.model.OwnershipRequest>[] requestObjects = item
                .getOrLoadRequestObjects(successCount -> Platform.runLater(() -> update(item)));
        if (requestObjects == null) {
            activeBox.getItems().setAll(item.getUser());
            deactivateRequestButton.setVisible(false);
            inactiveRequestsPanel.setVisible(false);
        }
        else {
            Collection<User> activeUsers = new ArrayList<>();
            Collection<User> inactiveUsers = new ArrayList<>();
            for (IPLDObject<org.projectjinxers.model.OwnershipRequest> request : requestObjects) {
                if (request.isMapped()) {
                    org.projectjinxers.model.OwnershipRequest req = request.getMapped();
                    IPLDObject<UserState> userState = req.getUserState();
                    if (userState.isMapped()) {
                        IPLDObject<org.projectjinxers.model.User> user = userState.getMapped().getUser();
                        if (user.isMapped()) {
                            if (req.isActive()) {
                                activeUsers.add(new User(user));
                            }
                            else {
                                inactiveUsers.add(new User(user));
                            }
                        }
                    }
                }
            }
            activeBox.getItems().setAll(activeUsers);
            inactiveBox.getItems().setAll(inactiveUsers);
            deactivateRequestButton.setVisible(true);
            inactiveRequestsPanel.setVisible(true);
        }
        anonymousVotingRequested.set(item.isAnonymousVotingRequested());
        updateVotingInfo();
        update((Document) item); // GitHub Actions Java compiler fails if super method is called.
    }

    @FXML
    void showActiveUser(Event e) {

    }

    @FXML
    void deactivateRequest(Event e) {

    }

    @FXML
    void showInactiveUser(Event e) {

    }

    @FXML
    void showVoting(Event e) {

    }

    private void updateVotingInfo() {
        IPLDObject<Voting> voting = getItem()
                .getOrLoadVoting(successCoount -> Platform.runLater(() -> updateVotingInfo()));
        if (voting == null) {
            votingPanel.setVisible(false);
        }
        else {
            votingPanel.setVisible(true);
            boolean anonymous = false;
            if (voting.isMapped()) {
                IPLDObject<Votable> subject = voting.getMapped().getSubject();
                if (subject.isMapped()) {
                    anonymous = voting.getMapped().isAnonymous();
                }
            }
            anonymousVoting.set(anonymous);
        }
    }

}
