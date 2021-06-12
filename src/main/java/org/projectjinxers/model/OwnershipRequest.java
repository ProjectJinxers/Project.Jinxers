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
import org.projectjinxers.ipld.IPLDReader;
import org.projectjinxers.ipld.IPLDWriter;

/**
 * Ownership requests can be issued if a user wants to take ownership of an abandoned document.
 * 
 * @author ProjectJinxers
 */
public class OwnershipRequest extends ToggleRequest implements DocumentAction, Loader<OwnershipRequest> {

    private static final String KEY_ANONYMOUS_VOTING = "n";
    private static final String KEY_VOTING_HASH_SEED = "h";
    private static final String KEY_DOCUMENT = "d";

    private boolean anonymousVoting;
    private int votingHashSeed;
    private IPLDObject<Document> document;

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        super.read(reader, context, validationContext, eager, metadata);
        this.anonymousVoting = Boolean.TRUE.equals(reader.readBoolean(KEY_ANONYMOUS_VOTING));
        this.votingHashSeed = reader.readNumber(KEY_VOTING_HASH_SEED).intValue();
        this.document = reader.readLinkObject(KEY_DOCUMENT, context, validationContext, LoaderFactory.DOCUMENT, eager);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        super.write(writer, signer, context);
        writer.writeIfTrue(KEY_ANONYMOUS_VOTING, anonymousVoting);
        writer.writeNumber(KEY_VOTING_HASH_SEED, votingHashSeed);
        writer.writeLink(KEY_DOCUMENT, document, signer, null);
    }

    @Override
    public IPLDObject<Document> getDocument() {
        return document;
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
