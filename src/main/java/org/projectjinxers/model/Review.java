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
 * Review instances represent users' reviews of a document.
 * 
 * @author ProjectJinxers
 */
public class Review extends Document implements DocumentAction, Loader<Review> {

    private static final String KEY_APPROVE = "r";
    static final String KEY_DOCUMENT = "o";

    private Boolean approve;
    private IPLDObject<Document> document;

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        super.read(reader, context, validationContext, eager, metadata);
        this.approve = reader.readBoolean(KEY_APPROVE);
        this.document = reader.readLinkObject(KEY_DOCUMENT, context, validationContext, LoaderFactory.DOCUMENT, eager);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        super.write(writer, signer, context);
        writer.writeBoolean(KEY_APPROVE, approve);
        writer.writeLink(KEY_DOCUMENT, document, signer, null);
    }

    /**
     * @return the approve value
     */
    public Boolean getApprove() {
        return approve;
    }

    @Override
    public IPLDObject<Document> getDocument() {
        return document;
    }

    @Override
    protected Document createCopyInstance() {
        Review res = new Review();
        res.approve = approve;
        res.document = document;
        return res;
    }

    @Override
    public Review getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public Review getLoaded() {
        return this;
    }

}
