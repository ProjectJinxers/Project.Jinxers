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

import org.projectjinxers.data.User;
import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.DataPresenter;
import org.projectjinxers.ui.common.PJPresenter.View;

import javafx.scene.Scene;

/**
 * @author ProjectJinxers
 *
 */
public class UserPresenter extends DataPresenter<User, UserPresenter.UserView> {

    public interface UserView extends View {

    }

    public UserPresenter(UserView view, User user, ProjectJinxers application) {
        super(view, user, application);
    }

    @Override
    protected Scene createScene() {
        Scene res = new UserScene(this);
        return res;
    }

    void confirm(String multihash, String username, String password, Integer securityLevel, boolean save) {
        if (multihash == null) {
            if (username == null || password == null) {
                getView().showMessage("Please enter a multihash or a valid username and a password");
            }
            else {
                confirmed(User.createNewUser(username, password, securityLevel == null ? 0 : securityLevel, save));
            }
        }
        else {
            confirmed(User.createExistingUser(multihash, username, securityLevel == null ? 0 : securityLevel, save));
        }
    }

}
