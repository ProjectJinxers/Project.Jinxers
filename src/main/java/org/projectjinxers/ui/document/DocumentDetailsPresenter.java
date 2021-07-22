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
package org.projectjinxers.ui.document;

import org.projectjinxers.data.Document;
import org.projectjinxers.ui.common.ChildPresenter;
import org.projectjinxers.ui.common.ChildScene;
import org.projectjinxers.ui.common.PJPresenter;
import org.projectjinxers.ui.common.PJPresenter.View;

import javafx.scene.Parent;

/**
 * @author ProjectJinxers
 *
 */
public class DocumentDetailsPresenter extends ChildPresenter<DocumentDetailsPresenter.DocumentDetailsView> {

    public interface DocumentDetailsView extends View {

    }

    private Document document;

    public DocumentDetailsPresenter(DocumentDetailsView view, PJPresenter<?> parent, Parent root) {
        super(view, parent, root);
    }

    @Override
    protected ChildScene<DocumentDetailsView, ?> createChildScene(Parent root) {
        return new DocumentDetailsScene(this, root);
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
        getView().updateView();
    }

}
