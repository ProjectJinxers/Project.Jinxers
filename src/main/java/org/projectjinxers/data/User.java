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
package org.projectjinxers.data;

import org.ethereum.crypto.ECKey;
import org.projectjinxers.account.Users;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.ModelController;
import org.projectjinxers.controller.ValidationException;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.UserState;

/**
 * @author ProjectJinxers
 *
 */
public class User {

    public static User createNewUser(String name, boolean save) {
        User res = new User();
        res.name = name;
        res.save = save;
        return res;
    }

    public static User createExistingUser(String multihash, String nameCheck, boolean save) {
        User res = new User();
        res.name = nameCheck;
        res.multihash = multihash;
        res.save = save;
        return res;
    }

    private String name;
    private String multihash;

    private transient boolean save;
    private transient IPLDObject<org.projectjinxers.model.User> userObject;

    User() {
        this.save = true;
    }

    public String getName() {
        return name;
    }

    public String getMultihash() {
        return multihash;
    }

    public boolean isSave() {
        return save;
    }

    public void setSave(boolean save) {
        this.save = save;
    }

    public IPLDObject<org.projectjinxers.model.User> getUserObject() {
        return userObject;
    }

    public boolean loadUserObject(Group group) throws Exception {
        ModelController controller = group.getController();
        if (controller != null) {
            IPLDObject<ModelState> currentValidatedState = controller.getCurrentValidatedState();
            if (currentValidatedState != null) {
                IPLDObject<UserState> userState = currentValidatedState.getMapped().getUserState(multihash);
                if (userState != null) {
                    userObject = userState.getMapped().getUser();
                    String name = userObject.getMapped().getUsername();
                    if (this.name == null) {
                        this.name = name;
                    }
                    else if (!this.name.equals(name)) {
                        throw new ValidationException("username mismatch");
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public IPLDObject<org.projectjinxers.model.User> createNewUserObject(String password, int securityLevel) {
        if (name == null) {
            throw new IllegalStateException("can't create user without name");
        }
        ECKey account = Users.createAccount(name, password, securityLevel);
        org.projectjinxers.model.User user = new org.projectjinxers.model.User(name, account.getPubKey());
        userObject = new IPLDObject<>(user);
        return userObject;
    }

    public void didSaveUserObject() {
        this.multihash = userObject.getMultihash();
    }

}
