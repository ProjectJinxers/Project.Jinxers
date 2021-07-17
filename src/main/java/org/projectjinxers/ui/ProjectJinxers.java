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
package org.projectjinxers.ui;

import java.awt.Taskbar;
import java.awt.Toolkit;
import java.net.URL;

import org.projectjinxers.ui.main.MainPresenter;
import org.projectjinxers.ui.main.MainView;

import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * @author ProjectJinxers
 *
 */
public class ProjectJinxers extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("ProjectJinxers");
        Image appIcon = new Image(getClass().getClassLoader().getResourceAsStream("images/appicon.jpeg"));
        primaryStage.getIcons().add(appIcon);
        setTaskbarImage();
        MainPresenter mainPresenter = MainView.createMainPresenter(this);
        primaryStage.setScene(mainPresenter.getScene());
        primaryStage.show();
    }

    private void setTaskbarImage() {
        final Toolkit defaultToolkit = Toolkit.getDefaultToolkit();
        final URL imageResource = getClass().getClassLoader().getResource("images/appicon.jpeg");
        final java.awt.Image image = defaultToolkit.getImage(imageResource);

        // this is new since JDK 9
        final Taskbar taskbar = Taskbar.getTaskbar();

        try {
            // set icon for mac os (and other systems which do support this method)
            taskbar.setIconImage(image);
        }
        catch (final UnsupportedOperationException e) {
            System.out.println("The os does not support: 'taskbar.setIconImage'");
        }
        catch (final SecurityException e) {
            System.out.println("There was a security exception for: 'taskbar.setIconImage'");
        }
    }

}
