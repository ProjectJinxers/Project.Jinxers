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
import static org.projectjinxers.util.ObjectUtility.parseLongValues;;

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

    void confirm(String name, String address, Long timestampTolerance, String secretObfuscationParams, boolean save) {
        if (isNullOrBlank(name) || isNullOrBlank(address)) {
            getView().showMessage("Please enter all required data.");
        }
        else {
            long[] obfuscationParams;
            if (isNullOrBlank(secretObfuscationParams)) {
                obfuscationParams = null;
            }
            else {
                try {
                    obfuscationParams = parseLongValues(secretObfuscationParams);
                }
                catch (NumberFormatException e) {
                    getView().showMessage(
                            "Failed to parse the secret obfuscation parameters. Please enter lines of positive long values without inner empty lines.");
                    return;
                }
            }
            Group edited = getData();
            if (edited == null) {
                confirmed(new Group(name, address, timestampTolerance, obfuscationParams, save));
            }
            else {
                edited.update(new Group(name, address, timestampTolerance, obfuscationParams, save));
                confirmed(edited);
            }
        }
    }

}
