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
package org.projectjinxers.ui.editor;

import static org.projectjinxers.ui.util.MarkdownUtility.fixSkippedProperties;

import java.net.URL;
import java.util.ResourceBundle;

import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.PJView;

import com.dansoftware.mdeditor.MarkdownEditorControl;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;

/**
 * @author ProjectJinxers
 *
 */
public class EditorView
        implements PJView<EditorPresenter.EditorView, EditorPresenter>, EditorPresenter.EditorView, Initializable {

    public static EditorPresenter createEditorPresenter(String abstr, String contents, Scene previousScene,
            ProjectJinxers application) {
        EditorView editorView = new EditorView();
        EditorPresenter res = new EditorPresenter(editorView, abstr, contents, previousScene, application);
        editorView.editorPresenter = res;
        return res;
    }

    @FXML
    private SplitPane editorsSplit;
    @FXML
    private MarkdownEditorControl abstractEditor;
    @FXML
    private MarkdownEditorControl contentsEditor;

    private EditorPresenter editorPresenter;

    @Override
    public EditorPresenter getPresenter() {
        return editorPresenter;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        editorsSplit.setDividerPosition(0, 0.25);
        abstractEditor.setMarkdown(editorPresenter.getAbstract());
        fixSkippedProperties(contentsEditor);
    }

    @Override
    public void updateView() {
        contentsEditor.setMarkdown(editorPresenter.getContents());
    }

    @FXML
    void confirm() {
        editorPresenter.confirm(abstractEditor.getMarkdown(), contentsEditor.getMarkdown());
    }

    @FXML
    void cancel() {
        editorPresenter.cancel();
    }

}
