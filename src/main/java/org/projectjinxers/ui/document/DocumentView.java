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
package org.projectjinxers.ui.document;

import static org.projectjinxers.ui.util.TextFieldUtility.checkNotBlank;
import static org.projectjinxers.ui.util.TextFieldUtility.checkNotEmpty;
import static org.projectjinxers.ui.util.TextFieldUtility.unfocus;
import static org.projectjinxers.util.ObjectUtility.isEqual;
import static org.projectjinxers.util.ObjectUtility.isNullOrBlank;
import static org.projectjinxers.util.ObjectUtility.isNullOrEmpty;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.data.Data;
import org.projectjinxers.data.Document;
import org.projectjinxers.data.Group;
import org.projectjinxers.data.User;
import org.projectjinxers.model.Review;
import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.PJView;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;

/**
 * @author ProjectJinxers
 *
 */
public class DocumentView implements PJView<DocumentPresenter.DocumentView, DocumentPresenter>,
        DocumentPresenter.DocumentView, Initializable {

    private static final String APPROVAL_YES = "Approve";
    private static final String APPROVAL_NO = "Decline";
    private static final String APPROVAL_NEUTRAL = "Neutral";

    public static DocumentPresenter createDocumentPresenter(Document document, Document reviewed, Data data,
            boolean truthInversion, Boolean approval, ProjectJinxers application) throws Exception {
        DocumentView documentView = new DocumentView();
        DocumentPresenter res = new DocumentPresenter(documentView, document, reviewed, data, truthInversion, approval,
                application);
        documentView.documentPresenter = res;
        return res;
    }

    private DocumentPresenter documentPresenter;

    @FXML
    private ChoiceBox<Group> groupsBox;
    @FXML
    private ChoiceBox<User> usersBox;
    @FXML
    private TextField importField;
    @FXML
    private TextField titleField;
    @FXML
    private TextField subtitleField;
    @FXML
    private TextField versionField;
    @FXML
    private TextField tagsField;
    @FXML
    private TextField sourceField;
    @FXML
    private TextField contentsField;
    @FXML
    private Button editButton;
    @FXML
    private Button clearButton;
    @FXML
    private Parent reviewPanel;
    @FXML
    private CheckBox truthInversionBox;
    @FXML
    private ChoiceBox<String> approvalBox;

    private StringProperty contentsIndicator;

    @Override
    public DocumentPresenter getPresenter() {
        return documentPresenter;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Document data = documentPresenter.getData();
        Document reviewed = documentPresenter.getReviewed();
        IPLDObject<org.projectjinxers.model.Document> documentObject = data == null ? null : data.getDocumentObject();
        if (documentObject == null || documentObject.getMultihash() == null || data.getGroup() == null) {
            Group group = reviewed == null ? null : reviewed.getGroup();
            if (group == null) {
                updateGroups(null);
            }
            else {
                updateGroups(group);
                groupsBox.setDisable(true);
            }
        }
        else {
            updateGroups(data.getGroup());
            groupsBox.setDisable(true);
        }
        updateUsers(data == null ? null : data.getUser());
        boolean isReview = reviewed != null
                || documentObject != null && documentObject.isMapped() && documentObject.getMapped() instanceof Review;
        if (isReview) {
            approvalBox.getItems().addAll(APPROVAL_YES, APPROVAL_NO, APPROVAL_NEUTRAL);
        }
        else {
            reviewPanel.setVisible(false);
        }
        org.projectjinxers.model.Document document = documentObject == null ? null : documentObject.getMapped();
        if (document == null) {
            Boolean approval = documentPresenter.getApproval();
            if (approval == null) {
                approvalBox.setValue(APPROVAL_NEUTRAL);
            }
            else {
                approvalBox.setValue(approval ? APPROVAL_YES : APPROVAL_NO);
            }
        }
        else {
            titleField.setText(document.getTitle());
            subtitleField.setText(document.getSubtitle());
            versionField.setText(document.getVersion());
            tagsField.setText(document.getTags());
            sourceField.setText(document.getSource());
            if (isReview) {
                Review review = (Review) document;
                if (review.isInvertTruth()) {
                    truthInversionBox.setSelected(true);
                    if (documentObject.getMultihash() != null) {
                        truthInversionBox.setDisable(true);
                    }
                    truthInversionToggled(null);
                }
                else {
                    Boolean approve = review.getApprove();
                    if (approve == null) {
                        approvalBox.setValue(APPROVAL_NEUTRAL);
                    }
                    else {
                        approvalBox.setValue(approve ? APPROVAL_YES : APPROVAL_NO);
                    }
                }
            }
        }
        if (data != null) {
            importField.setText(data.getImportURL());
        }
        updateContentsIndicator();
        unfocus(importField);
    }

    @Override
    public void updateGroups(Group toSelect) {
        List<Group> groups = documentPresenter.getGroups();
        Collections.sort(groups);
        groupsBox.setItems(FXCollections.observableList(groups));
        if (toSelect != null) {
            groupsBox.setValue(toSelect);
        }
    }

    @Override
    public void updateUsers(User toSelect) {
        List<User> users = documentPresenter.getUsers();
        if (users != null) {
            ObservableList<User> items = usersBox.getItems();
            for (User user : users) {
                if (user.getUserObject() != null && !items.contains(user)) {
                    items.add(user);
                }
            }
            if (toSelect != null && toSelect.getUserObject() != null) {
                usersBox.setValue(toSelect);
            }
        }
    }

    @Override
    public void updateContentsIndicator() {
        String abstr = documentPresenter.getAbstract();
        String contents = documentPresenter.getContents();
        boolean hasAbstract = !isNullOrBlank(abstr);
        boolean hasContents = !isNullOrBlank(contents);
        if (hasAbstract || hasContents) {
            importField.setEditable(false);
            if (hasAbstract) {
                if (hasContents) {
                    contentsIndicator.set("Abstract & Contents");
                }
                else {
                    contentsIndicator.set("Abstract only");
                }
            }
            else {
                contentsIndicator.set("Contents only");
            }
            clearButton.setDisable(false);
            if (checkNotBlank(sourceField) == null) {
                sourceField.setText(importField.getText());
            }
        }
        else {
            importField.setEditable(true);
            contentsIndicator.set(null);
            clearButton.setDisable(true);
            String importValue = importField.getText();
            if (importValue != null && isEqual(importValue, sourceField.getText())) {
                sourceField.setText(null);
            }
        }
    }

    public StringProperty contentsIndicatorProperty() {
        if (contentsIndicator == null) {
            contentsIndicator = new SimpleStringProperty();
        }
        return contentsIndicator;
    }

    public String getContentsIndicator() {
        return contentsIndicator.get();
    }

    @FXML
    void onAddGroup(Event e) {
        documentPresenter.addGroup();
    }

    @FXML
    void onAddUser(Event e) {
        documentPresenter.addUser();
    }

    @FXML
    void onEditDocument(Event e) {
        documentPresenter.showEditor(importField.isEditable() ? importField.getText() : null);
    }

    @FXML
    void onClearDocument(Event e) {
        documentPresenter.clearDocument();
    }

    @FXML
    void truthInversionToggled(Event e) {
        if (truthInversionBox.isSelected()) {
            approvalBox.setValue(documentPresenter.isReviewedFalseClaim() ? APPROVAL_YES : APPROVAL_NO);
            approvalBox.setDisable(true);
        }
        else {
            approvalBox.setDisable(false);
        }
    }

    @FXML
    void confirm(Event e) {
        boolean didNotConfirmContents = importField.isEditable();
        if (didNotConfirmContents && !reviewPanel.isVisible()) {
            documentPresenter.confirmed(checkNotBlank(importField), groupsBox.getValue());
        }
        else {
            org.projectjinxers.model.Document data;
            if (reviewPanel.isVisible()) {
                Document reviewed = documentPresenter.getReviewed();
                String approvalValue = approvalBox.getValue();
                Boolean approve;
                if (isNullOrEmpty(approvalValue) || approvalValue.equals(APPROVAL_NEUTRAL)) {
                    approve = null;
                }
                else {
                    approve = approvalValue.equals(APPROVAL_YES);
                }
                data = new Review(checkNotEmpty(titleField), checkNotEmpty(subtitleField), checkNotEmpty(versionField),
                        checkNotEmpty(tagsField), checkNotEmpty(sourceField), null,
                        reviewed == null ? null : reviewed.getDocumentObject(), truthInversionBox.isSelected(), approve,
                        null);
            }
            else {
                data = new org.projectjinxers.model.Document(checkNotEmpty(titleField), checkNotEmpty(subtitleField),
                        checkNotEmpty(versionField), checkNotEmpty(tagsField), checkNotEmpty(sourceField), null, null);
            }
            documentPresenter.confirmed(data, groupsBox.getValue(), usersBox.getValue(), checkNotBlank(importField),
                    didNotConfirmContents);
        }
    }

    @FXML
    void cancel(Event e) {
        documentPresenter.canceled();
    }

}
