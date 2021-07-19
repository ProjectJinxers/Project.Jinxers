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

import org.projectjinxers.data.Group;
import org.projectjinxers.data.Settings;
import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.DataPresenter;
import org.projectjinxers.ui.common.PJPresenter.View;

import javafx.scene.Scene;

import static org.projectjinxers.util.ObjectUtility.isNullOrBlank;

/**
 * @author ProjectJinxers
 *
 */
public class GroupPresenter extends DataPresenter<Group, GroupPresenter.GroupView> {

    interface GroupView extends View {

    }

    private Settings settings;

    protected GroupPresenter(GroupView view, Group data, Settings settings, ProjectJinxers application) {
        super(view, data, application);
        this.settings = settings;
    }

    public Settings getSettings() {
        return settings;
    }

    @Override
    protected Scene createScene() {
        Scene res = new GroupScene(this);
        return res;
    }

    @Override
    protected Group handleData(Group confirmed) {
        String name = confirmed.getName();
        String address = confirmed.getAddress();
        if (isNullOrBlank(name) || isNullOrBlank(address)) {
            getView().showMessage("Please enter all required data.");
            return null;
        }
        Group edited = getData();
        if (edited == null) {
            return confirmed;
        }
        edited.update(confirmed);
        return edited;
    }

}
