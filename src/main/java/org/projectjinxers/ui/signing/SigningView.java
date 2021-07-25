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
package org.projectjinxers.ui.signing;

import java.net.URL;
import java.util.ResourceBundle;

import org.projectjinxers.data.User;
import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.PJView;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;

/**
 * @author ProjectJinxers
 *
 */
public class SigningView
        implements PJView<SigningPresenter.SigningView, SigningPresenter>, SigningPresenter.SigningView, Initializable {

    public static SigningPresenter createSigningPresenter(User user, ProjectJinxers application) {
        SigningView signingView = new SigningView();
        SigningPresenter res = new SigningPresenter(signingView, user, application);
        signingView.signingPresenter = res;
        signingView.username = new SimpleStringProperty(user.getName());
        return res;
    }

    private SigningPresenter signingPresenter;

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Spinner<Integer> securityLevelField;

    private StringProperty username;

    public StringProperty usernameProperty() {
        return username;
    }

    public String getUsername() {
        return username.get();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        securityLevelField.getValueFactory().setValue(signingPresenter.getSecurityLevel());
    }

    @Override
    public SigningPresenter getPresenter() {
        return signingPresenter;
    }

    @FXML
    void confirm(Event e) {
        signingPresenter.confirm(passwordField.getText(), securityLevelField.getValue());
    }

    @FXML
    void cancel(Event e) {
        signingPresenter.cancel();
    }

}
