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
import org.projectjinxers.controller.ValidationContext;

/**
 * Granted ownerships can be validated by checking the existence of specific instances of this class.
 * 
 * @author ProjectJinxers
 */
public class GrantedOwnership implements IPLDSerializable, Loader<GrantedOwnership> {

    private static final String KEY_DOCUMENT = "d";
    private static final String KEY_MODEL_STATE = "m";

    private IPLDObject<Document> document;
    private IPLDObject<ModelState> modelState; // at request resolution time

    GrantedOwnership() {

    }

    /**
     * Constructor for a new granted ownership.
     * 
     * @param document   the transferred document
     * @param modelState the model state at request resolution time
     */
    public GrantedOwnership(IPLDObject<Document> document, IPLDObject<ModelState> modelState) {
        this.document = document;
        this.modelState = modelState;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.document = reader.readLinkObject(KEY_DOCUMENT, context, validationContext, LoaderFactory.DOCUMENT, eager);
        this.modelState = reader.readLinkObject(KEY_MODEL_STATE, context, null, LoaderFactory.MODEL_STATE, eager);
        if (validationContext != null) {
            validationContext.addMustKeepModelState(modelState);
        }
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeLink(KEY_DOCUMENT, document, signer, null);
        writer.writeLink(KEY_MODEL_STATE, modelState, signer, null);
    }

    public IPLDObject<Document> getDocument() {
        return document;
    }

    @Override
    public GrantedOwnership getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public GrantedOwnership getLoaded() {
        return this;
    }

}
