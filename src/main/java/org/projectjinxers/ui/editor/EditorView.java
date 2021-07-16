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

import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.PJView;

import com.dansoftware.mdeditor.MarkdownEditorControl;

/**
 * @author ProjectJinxers
 *
 */
public class EditorView implements PJView<EditorPresenter.EditorView, EditorPresenter>, EditorPresenter.EditorView {

    public static EditorPresenter createEditorPresenter(String markdown, ProjectJinxers application) {
        EditorView editorView = new EditorView();
        EditorPresenter res = new EditorPresenter(editorView, markdown, application);
        editorView.editorPresenter = res;
        return res;
    }

    public MarkdownEditorControl editor;

    private EditorPresenter editorPresenter;

    public MarkdownEditorControl getEditor() {
        return editor;
    }

    public void setEditor(MarkdownEditorControl editor) {
        this.editor = editor;
    }

    @Override
    public void updateView() {
        editor.setMarkdown(editorPresenter.getMarkdown());
    }

    @Override
    public EditorPresenter getPresenter() {
        return editorPresenter;
    }

}
