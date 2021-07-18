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
package org.projectjinxers.ui.common;

import org.projectjinxers.ui.ProjectJinxers;

import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * @author ProjectJinxers
 *
 */
public abstract class PJPresenter<V extends PJPresenter.View> {

    public interface View {

        default void updateView() {

        }

        void showMessage(String message);

        void showError(String message, Throwable exception);

    }

    private V view;
    private ProjectJinxers application;
    private Scene scene;

    protected PJPresenter(V view, ProjectJinxers application) {
        this.view = view;
        this.application = application;
    }

    public V getView() {
        return view;
    }

    public ProjectJinxers getApplication() {
        return application;
    }

    public Scene getScene() {
        if (scene == null) {
            scene = createScene();
            view.updateView();
        }
        return scene;
    }

    protected abstract Scene createScene();

    public Stage getStage() {
        return (Stage) scene.getWindow();
    }

}
