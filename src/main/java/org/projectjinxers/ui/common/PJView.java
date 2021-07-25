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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import org.projectjinxers.ui.common.PJPresenter.View;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

/**
 * @author ProjectJinxers
 *
 */
public interface PJView<V extends View, P extends PJPresenter<V>> extends View {

    P getPresenter();

    @Override
    default void showMessage(String message) {
        showMessage(message, "Info", "Info");
    }

    @Override
    default void showMessage(String message, String title, String header) {
        Alert alert = new Alert(AlertType.INFORMATION, message, ButtonType.OK);
        if (title != null) {
            alert.setTitle(title);
        }
        if (header != null) {
            alert.setHeaderText(header);
        }
        alert.showAndWait();
    }

    @Override
    default boolean askForConfirmation(String message) {
        return askForConfirmation(message, null, null);
    }

    @Override
    default boolean askForConfirmation(String message, String title, String header) {
        Alert alert = new Alert(AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.CANCEL);
        if (title != null) {
            alert.setTitle(title);
        }
        if (header != null) {
            alert.setHeaderText(header);
        }
        Optional<ButtonType> showAndWait = alert.showAndWait();
        return showAndWait.isPresent() && showAndWait.get() == ButtonType.YES;
    }

    @Override
    default void showError(String message, Throwable throwable) {
        showError(message, null, null, throwable);
    }

    @Override
    default void showError(String message, String title, String header, Throwable throwable) {
        Alert alert = new Alert(AlertType.ERROR, message, ButtonType.OK);
        if (title != null) {
            alert.setTitle(title);
        }
        if (header != null) {
            alert.setHeaderText(header);
        }
        if (throwable != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            String exceptionText = sw.toString();

            Label label = new Label("The exception stacktrace was:");

            TextArea textArea = new TextArea(exceptionText);
            textArea.setEditable(false);
            textArea.setWrapText(true);

            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);
            GridPane.setVgrow(textArea, Priority.ALWAYS);
            GridPane.setHgrow(textArea, Priority.ALWAYS);

            GridPane expContent = new GridPane();
            expContent.setMaxWidth(Double.MAX_VALUE);
            expContent.add(label, 0, 0);
            expContent.add(textArea, 0, 1);

            alert.getDialogPane().setExpandableContent(expContent);
        }
        alert.showAndWait();
    }

}
