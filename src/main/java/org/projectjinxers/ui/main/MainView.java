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

import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.PJView;

import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;

/**
 * @author ProjectJinxers
 *
 */
public class MainView implements PJView<MainPresenter.MainView, MainPresenter>, MainPresenter.MainView {

    public static MainPresenter createMainPresenter(ProjectJinxers application) {
        MainView mainView = new MainView();
        MainPresenter mainPresenter = new MainPresenter(mainView, application);
        mainView.mainPresenter = mainPresenter;
        return mainPresenter;
    }

    private MainPresenter mainPresenter;

    @FXML
    private VBox detailViewContainer;

    @Override
    public MainPresenter getPresenter() {
        return mainPresenter;
    }

    @FXML
    void onAddGroup(Event e) {
        System.out.println("Not yet implemented");
    }

    @FXML
    void onAddDocument(Event e) {
        mainPresenter.createDocument("https://en.wikipedia.org/wiki/Carolin_Kebekus");
    }

}
