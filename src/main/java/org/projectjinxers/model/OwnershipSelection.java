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
import java.util.Date;
import java.util.Map;

import org.projectjinxers.account.Signer;
import org.projectjinxers.ipld.IPLDContext;
import org.projectjinxers.ipld.IPLDReader;
import org.projectjinxers.ipld.IPLDReader.KeyProvider;
import org.projectjinxers.ipld.IPLDWriter;

/**
 * OwnershipSelection instances can be voted for if more than one user requests ownership of an abandoned document.
 * 
 * @author ProjectJinxers
 */
public class OwnershipSelection implements Votable {

    private static final String KEY_ANONYMOUS = "a";
    private static final String KEY_HASH_SEED = "h";
    private static final String KEY_DEADLINE = "d";
    static final String KEY_SELECTION = "s";

    private static final KeyProvider<OwnershipRequest> SELECTION_KEY_PROVIDER = new KeyProvider<OwnershipRequest>() {
    };

    private boolean anonymous;
    private int hashSeed;
    private Date deadline;
    private Map<String, IPLDObject<OwnershipRequest>> selection;

    private Object[] allValues;

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.anonymous = Boolean.TRUE.equals(reader.readBoolean(KEY_ANONYMOUS));
        this.hashSeed = reader.readNumber(KEY_HASH_SEED).intValue();
        this.deadline = new Date(reader.readNumber(KEY_DEADLINE).longValue());
        this.selection = reader.readLinkObjects(KEY_SELECTION, context, validationContext,
                LoaderFactory.OWNERSHIP_REQUEST, eager, SELECTION_KEY_PROVIDER);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeIfTrue(KEY_ANONYMOUS, anonymous);
        writer.writeNumber(KEY_HASH_SEED, hashSeed);
        writer.writeNumber(KEY_DEADLINE, deadline.getTime());
        writer.writeLinkObjects(KEY_SELECTION, selection, signer, null);
    }

    @Override
    public boolean isAnonymous() {
        return anonymous;
    }

    @Override
    public int getHashSeed() {
        return hashSeed;
    }

    @Override
    public Date getDeadline() {
        return deadline;
    }

    @Override
    public Vote createVote() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object[] getAllValues() {
        if (allValues == null && selection != null) {
            allValues = new Object[selection.size()];
            int i = 0;
            for (IPLDObject<OwnershipRequest> request : selection.values()) {
                allValues[i++] = request.getMapped().getUser();
            }
        }
        return allValues;
    }

    @Override
    public boolean checkWinner() {
        // TODO Auto-generated method stub
        return false;
    }

}
