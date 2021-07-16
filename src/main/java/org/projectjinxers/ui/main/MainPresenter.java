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

import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.PJPresenter;
import org.projectjinxers.ui.common.PJPresenter.View;
import org.projectjinxers.ui.editor.EditorPresenter;
import org.projectjinxers.ui.editor.EditorView;

import com.overzealous.remark.Remark;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;

/**
 * @author ProjectJinxers
 *
 */
public class MainPresenter extends PJPresenter<MainPresenter.MainView> {

    interface MainView extends View {

    }

    public MainPresenter(MainView view, ProjectJinxers application) {
        super(view, application);
    }

    @Override
    protected Scene createScene() {
        Scene res = new MainScene(this);
        // configure scene
        createDocument("https://en.wikipedia.org/wiki/Carolin_Kebekus");
        return res;
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

}
