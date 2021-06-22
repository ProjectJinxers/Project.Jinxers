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
 * @author ProjectJinxers
 *
 */
public class SealedDocument implements IPLDSerializable, Loader<SealedDocument> {

    private static final String KEY_SETTLEMENT_REQUEST = "r";
    private static final String KEY_USER_STATE = "u";

    private IPLDObject<SettlementRequest> settlementRequest;
    private IPLDObject<UserState> userState;

    SealedDocument() {

    }

    public SealedDocument(IPLDObject<SettlementRequest> settlementRequest, IPLDObject<UserState> userState) {
        this.settlementRequest = settlementRequest;
        this.userState = userState;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.settlementRequest = reader.readLinkObject(KEY_SETTLEMENT_REQUEST, context, validationContext,
                LoaderFactory.SETTLEMENT_REQUEST, eager);
        this.userState = reader.readLinkObject(KEY_USER_STATE, context, validationContext, LoaderFactory.USER_STATE,
                eager);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeLink(KEY_SETTLEMENT_REQUEST, settlementRequest, signer, context);
        writer.writeLink(KEY_USER_STATE, userState, signer, context);
    }

    public IPLDObject<SettlementRequest> getSettlementRequest() {
        return settlementRequest;
    }

    public IPLDObject<UserState> getUserState() {
        return userState;
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
