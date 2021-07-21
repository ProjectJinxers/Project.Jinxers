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

import java.io.IOException;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;

/**
 * @author ProjectJinxers
 *
 */
public abstract class AbstractListCell<T> extends ListCell<T> {

    private String fxmlPath;
    private boolean hasContextMenu;

    private FXMLLoader loader;
    private ContextMenu contextMenu;

    protected AbstractListCell(String fxmlPath) {
        this(fxmlPath, false);
    }

    protected AbstractListCell(String fxmlPath, boolean hasContextMenu) {
        this.fxmlPath = fxmlPath;
        this.hasContextMenu = hasContextMenu;
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            if (hasContextMenu) {
                setContextMenu(null);
            }
        }
        else {
            if (loader == null) {
                loader = new FXMLLoader(getClass().getResource(fxmlPath));
                loader.setController(this);
                try {
                    loader.load();
                    // InputStream is = getClass().getResource(fxmlPath).openStream();
                    // loader.load(is);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (hasContextMenu) {
                    contextMenu = new ContextMenu();
                }
            }
            update(item);
            if (hasContextMenu) {
                updateContextMenu(contextMenu);
                if (getContextMenu() != contextMenu) {
                    setContextMenu(contextMenu);
                }
            }
            else if (getContextMenu() != null) {
                setContextMenu(null);
            }
            setText(null);
            setGraphic(loader.getRoot());
        }
    }

    protected abstract void update(T item);

    protected void updateContextMenu(ContextMenu contextMenu) {

    }

}
