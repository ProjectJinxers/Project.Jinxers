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
package org.projectjinxers.ui.cell;

import org.projectjinxers.data.Group;
import org.projectjinxers.ui.main.MainPresenter;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

/**
 * @author ProjectJinxers
 *
 */
public class GroupCell extends AbstractListCell<Group> {

    private MainPresenter mainPresenter;

    @FXML
    private CheckBox saveBox;

    private StringProperty name;
    private StringProperty address;
    private StringProperty timestampTolerance;
    private StringProperty validModelState;
    private StringProperty multihash;

    public GroupCell(MainPresenter mainPresenter) {
        super("GroupCell.fxml", true);
        this.mainPresenter = mainPresenter;
    }

    public StringProperty nameProperty() {
        if (name == null) {
            name = new SimpleStringProperty();
        }
        return name;
    }

    public String getName() {
        return name.get();
    }

    public StringProperty addressProperty() {
        if (address == null) {
            address = new SimpleStringProperty();
        }
        return address;
    }

    public String getAddress() {
        return address.get();
    }

    public StringProperty timestampToleranceProperty() {
        if (timestampTolerance == null) {
            timestampTolerance = new SimpleStringProperty();
        }
        return timestampTolerance;
    }

    public String getTimestampTolerance() {
        return timestampTolerance.get();
    }

    public StringProperty validModelStateProperty() {
        if (validModelState == null) {
            validModelState = new SimpleStringProperty();
        }
        return validModelState;
    }

    public String getValidModelState() {
        return validModelState.get();
    }

    public StringProperty multihashProperty() {
        if (multihash == null) {
            multihash = new SimpleStringProperty();
        }
        return multihash;
    }

    public String getMultihash() {
        return multihash.get();
    }

    @Override
    protected void update(Group item) {
        name.set(item.getName());
        address.set(item.getAddress());
        timestampTolerance.set(mainPresenter.getTimestampToleranceInfo(item));
        saveBox.setSelected(item.isSave());
        validModelState.set(mainPresenter.getValidModelStateInfo(item));
        multihash.set(mainPresenter.getMultihash(item));
    }

    @Override
    protected void updateContextMenu(ContextMenu contextMenu) {
        ObservableList<MenuItem> items = contextMenu.getItems();
        boolean main = getItem().isMain();
        int itemCount = items.size();
        if (itemCount == 0) {
            items.addAll(createEditItem(), createDeleteItem());
        }
        else if (main && itemCount == 2 || !main && itemCount == 1) {
            if (main) {
                items.remove(1);
            }
            else {
                items.add(createDeleteItem());
            }
        }
    }

    @FXML
    void saveToggled(Event e) {
        Group item = getItem();
        boolean save = saveBox.isSelected();
        item.setSave(save);
        mainPresenter.saveData();
    }

    private MenuItem createEditItem() {
        MenuItem res = new MenuItem("Edit group");
        res.setOnAction(event -> mainPresenter.editGroup(getItem()));
        return res;
    }

    private MenuItem createDeleteItem() {
        MenuItem res = new MenuItem("Delete group");
        res.setOnAction(event -> mainPresenter.deleteGroup(getItem()));
        return res;
    }

}
