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
import java.nio.charset.StandardCharsets;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.OwnershipTransferController;
import org.projectjinxers.controller.ValidationContext;

/**
 * Ownership requests can be issued if a user wants to take ownership of an abandoned document.
 * 
 * @author ProjectJinxers
 */
public class OwnershipRequest extends ToggleRequest implements DocumentAction, Loader<OwnershipRequest> {

    private static final String KEY_ANONYMOUS_VOTING = "n";
    private static final String KEY_VOTING_HASH_SEED = "h";
    private static final String KEY_TIMESTAMP = "t";
    private static final String KEY_DOCUMENT = "d";

    private boolean anonymousVoting;
    private int votingHashSeed;
    private long timestamp;
    private IPLDObject<Document> document;

    OwnershipRequest() {

    }

    /**
     * Constructor.
     * 
     * @param document        the document
     * @param anonymousVoting indicates whether or not a voting, if necessary, has to be anonymous
     */
    public OwnershipRequest(IPLDObject<UserState> userState, IPLDObject<Document> document, boolean anonymousVoting) {
        super(userState);
        this.document = document;
        this.anonymousVoting = anonymousVoting;
        this.votingHashSeed = (int) (Math.random() * Integer.MAX_VALUE);
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        super.read(reader, context, validationContext, eager, metadata);
        this.anonymousVoting = Boolean.TRUE.equals(reader.readBoolean(KEY_ANONYMOUS_VOTING));
        this.votingHashSeed = reader.readNumber(KEY_VOTING_HASH_SEED).intValue();
        this.timestamp = reader.readNumber(KEY_TIMESTAMP).longValue();
        this.document = reader.readLinkObject(KEY_DOCUMENT, context, validationContext, LoaderFactory.DOCUMENT, eager);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        super.write(writer, signer, context);
        writer.writeIfTrue(KEY_ANONYMOUS_VOTING, anonymousVoting);
        writer.writeNumber(KEY_VOTING_HASH_SEED, votingHashSeed);
        writer.writeNumber(KEY_TIMESTAMP, timestamp);
        writer.writeLink(KEY_DOCUMENT, document, null, null);
    }

    /**
     * @return whether or not the voting, if necessary, is to be anonymous
     */
    public boolean isAnonymousVoting() {
        return anonymousVoting;
    }

    /**
     * @return the creation timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public IPLDObject<Document> getDocument() {
        return document;
    }

    @Override
    protected ToggleRequest createCopyInstance() {
        OwnershipRequest res = new OwnershipRequest();
        res.anonymousVoting = anonymousVoting;
        res.votingHashSeed = votingHashSeed;
        res.timestamp = timestamp;
        res.document = document;
        return res;
    }

    @Override
    public byte[] hashBase(IPLDWriter writer, IPLDContext context) throws IOException {
        String hashBase = OwnershipTransferController.composePubMessageRequest(anonymousVoting, expectUserHash(),
                document.getMultihash());
        return hashBase.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public OwnershipRequest getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public OwnershipRequest getLoaded() {
        return this;
    }

}
