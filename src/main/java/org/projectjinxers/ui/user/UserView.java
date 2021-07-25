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
package org.projectjinxers.ui.user;

import static org.projectjinxers.ui.util.TextFieldUtility.checkNotBlank;
import static org.projectjinxers.ui.util.TextFieldUtility.checkNotEmpty;
import static org.projectjinxers.ui.util.TextFieldUtility.unfocus;

import java.net.URL;
import java.util.ResourceBundle;

import org.projectjinxers.data.Settings;
import org.projectjinxers.data.User;
import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.PJView;

import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;

/**
 * @author ProjectJinxers
 *
 */
public class UserView implements PJView<UserPresenter.UserView, UserPresenter>, UserPresenter.UserView, Initializable {

    public static UserPresenter createUserPresenter(User user, Settings settings, ProjectJinxers application) {
        UserView userView = new UserView();
        UserPresenter res = new UserPresenter(userView, user, application);
        userView.userPresenter = res;
        userView.settings = settings;
        return res;
    }

    private UserPresenter userPresenter;

    private Settings settings;

    @FXML
    private TextField multihashField;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField repeatPasswordField;
    @FXML
    private Spinner<Integer> securityLevelField;
    @FXML
    private CheckBox saveBox;

    @Override
    public UserPresenter getPresenter() {
        return userPresenter;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (settings.isSaveUsers()) {
            saveBox.setSelected(true);
        }
        unfocus(multihashField);
    }

    @FXML
    void confirm(Event e) {
        userPresenter.confirm(checkNotBlank(multihashField), checkNotBlank(usernameField), checkNotEmpty(passwordField),
                checkNotEmpty(repeatPasswordField), securityLevelField.getValue(), saveBox.isSelected());
    }

    @FXML
    void cancel(Event e) {
        userPresenter.canceled();
    }

}
