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
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.paint.Paint;

/**
 * @author ProjectJinxers
 *
 */
public abstract class PJScene<V extends View, P extends PJPresenter<V>> extends Scene {

    private static Parent loadRoot(String fxmlPath, PJPresenter<?> presenter) {
        FXMLLoader loader = new FXMLLoader(presenter.getClass().getResource(fxmlPath));
        loader.setController(presenter.getView());
        try {
            loader.load();
        }
        catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        return loader.getRoot();
    }

    private P presenter;

    protected PJScene(String fxmlPath, P presenter) {
        super(loadRoot(fxmlPath, presenter));
        this.presenter = presenter;
    }

    /**
     * @param root
     * @param width
     * @param height
     * @param depthBuffer
     * @param antiAliasing
     */
    protected PJScene(String fxmlPath, P presenter, double width, double height, boolean depthBuffer,
            SceneAntialiasing antiAliasing) {
        super(loadRoot(fxmlPath, presenter), width, height, depthBuffer, antiAliasing);
        this.presenter = presenter;
    }

    /**
     * @param root
     * @param width
     * @param height
     * @param depthBuffer
     */
    protected PJScene(String fxmlPath, P presenter, double width, double height, boolean depthBuffer) {
        super(loadRoot(fxmlPath, presenter), width, height, depthBuffer);
        this.presenter = presenter;
    }

    /**
     * @param root
     * @param width
     * @param height
     * @param fill
     */
    protected PJScene(String fxmlPath, P presenter, double width, double height, Paint fill) {
        super(loadRoot(fxmlPath, presenter), width, height, fill);
        this.presenter = presenter;
    }

    /**
     * @param root
     * @param width
     * @param height
     */
    protected PJScene(String fxmlPath, P presenter, double width, double height) {
        super(loadRoot(fxmlPath, presenter), width, height);
        this.presenter = presenter;
    }

    /**
     * @param root
     * @param fill
     */
    protected PJScene(String fxmlPath, P presenter, Paint fill) {
        super(loadRoot(fxmlPath, presenter), fill);
        this.presenter = presenter;
    }

    public P getPresenter() {
        return presenter;
    }

}
