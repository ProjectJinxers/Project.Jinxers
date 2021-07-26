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

import static org.projectjinxers.ui.util.MarkdownUtility.fixSkippedProperties;
import static org.projectjinxers.ui.util.ModelLoadingUIUtility.loadObject;
import static org.projectjinxers.util.ObjectUtility.isNullOrBlank;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.data.Document;
import org.projectjinxers.model.DocumentContents;
import org.projectjinxers.ui.cell.DocumentCell;
import org.projectjinxers.ui.cell.ReviewCell;
import org.projectjinxers.ui.common.PJView;
import org.projectjinxers.ui.main.MainPresenter;

import com.dansoftware.mdeditor.MarkdownEditorControl;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

/**
 * @author ProjectJinxers
 *
 */
public class DocumentDetailsView
        implements PJView<DocumentDetailsPresenter.DocumentDetailsView, DocumentDetailsPresenter>,
        DocumentDetailsPresenter.DocumentDetailsView, Initializable {

    private static final double DEFAULT_DIVIDER_POSITION = 0.25;
    private static final double DEFAULT_REVIEWS_DIVIDER_POSITION = 0.33;

    public static DocumentDetailsPresenter createDocumentDetailsPresenter(MainPresenter parent, Parent root) {
        DocumentDetailsView documentDetailsView = new DocumentDetailsView();
        DocumentDetailsPresenter res = new DocumentDetailsPresenter(documentDetailsView, parent, root);
        documentDetailsView.mainPresenter = parent;
        documentDetailsView.documentDetailsPresenter = res;
        res.getScene();
        return res;
    }

    private MainPresenter mainPresenter;
    private DocumentDetailsPresenter documentDetailsPresenter;

    @FXML
    private SplitPane editorsSplit;
    @FXML
    private MarkdownEditorControl abstractEditor;
    @FXML
    private MarkdownEditorControl contentsEditor;
    @FXML
    private SplitPane reviewsSplit;
    @FXML
    private TreeView<Document> reviewsTree;
    @FXML
    private SplitPane reviewEditorsSplit;
    @FXML
    private MarkdownEditorControl reviewAbstractEditor;
    @FXML
    private MarkdownEditorControl reviewContentsEditor;
    @FXML
    private ListView<Document> historyList;

    private StringProperty statusLine = new SimpleStringProperty();
    private StringProperty reviewStatusLine = new SimpleStringProperty();

    @Override
    public DocumentDetailsPresenter getPresenter() {
        return documentDetailsPresenter;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        editorsSplit.setDividerPosition(0, DEFAULT_DIVIDER_POSITION);
        abstractEditor.managedProperty().bind(abstractEditor.visibleProperty());
        contentsEditor.managedProperty().bind(contentsEditor.visibleProperty());
        fixSkippedProperties(contentsEditor);
        reviewsSplit.setDividerPosition(0, DEFAULT_REVIEWS_DIVIDER_POSITION);
        reviewsTree.managedProperty().bind(reviewsTree.visibleProperty());
        reviewsTree.setCellFactory(param -> new ReviewCell(mainPresenter));
        reviewsTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateDetails(newVal == null ? null : newVal.getValue(), reviewStatusLine, reviewEditorsSplit,
                    reviewAbstractEditor, reviewContentsEditor);
        });
        reviewEditorsSplit.setDividerPosition(0, DEFAULT_DIVIDER_POSITION);
        reviewAbstractEditor.managedProperty().bind(reviewAbstractEditor.visibleProperty());
        reviewContentsEditor.managedProperty().bind(reviewContentsEditor.visibleProperty());
        fixSkippedProperties(reviewContentsEditor);
        historyList.setCellFactory(param -> new DocumentCell(mainPresenter, true));
    }

    @Override
    public void updateView() {
        Document document = documentDetailsPresenter.getDocument();
        updateDetails(document, statusLine, editorsSplit, abstractEditor, contentsEditor);
        updateReviews();
        updateHistory(document);
    }

    @Override
    public void updateReviews() {
        Document document = documentDetailsPresenter.getDocument();
        Map<String, Document> reviews = document == null ? null : document.getReviews();
        if (reviews == null) {
            reviewsTree.setVisible(false);
            TreeItem<Document> root = reviewsTree.getRoot();
            if (root != null) {
                root.getChildren().clear();
            }
            reviewsSplit.setDividerPosition(0, DEFAULT_REVIEWS_DIVIDER_POSITION);
            configureEmpty(reviewEditorsSplit, reviewAbstractEditor, reviewContentsEditor);
        }
        else {
            TreeItem<Document> root = reviewsTree.getRoot();
            if (root == null) {
                root = new TreeItem<>(null);
                reviewsTree.setRoot(root);
            }
            if (updateTreeItem(root, reviews)) {
                reviewsTree.refresh();
            }
            if (!reviewsTree.isVisible()) {
                reviewsTree.setVisible(true);
                reviewsSplit.setDividerPosition(0, DEFAULT_REVIEWS_DIVIDER_POSITION);
            }
        }
    }

    @Override
    public void refreshTime() {
        reviewsTree.refresh();
        historyList.refresh();
    }

    private boolean updateTreeItem(TreeItem<Document> item, Map<String, Document> reviews) {
        boolean refresh = false;
        if (reviews != null) {
            ObservableList<TreeItem<Document>> children = item.getChildren();
            outer: for (Document review : reviews.values()) {
                String documentHash = review.getMultihash();
                for (TreeItem<Document> child : children) {
                    if (documentHash.equals(child.getValue().getMultihash())) {
                        refresh = true;
                        continue outer;
                    }
                }
                TreeItem<Document> childItem = new TreeItem<>(review);
                childItem.expandedProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal) {
                        updateTreeItem(childItem, review.getReviews());
                    }
                });
                review.getOrLoadReviewInfo((successCount) -> Platform.runLater(() -> {
                    if (childItem.isExpanded()) {
                        updateTreeItem(childItem, review.getReviews());
                    }
                }));
                children.add(childItem);
            }
        }
        return refresh;
    }

    private void updateDetails(Document document, StringProperty statusLine, SplitPane splitPane,
            MarkdownEditorControl abstractEditor, MarkdownEditorControl contentsEditor) {
        if (document == null) {
            statusLine.set("No document selected");
            configureEmpty(splitPane, abstractEditor, contentsEditor);
            return;
        }
        String multihash = document.getMultihash();
        IPLDObject<org.projectjinxers.model.Document> documentObject = document.getDocumentObject();
        if (documentObject == null || !documentObject.isMapped()) {
            if (document.isLoading()) {
                statusLine.set("Loading " + multihash + "…");
            }
            else {
                statusLine.set(multihash);
            }
            configureEmpty(splitPane, abstractEditor, contentsEditor);
        }
        else {
            org.projectjinxers.model.Document doc = documentObject.getMapped();
            IPLDObject<DocumentContents> contentsObject = doc.getContents();
            if (contentsObject == null) {
                statusLine.set(multihash + " - No contents");
                configureEmpty(splitPane, abstractEditor, contentsEditor);
            }
            else if (contentsObject.isMapped()) {
                statusLine.set(multihash + " - Loaded");
                DocumentContents contents = contentsObject.getMapped();
                String abstr = contents.getAbstract();
                String contentsMarkdown = contents.getContents();
                if (isNullOrBlank(abstr)) {
                    if (isNullOrBlank(contentsMarkdown)) {
                        statusLine.set(multihash + " - No contents");
                        configureEmpty(splitPane, abstractEditor, contentsEditor);
                    }
                    else {
                        editorsSplit.setDividerPosition(0, 0);
                        abstractEditor.setVisible(false);
                        contentsEditor.setVisible(true);
                        contentsEditor.setMarkdown(contentsMarkdown);
                        abstractEditor.setMarkdown("");
                    }
                }
                else {
                    if (isNullOrBlank(contentsMarkdown)) {
                        editorsSplit.setDividerPosition(0, 1);
                        contentsEditor.setVisible(false);
                    }
                    else {
                        if (!abstractEditor.isVisible() || !contentsEditor.isVisible()) {
                            editorsSplit.setDividerPosition(0, DEFAULT_DIVIDER_POSITION);
                        }
                        contentsEditor.setVisible(true);
                        contentsEditor.setMarkdown(contentsMarkdown);
                    }
                    abstractEditor.setVisible(true);
                    abstractEditor.setMarkdown(abstr);
                }
            }
            else if (statusLine == this.statusLine) {
                loadObject(contentsObject, (successCount) -> {
                    if (document == documentDetailsPresenter.getDocument()) {
                        updateDetails(document, statusLine, splitPane, abstractEditor, contentsEditor);
                    }
                });
            }
            else {
                loadObject(contentsObject, (successCount) -> {
                    TreeItem<Document> selectedItem = reviewsTree.getSelectionModel().getSelectedItem();
                    if (selectedItem != null && document == selectedItem.getValue()) {
                        updateDetails(document, statusLine, splitPane, abstractEditor, contentsEditor);
                    }
                });
            }
        }
    }

    private void configureEmpty(SplitPane splitPane, MarkdownEditorControl abstractEditor,
            MarkdownEditorControl contentsEditor) {
        abstractEditor.setMarkdown("");
        contentsEditor.setMarkdown("");
        splitPane.setDividerPosition(0, DEFAULT_DIVIDER_POSITION);
        abstractEditor.setVisible(true);
        contentsEditor.setVisible(true);
    }

    private void updateHistory(Document document) {
        if (document != documentDetailsPresenter.getDocument()) {
            return;
        }
        if (document == null) {
            historyList.getItems().clear();
        }
        else {
            IPLDObject<org.projectjinxers.model.Document> documentObject = document.getDocumentObject();
            if (documentObject == null || !documentObject.isMapped()) {
                historyList.getItems().clear();
            }
            else {
                ObservableList<Document> items = historyList.getItems();
                List<Document> versions = new ArrayList<>();
                addPreviousVersions(document, documentObject.getMapped(), versions);
                if (versions.isEmpty()) {
                    items.clear();
                }
                else if (items.isEmpty()) {
                    items.addAll(versions);
                }
                else {
                    int targetSize = versions.size();
                    int currentSize = items.size();
                    int common = Math.min(targetSize, currentSize);
                    for (int i = 0; i < common; i++) {
                        if (!versions.get(i).getMultihash().equals(items.get(i).getMultihash())) {
                            common = i;
                            break;
                        }
                    }
                    items.remove(common, currentSize);
                    for (int i = common; i < targetSize; i++) {
                        items.add(versions.get(i));
                    }
                    if (common > 0) {
                        historyList.refresh();
                    }
                }
            }
        }
    }

    private void addPreviousVersions(Document document, org.projectjinxers.model.Document doc,
            Collection<Document> versions) {
        IPLDObject<org.projectjinxers.model.Document> previousVersion = doc.getPreviousVersionObject();
        if (previousVersion != null) {
            Document prev = new Document(document.getGroup(), previousVersion, true);
            versions.add(prev);
            if (previousVersion.isMapped()) {
                addPreviousVersions(document, previousVersion.getMapped(), versions);
            }
            else {
                prev.setProgressChangeListener(observer -> updateHistory(document));
                prev.getOrLoadDocumentObject();
            }
        }
    }

    public StringProperty statusLineProperty() {
        return statusLine;
    }

    public String getStatusLine() {
        return statusLine.get();
    }

    public StringProperty reviewStatusLineProperty() {
        return reviewStatusLine;
    }

    public String getReviewStatusLine() {
        return reviewStatusLine.get();
    }

}
