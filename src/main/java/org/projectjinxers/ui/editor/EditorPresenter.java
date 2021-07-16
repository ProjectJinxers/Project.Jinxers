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
import org.projectjinxers.ui.common.PJPresenter;
import org.projectjinxers.ui.common.PJPresenter.View;

import javafx.scene.Scene;

/**
 * @author ProjectJinxers
 *
 */
public class EditorPresenter extends PJPresenter<EditorPresenter.EditorView> {

    public interface EditorView extends View {

    }

    private String markdown;

    protected EditorPresenter(EditorView view, String markdown, ProjectJinxers application) {
        super(view, application);
        this.markdown = markdown;
    }

    public String getMarkdown() {
        return markdown;
    }

    @Override
    protected Scene createScene() {
        Scene res = new EditorScene(this);
        return res;
    }

}
