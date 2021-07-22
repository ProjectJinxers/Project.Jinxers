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

import java.io.IOException;

import org.projectjinxers.ui.common.PJPresenter.View;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

/**
 * @author ProjectJinxers
 *
 */
public class ChildScene<V extends View, P extends PJPresenter<V>> {

    private static void loadRoot(String fxmlPath, PJPresenter<?> presenter, Parent root) {
        FXMLLoader loader = new FXMLLoader(presenter.getClass().getResource(fxmlPath));
        loader.setController(presenter.getView());
        loader.setRoot(root);
        try {
            loader.load();
        }
        catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private P presenter;

    protected ChildScene(String fxmlPath, P presenter, Parent root) {
        this.presenter = presenter;
        loadRoot(fxmlPath, presenter, root);
    }

    public P getPresenter() {
        return presenter;
    }

}
