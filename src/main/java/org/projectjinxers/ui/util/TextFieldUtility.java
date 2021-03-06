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
package org.projectjinxers.ui.util;

import java.util.function.UnaryOperator;

import javafx.application.Platform;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter.Change;
import javafx.scene.control.TextInputControl;

import static org.projectjinxers.util.ObjectUtility.isNullOrBlank;
import static org.projectjinxers.util.ObjectUtility.isNullOrEmpty;

/**
 * @author ProjectJinxers
 *
 */
public class TextFieldUtility {

    public static final UnaryOperator<Change> NUMBER_FILTER = change -> {
        String text = change.getText();

        if (text.matches("[0-9]*")) {
            return change;
        }

        return null;
    };

    public static final UnaryOperator<Change> NUMBERS_FILTER = change -> {
        String text = change.getText();

        if (text.matches("[0-9\\n]*")) {
            return change;
        }

        return null;
    };

    public static void unfocus(TextField textField) {
        Platform.runLater(() -> {
            textField.getParent().requestFocus();
        });
    }

    public static String checkNotBlank(TextInputControl textField) {
        String check = textField.getText();
        return isNullOrBlank(check) ? null : check;
    }

    public static String checkNotEmpty(TextInputControl textField) {
        String check = textField.getText();
        return isNullOrEmpty(check) ? null : check;
    }

}
