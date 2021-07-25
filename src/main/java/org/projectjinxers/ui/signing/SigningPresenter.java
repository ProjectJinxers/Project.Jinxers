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

import org.projectjinxers.account.ECCSigner;
import org.projectjinxers.account.Signer;
import org.projectjinxers.data.User;
import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.PJPresenter;
import org.projectjinxers.ui.common.PJPresenter.View;

import javafx.scene.Scene;

/**
 * @author ProjectJinxers
 *
 */
public class SigningPresenter extends PJPresenter<SigningPresenter.SigningView> {

    public interface SigningView extends View {

    }

    public interface SigningListener {

        void didCreateSigner(Signer signer);

    }

    private User user;

    private SigningListener listener;

    public SigningPresenter(SigningView view, User user, ProjectJinxers application) {
        super(view, application);
        this.user = user;
    }

    @Override
    protected Scene createScene() {
        Scene res = new SigningScene(this);
        return res;
    }

    public int getSecurityLevel() {
        return user.getSecurityLevel();
    }

    public void setListener(SigningListener listener) {
        this.listener = listener;
    }

    void confirm(String password, Integer securityLevel) {
        ECCSigner signer = new ECCSigner(user.getName(), password, securityLevel == null ? 0 : securityLevel);
        if (signer.checkPublicKey(user.getPublicKey())) {
            listener.didCreateSigner(signer);
            getStage().close();
        }
        else {
            getView().showMessage("Incorrect password or security level");
        }
    }

    void cancel() {
        getStage().close();
    }

}
