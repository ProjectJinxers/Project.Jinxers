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
import org.projectjinxers.config.Config;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.ModelController;
import org.projectjinxers.controller.ValidationException;
import org.projectjinxers.model.LoaderFactory;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.UserState;

/**
 * @author ProjectJinxers
 *
 */
public class User {

    public static User createNewUser(String name, String password, int securityLevel, boolean save) {
        User res = new User();
        res.name = name;
        res.securityLevel = securityLevel;
        ECKey account = Users.createAccount(name, password, securityLevel);
        res.publicKey = account.getPubKey();
        res.save = save;
        return res;
    }

    public static User createExistingUser(String multihash, String nameCheck, int securityLevel, boolean save) {
        User res = new User();
        res.name = nameCheck;
        res.securityLevel = securityLevel;
        res.multihash = multihash;
        res.save = save;
        return res;
    }

    private String name;
    private int securityLevel;
    private byte[] publicKey;
    private String multihash;

    private transient boolean save;
    private transient IPLDObject<org.projectjinxers.model.User> userObject;

    User() {
        this.save = true;
    }

    public String getName() {
        return name;
    }

    public int getSecurityLevel() {
        return securityLevel;
    }

    public byte[] getPublicKey() {
        return publicKey;
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

    public IPLDObject<org.projectjinxers.model.User> getOrLoadUserObject() throws Exception {
        if (userObject == null) {
            if (multihash == null) {
                throw new IllegalStateException("can't load user without multihash");
            }
            IPLDObject<org.projectjinxers.model.User> tmp = new IPLDObject<>(multihash,
                    LoaderFactory.USER.createLoader(),
                    ModelController.getModelController(Config.getSharedInstance()).getContext(), null);
            org.projectjinxers.model.User user = tmp.getMapped();
            if (user == null) {
                throw new IllegalStateException("failed to load user");
            }
            userObject = tmp;
            String name = user.getUsername();
            if (this.name == null) {
                this.name = name;
            }
            else if (!this.name.equals(name)) {
                throw new ValidationException("username mismatch");
            }
        }
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
                    org.projectjinxers.model.User user = userObject.getMapped();
                    String name = user.getUsername();
                    if (this.name == null) {
                        this.name = name;
                    }
                    else if (!this.name.equals(name)) {
                        throw new ValidationException("username mismatch");
                    }
                    this.publicKey = user.getPublicKey();
                    return true;
                }
            }
        }
        return false;
    }

    public IPLDObject<org.projectjinxers.model.User> getOrCreateNewUserObject() {
        if (userObject == null) {
            if (name == null || publicKey == null) {
                throw new IllegalStateException("can't create user without name or public key");
            }
            org.projectjinxers.model.User user = new org.projectjinxers.model.User(name, publicKey);
            userObject = new IPLDObject<>(user);
        }
        return userObject;
    }

    public void didSaveUserObject() {
        this.multihash = userObject.getMultihash();
    }

}
