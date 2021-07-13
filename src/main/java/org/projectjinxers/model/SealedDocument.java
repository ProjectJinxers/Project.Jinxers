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
import org.projectjinxers.controller.IPLDObject.ProgressListener;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.ValidationContext;

/**
 * @author ProjectJinxers
 *
 */
public class SealedDocument implements IPLDSerializable, Loader<SealedDocument> {

    private static final String KEY_TRUTH_INVERTED = "i";
    private static final String KEY_DOCUMENT = "d";

    private Boolean truthInverted;
    private IPLDObject<Document> document;

    SealedDocument() {

    }

    /**
     * Constructor for a new sealed document.
     * 
     * @param document the document
     */
    public SealedDocument(IPLDObject<Document> document) {
        this.document = document;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.truthInverted = reader.readBoolean(KEY_TRUTH_INVERTED);
        this.document = reader.readLinkObject(KEY_DOCUMENT, context, validationContext, LoaderFactory.DOCUMENT, eager);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context, ProgressListener progressListener)
            throws IOException {
        writer.writeBoolean(KEY_TRUTH_INVERTED, truthInverted);
        writer.writeLink(KEY_DOCUMENT, document, null, null, null);
    }

    public boolean isOriginal() {
        return truthInverted == null;
    }

    public boolean isTruthInverted() {
        return Boolean.TRUE.equals(truthInverted);
    }

    public IPLDObject<Document> getDocument() {
        return document;
    }

    public SealedDocument invertTruth() {
        SealedDocument res = new SealedDocument(document);
        res.truthInverted = !Boolean.TRUE.equals(truthInverted);
        return res;
    }

    @Override
    public SealedDocument getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public SealedDocument getLoaded() {
        return this;
    }

}
