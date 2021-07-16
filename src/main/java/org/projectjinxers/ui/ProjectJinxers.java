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

import org.projectjinxers.ui.main.MainPresenter;
import org.projectjinxers.ui.main.MainView;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * @author ProjectJinxers
 *
 */
public class ProjectJinxers extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("ProjectJinxers");
        MainPresenter mainPresenter = MainView.createMainPresenter(this);
        primaryStage.setScene(mainPresenter.getScene());
        primaryStage.show();
    }

}
