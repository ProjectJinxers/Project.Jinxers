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

import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.PJPresenter.View;

/**
 * @author ProjectJinxers
 *
 */
public abstract class DataPresenter<D, V extends View> extends PJPresenter<V> {

    public interface DataListener<D> {

        boolean didConfirmData(D data);

        default void didCancel() {

        }

    }

    private D data;
    private DataListener<D> listener;

    protected DataPresenter(V view, D data, ProjectJinxers application) {
        super(view, application);
        this.data = data;
    }

    public D getData() {
        return data;
    }

    public void setListener(DataListener<D> listener) {
        this.listener = listener;
    }

    public boolean confirmed(D confirmed) {
        D data = handleData(confirmed);
        if (data != null && (listener == null || listener.didConfirmData(data))) {
            getStage().close();
            return true;
        }
        return false;
    }

    public void canceled() {
        if (listener != null) {
            listener.didCancel();
        }
        getStage().close();
    }

    protected D handleData(D confirmed) {
        return confirmed;
    }

}
