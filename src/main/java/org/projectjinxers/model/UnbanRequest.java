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
import java.util.Date;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.ValidationContext;
import org.projectjinxers.controller.ValidationException;
import org.projectjinxers.util.ModelUtility;

/**
 * Unban requests can be issued by banned users on a false-post basis.
 * 
 * @author ProjectJinxers
 */
public class UnbanRequest extends ToggleRequest implements DocumentAction, Votable, Loader<UnbanRequest> {

    private static final String KEY_ANONYMOUS = "o";
    private static final String KEY_HASH_SEED = "s";
    private static final String KEY_DEADLINE = "d";
    private static final String KEY_DOCUMENT = "c";

    private static final Boolean[] ALL_VALUES = { Boolean.TRUE, Boolean.FALSE, null };
    private static final Integer[] ALL_VALUE_INDICES = { 0, 1, 2 };
    private static final byte[][] ALL_VALUE_HASH_BASES = new byte[][] { "true".getBytes(StandardCharsets.UTF_8),
            "false".getBytes(StandardCharsets.UTF_8), "null".getBytes(StandardCharsets.UTF_8) };

    private boolean anonymous;
    private int hashSeed;
    private Date deadline;
    private IPLDObject<Document> document;

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        super.read(reader, context, validationContext, eager, metadata);
        this.anonymous = Boolean.TRUE.equals(reader.readBoolean(KEY_ANONYMOUS));
        this.hashSeed = reader.readNumber(KEY_HASH_SEED).intValue();
        this.deadline = new Date(reader.readNumber(KEY_DEADLINE).longValue());
        this.document = reader.readLinkObject(KEY_DOCUMENT, context, validationContext, LoaderFactory.DOCUMENT, eager);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        super.write(writer, signer, context);
        writer.writeBoolean(KEY_ANONYMOUS, anonymous);
        writer.writeNumber(KEY_HASH_SEED, hashSeed);
        writer.writeNumber(KEY_DEADLINE, deadline.getTime());
        writer.writeLink(KEY_DOCUMENT, document, signer, null);
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
    public Vote createVote(byte[] invitationKey, int valueIndex, int seed, int obfuscationVersion) {
        if (anonymous) {
            int valueHashObfuscation;
            do {
                valueHashObfuscation = (int) (Math.random() * Integer.MAX_VALUE);
            }
            while (valueHashObfuscation == 0);
            return new ValueVote(invitationKey, ModelUtility.obfuscateHash(ALL_VALUE_HASH_BASES[valueIndex], seed,
                    obfuscationVersion, valueHashObfuscation), false, valueHashObfuscation);
        }
        return new YesNoMaybeVote(invitationKey, ALL_VALUES[valueIndex]);
    }

    @Override
    public Object[] getAllValues(boolean forDisplay) {
        return forDisplay ? ALL_VALUES : ALL_VALUE_INDICES;
    }

    @Override
    public byte[][] getAllValueHashBases() {
        return null;
    }

    @Override
    public int getPlainTextValueIndex(Object value) {
        if (value == null) {
            return 2;
        }
        return Boolean.TRUE.equals(value) ? 0 : 1;
    }

    @Override
    public void expectWinner(Object value, int[] counts) {
        if (counts[0] <= counts[1]) {
            throw new ValidationException("Expected yes count to be greater than no count");
        }
    }

    @Override
    public IPLDObject<Document> getDocument() {
        return document;
    }

    @Override
    protected ToggleRequest createCopyInstance() {
        UnbanRequest res = new UnbanRequest();
        res.anonymous = anonymous;
        res.hashSeed = hashSeed;
        res.deadline = deadline;
        res.document = document;
        return res;
    }

    @Override
    public UnbanRequest getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public UnbanRequest getLoaded() {
        return this;
    }

    @Override
    public void validate(Voting voting, ValidationContext validationContext) {
        String userHash = expectUserHash();
        User user = validationContext.getCurrentValidLocalState().getMapped().expectUserState(userHash).getMapped()
                .getUser().getMapped();
        validationContext.getContext().verifySignature(voting.getSubject(), Signer.VERIFIER, user);
        IPLDObject<UserState> userState = voting.getInitialModelState().getMapped().expectUserState(userHash);
        UnbanRequest unbanRequest;
        if (userState == getUserState()) {
            unbanRequest = this;
        }
        else {
            unbanRequest = userState.getMapped().expectUnbanRequest(document.getMultihash()).getMapped();
        }
        if (!unbanRequest.isActive()) {
            throw new ValidationException("created voting from inactive unban request");
        }
    }

}
