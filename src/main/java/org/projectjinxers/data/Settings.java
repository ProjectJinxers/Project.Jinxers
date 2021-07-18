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

/**
 * @author ProjectJinxers
 *
 */
public class Settings {

    private boolean saveGroups;
    private boolean saveUsers;

    public Settings() {

    }

    public boolean isSaveGroups() {
        return saveGroups;
    }

    public void setSaveGroups(boolean saveGroups) {
        this.saveGroups = saveGroups;
    }

    public boolean isSaveUsers() {
        return saveUsers;
    }

    public void setSaveUsers(boolean saveUsers) {
        this.saveUsers = saveUsers;
    }

}
