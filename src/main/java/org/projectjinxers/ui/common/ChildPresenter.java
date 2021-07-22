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

import org.projectjinxers.ui.common.PJPresenter.View;

import javafx.scene.Parent;
import javafx.scene.Scene;

/**
 * @author ProjectJinxers
 *
 */
public abstract class ChildPresenter<V extends View> extends PJPresenter<V> {

    private PJPresenter<?> parent;
    private ChildScene<V, ?> childScene;
    private Parent root;

    protected ChildPresenter(V view, PJPresenter<?> parent, Parent root) {
        super(view, parent.getApplication());
        this.parent = parent;
        this.root = root;
    }

    @Override
    protected Scene createScene() {
        childScene = createChildScene(root);
        return parent.getScene();
    }

    public ChildScene<V, ?> getChildScene() {
        if (childScene == null) {
            createScene();
        }
        return childScene;
    }

    protected abstract ChildScene<V, ?> createChildScene(Parent root);

}
