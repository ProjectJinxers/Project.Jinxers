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
package org.projectjinxers.ui.common;

import java.util.Optional;

import org.projectjinxers.ui.common.PJPresenter.View;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

/**
 * @author ProjectJinxers
 *
 */
public interface PJView<V extends View, P extends PJPresenter<V>> extends View {

    P getPresenter();

    @Override
    default void showMessage(String message) {
        Alert alert = new Alert(AlertType.INFORMATION, message, ButtonType.OK);
        alert.showAndWait();
    }

    @Override
    default boolean askForConfirmation(String message) {
        Alert alert = new Alert(AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.CANCEL);
        Optional<ButtonType> showAndWait = alert.showAndWait();
        return showAndWait.isPresent() && showAndWait.get() == ButtonType.YES;
    }

    @Override
    default void showError(String message, Throwable exception) {
        Alert alert = new Alert(AlertType.ERROR, message, ButtonType.OK);
        alert.showAndWait();
    }

}
