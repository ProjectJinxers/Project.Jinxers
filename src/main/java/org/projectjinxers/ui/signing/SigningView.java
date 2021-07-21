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

import org.projectjinxers.data.User;
import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.PJView;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * @author ProjectJinxers
 *
 */
public class SigningView
        implements PJView<SigningPresenter.SigningView, SigningPresenter>, SigningPresenter.SigningView {

    public static SigningPresenter createSigningPresenter(User user, ProjectJinxers application) {
        SigningView signingView = new SigningView();
        SigningPresenter res = new SigningPresenter(signingView, user, application);
        signingView.signingPresenter = res;
        signingView.username = new SimpleStringProperty(user.getName());
        signingView.securityLevel = new SimpleStringProperty(String.valueOf(user.getSecurityLevel()));
        return res;
    }

    private SigningPresenter signingPresenter;

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField securityLevelField;

    private StringProperty username;

    private StringProperty securityLevel;

    public StringProperty usernameProperty() {
        return username;
    }

    public String getUsername() {
        return username.get();
    }

    public StringProperty securityLevelProperty() {
        return securityLevel;
    }

    public String getSecurityLevel() {
        return securityLevel.get();
    }

    @Override
    public SigningPresenter getPresenter() {
        return signingPresenter;
    }

    @FXML
    void confirm(Event e) {
        signingPresenter.confirm(passwordField.getText());
    }

    @FXML
    void cancel(Event e) {

    }

}
