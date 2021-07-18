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
package org.projectjinxers.ui.group;

import static org.projectjinxers.ui.util.TextFieldUtility.unfocus;

import java.net.URL;
import java.util.ResourceBundle;

import org.projectjinxers.config.Config;
import org.projectjinxers.data.Group;
import org.projectjinxers.data.Settings;
import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.PJView;
import org.projectjinxers.ui.util.TextFieldUtility;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.util.converter.IntegerStringConverter;
import javafx.util.converter.LongStringConverter;

/**
 * @author ProjectJinxers
 *
 */
public class GroupView
        implements PJView<GroupPresenter.GroupView, GroupPresenter>, GroupPresenter.GroupView, Initializable {

    public static GroupPresenter createGroupPresenter(Group group, Settings settings, ProjectJinxers application) {
        GroupView groupView = new GroupView();
        GroupPresenter res = new GroupPresenter(groupView, group, settings, application);
        groupView.groupPresenter = res;
        return res;
    }

    private GroupPresenter groupPresenter;

    @FXML
    private TextField nameField;
    @FXML
    private TextField addressField;
    @FXML
    private TextField timestampToleranceField;
    @FXML
    private TextField secretObfuscationParamField;
    @FXML
    private CheckBox saveBox;

    private ObjectProperty<Long> timestampTolerance;
    private ObjectProperty<Integer> secretObfuscationParam;

    @Override
    public GroupPresenter getPresenter() {
        return groupPresenter;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        TextFormatter<Long> formatter = new TextFormatter<>(new LongStringConverter(), null,
                TextFieldUtility.NUMBER_FILTER);
        timestampToleranceField.setTextFormatter(formatter);
        Group group = groupPresenter.getData();
        Long initialValue = group == null ? null : group.getTimestampTolerance();
        timestampTolerance = new SimpleObjectProperty<>(initialValue);
        timestampTolerance.bind(formatter.valueProperty());
        if (initialValue != null) {
            timestampToleranceField.setText(String.valueOf(initialValue));
        }
        timestampToleranceField
                .setTooltip(new Tooltip("The timestamp tolerance in milliseconds. If empty, the default value ("
                        + Config.DEFAULT_TIMESTAMP_TOLERANCE / 1000 + " seconds) will be used."));
        TextFormatter<Integer> fmt = new TextFormatter<>(new IntegerStringConverter(), null,
                TextFieldUtility.NUMBER_FILTER);
        secretObfuscationParamField.setTextFormatter(fmt);
        Integer secretInitialValue = group == null ? null : group.getSecretObfuscationParam();
        secretObfuscationParam = new SimpleObjectProperty<>(secretInitialValue);
        secretObfuscationParam.bind(fmt.valueProperty());
        if (secretInitialValue != null) {
            secretObfuscationParamField.setText(String.valueOf(secretInitialValue));
        }
        secretObfuscationParamField.setTooltip(
                new Tooltip("The secret obfuscation param (int). If empty, the default value will be used."));
        unfocus(nameField);
        if (group == null) {
            if (groupPresenter.getSettings().isSaveGroups()) {
                saveBox.setSelected(true);
            }
        }
        else {
            nameField.setText(group.getName());
            addressField.setText(group.getAddress());
            addressField.setEditable(false);
            saveBox.setSelected(group.isSave());
        }
    }

    @FXML
    void confirm(Event e) {
        groupPresenter.confirmed(new Group(nameField.getText(), addressField.getText(), timestampTolerance.get(),
                secretObfuscationParam.get(), saveBox.isSelected()));
    }

    @FXML
    void cancel(Event e) {
        groupPresenter.canceled();
    }

}
