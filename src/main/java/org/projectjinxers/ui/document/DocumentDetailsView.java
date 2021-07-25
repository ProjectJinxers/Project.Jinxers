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
import static org.projectjinxers.ui.util.ModelLoadingUtility.loadObject;
import static org.projectjinxers.util.ObjectUtility.isNullOrBlank;

import java.net.URL;
import java.util.ResourceBundle;

import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.data.Document;
import org.projectjinxers.model.DocumentContents;
import org.projectjinxers.ui.common.PJPresenter;
import org.projectjinxers.ui.common.PJView;

import com.dansoftware.mdeditor.MarkdownEditorControl;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.SplitPane;

/**
 * @author ProjectJinxers
 *
 */
public class DocumentDetailsView
        implements PJView<DocumentDetailsPresenter.DocumentDetailsView, DocumentDetailsPresenter>,
        DocumentDetailsPresenter.DocumentDetailsView, Initializable {

    private static final double DEFAULT_DIVIDER_POSITION = 0.25;

    public static DocumentDetailsPresenter createDocumentDetailsPresenter(PJPresenter<?> parent, Parent root) {
        DocumentDetailsView documentDetailsView = new DocumentDetailsView();
        DocumentDetailsPresenter res = new DocumentDetailsPresenter(documentDetailsView, parent, root);
        documentDetailsView.documentDetailsPresenter = res;
        res.getScene();
        return res;
    }

    private DocumentDetailsPresenter documentDetailsPresenter;

    @FXML
    private SplitPane editorsSplit;
    @FXML
    private MarkdownEditorControl abstractEditor;
    @FXML
    private MarkdownEditorControl contentsEditor;

    private StringProperty statusLine = new SimpleStringProperty();

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
    }

    @Override
    public void updateView() {
        Document document = documentDetailsPresenter.getDocument();
        if (document == null) {
            statusLine.set("No document selected");
            configureEmpty();
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
            configureEmpty();
        }
        else {
            org.projectjinxers.model.Document doc = documentObject.getMapped();
            IPLDObject<DocumentContents> contentsObject = doc.getContents();
            if (contentsObject == null) {
                statusLine.set(multihash + " - No contents");
                configureEmpty();
            }
            else if (contentsObject.isMapped()) {
                statusLine.set(multihash + " - Loaded");
                DocumentContents contents = contentsObject.getMapped();
                String abstr = contents.getAbstract();
                String contentsMarkdown = contents.getContents();
                if (isNullOrBlank(abstr)) {
                    if (isNullOrBlank(contentsMarkdown)) {
                        statusLine.set(multihash + " - No contents");
                        configureEmpty();
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
                        editorsSplit.setDividerPosition(0, DEFAULT_DIVIDER_POSITION);
                        contentsEditor.setVisible(true);
                        contentsEditor.setMarkdown(contentsMarkdown);
                    }
                    abstractEditor.setVisible(true);
                    abstractEditor.setMarkdown(abstr);
                }
            }
            else {
                loadObject(contentsObject, (successCount) -> updateView());
            }
        }
    }

    private void configureEmpty() {
        abstractEditor.setMarkdown("");
        contentsEditor.setMarkdown("");
        editorsSplit.setDividerPosition(0, DEFAULT_DIVIDER_POSITION);
        abstractEditor.setVisible(true);
        contentsEditor.setVisible(true);
    }

    public StringProperty statusLineProperty() {
        return statusLine;
    }

    public String getStatusLine() {
        return statusLine.get();
    }

}
