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

import org.projectjinxers.ui.common.PJScene;

/**
 * @author ProjectJinxers
 *
 */
public class SigningScene extends PJScene<SigningPresenter.SigningView, SigningPresenter> {

    protected SigningScene(SigningPresenter presenter) {
        super("SigningView.fxml", presenter);
    }

}
