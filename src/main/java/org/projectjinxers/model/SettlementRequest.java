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
 * Users can issue SettlementRequests to initiate the settlement of a document (discussion).
 * 
 * @author ProjectJinxers
 */
public class SettlementRequest implements DocumentAction, Loader<SettlementRequest> {

    private static final String KEY_TIMESTAMP = "t";
    private static final String KEY_DOCUMENT = "d";
    private static final String KEY_USER_STATE = "u";

    private long timestamp;
    private IPLDObject<Document> document;
    private IPLDObject<UserState> userState;

    SettlementRequest() {

    }

    public SettlementRequest(long timestamp, IPLDObject<Document> document, IPLDObject<UserState> userState) {
        this.timestamp = timestamp;
        this.document = document;
        this.userState = userState;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.timestamp = reader.readNumber(KEY_TIMESTAMP).longValue();
        this.document = reader.readLinkObject(KEY_DOCUMENT, context, validationContext, LoaderFactory.DOCUMENT, eager);
        this.userState = reader.readLinkObject(KEY_USER_STATE, context, validationContext, LoaderFactory.USER_STATE,
                eager);
        if (validationContext != null) {
            validationContext.addMustValidateUserState(userState);
        }
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context, ProgressListener progressListener)
            throws IOException {
        writer.writeNumber(KEY_TIMESTAMP, timestamp);
        writer.writeLink(KEY_DOCUMENT, document, null, null, null);
        writer.writeLink(KEY_USER_STATE, userState, null, null, null);
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public IPLDObject<Document> getDocument() {
        return document;
    }

    public IPLDObject<UserState> getUserState() {
        return userState;
    }

    public void synchronizeTimestamp(SettlementRequest other) {
        this.timestamp = other == null ? System.currentTimeMillis() : other.timestamp;
    }

    @Override
    public SettlementRequest getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public SettlementRequest getLoaded() {
        return this;
    }

}
