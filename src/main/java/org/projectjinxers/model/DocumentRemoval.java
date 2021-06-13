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
package org.projectjinxers.model;

import java.io.IOException;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDWriter;

/**
 * In order to be able to validate the state, there has to be some kind of info, that a document has been deleted
 * (a.k.a. unlinked - there is no guaranteed deletion) documents). Instances of this class provide that piece of info.
 * 
 * @author ProjectJinxers
 */
public class DocumentRemoval implements DocumentAction {

    private static final String KEY_DOCUMENT = "d";

    private IPLDObject<Document> document;

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.document = reader.readLinkObject(KEY_DOCUMENT, context, validationContext, LoaderFactory.DOCUMENT, eager);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeLink(KEY_DOCUMENT, document, signer, null);
    }

    @Override
    public IPLDObject<Document> getDocument() {
        return document;
    }

}
