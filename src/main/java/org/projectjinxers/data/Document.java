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

import org.projectjinxers.config.Config;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.ModelController;
import org.projectjinxers.model.LoaderFactory;

/**
 * @author ProjectJinxers
 *
 */
public class Document {

    public enum Kind {

        NEW, STANDALONE, LOADED

    }

    private transient Group group;
    private transient User user;
    private String multihash;
    private transient String importURL;
    private transient IPLDObject<org.projectjinxers.model.Document> documentObject;
    private Kind kind;

    public Document(Group group, IPLDObject<org.projectjinxers.model.Document> documentObject) {
        this.group = group;
        this.multihash = documentObject.getMultihash();
        this.documentObject = documentObject;
        this.kind = Kind.LOADED;
    }

    public Document(Group group, String multihash) {
        this.group = group;
        this.multihash = multihash;
        if (group != null) {
            group.addStandaloneDocument(this);
        }
        this.kind = Kind.STANDALONE;
    }

    public Document(Group group, User user, IPLDObject<org.projectjinxers.model.Document> documentObject,
            String importURL) {
        this.group = group;
        this.user = user;
        this.documentObject = documentObject;
        this.importURL = importURL;
        this.kind = Kind.NEW;
    }

    public Group getGroup() {
        return group;
    }

    public User getUser() {
        return user;
    }

    public String getMultihash() {
        return multihash;
    }

    public String getImportURL() {
        return importURL;
    }

    public IPLDObject<org.projectjinxers.model.Document> getDocumentObject() {
        return documentObject;
    }

    public IPLDObject<org.projectjinxers.model.Document> getOrLoadDocumentObject() throws Exception {
        if (documentObject == null && multihash != null) {
            ModelController controller = ModelController.getModelController(Config.getSharedInstance());
            IPLDObject<org.projectjinxers.model.Document> documentObject = new IPLDObject<>(multihash,
                    LoaderFactory.DOCUMENT.createLoader(), controller.getContext(), null);
            if (documentObject.getMapped() != null) {
                this.documentObject = documentObject;
            }
        }
        return documentObject;
    }

    public Kind getKind() {
        return kind;
    }

}
