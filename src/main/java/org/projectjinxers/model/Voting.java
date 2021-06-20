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
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDReader.KeyProvider;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.ValidationContext;

/**
 * Votings can be initiated by eligible users. They can be anonymous. They are executed without any external factors or
 * actors.
 * 
 * @author ProjectJinxers
 */
public class Voting implements IPLDSerializable, Loader<Voting> {

    private static final String KEY_SEED = "f"; // f for fallback
    private static final String KEY_OBFUSCATION_VERSION = "o";
    private static final String KEY_SUBJECT = "s";
    private static final String KEY_VOTES = "v";

    private static final KeyProvider<Vote> ANONYMOUS_VOTE_KEY_PROVIDER = new KeyProvider<>() {
        @Override
        public String getKey(IPLDObject<Vote> object) {
            return Base64.encodeBase64String(object.getMapped().getInvitationKey());
        }
    };
    private static final KeyProvider<Vote> NON_ANONYMOUS_VOTE_KEY_PROVIDER = new KeyProvider<>() {
        @Override
        public String getKey(IPLDObject<Vote> object) {
            return new String(object.getMapped().getInvitationKey(), StandardCharsets.UTF_8);
        }
    };

    private int seed;
    private int obfuscationVersion;
    private IPLDObject<Votable> subject;
    private Map<String, IPLDObject<Vote>> votes;

    Voting() {

    }

    /**
     * Constructor.
     * 
     * @param subject            the subject
     * @param obfuscationVersion the version of the hash obfuscation algorithm
     */
    public Voting(IPLDObject<Votable> subject, int obfuscationVersion) {
        this.seed = (int) (Math.random() * Integer.MAX_VALUE);
        this.obfuscationVersion = obfuscationVersion;
        this.subject = subject;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.seed = reader.readNumber(KEY_SEED).intValue();
        this.obfuscationVersion = reader.readNumber(KEY_OBFUSCATION_VERSION).intValue();
        this.subject = reader.readLinkObject(KEY_OBFUSCATION_VERSION, context, validationContext, LoaderFactory.VOTABLE,
                eager);
        this.votes = reader.readLinkObjects(KEY_VOTES, context, validationContext, LoaderFactory.VOTE, eager,
                subject.getMapped().isAnonymous() ? ANONYMOUS_VOTE_KEY_PROVIDER : NON_ANONYMOUS_VOTE_KEY_PROVIDER);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeNumber(KEY_SEED, seed);
        writer.writeNumber(KEY_OBFUSCATION_VERSION, obfuscationVersion);
        writer.writeLink(KEY_SUBJECT, subject, signer, context);
        writer.writeLinkObjects(KEY_VOTES, votes, signer, context);
    }

    /**
     * @return the subject of the voting
     */
    public IPLDObject<Votable> getSubject() {
        return subject;
    }

    /**
     * @return whether or not the voting is (must be) anonymous
     */
    public boolean isAnonymous() {
        return subject.getMapped().isAnonymous();
    }

    /**
     * @return the deadline after which no more Votes can be placed
     */
    public Date getDeadline() {
        return subject.getMapped().getDeadline();
    }

    /**
     * Calculates the invitation key for a given user. The result depends on the anonymity of the voting. If it is
     * anonymous, the result is calculated by a publich algorithm with secret parameters. Otherwise the result is simply
     * the the UTF-8 byte array for the user hash. In both cases, the user must exist in the given modelState.
     * 
     * @param userHash   the multihash of the user
     * @param modelState the model state
     * @return the invitation key for the user or null, if the user is not eligible to vote
     */
    public byte[] getInvitationKey(String userHash, ModelState modelState) {
        if (modelState.containsUserState(userHash)) {
            if (isAnonymous()) {
                // TODO hash userHash using seed and obfuscationVersion
            }
            return userHash.getBytes(StandardCharsets.UTF_8);
        }
        return null;
    }

    @Override
    public Voting getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public Voting getLoaded() {
        return this;
    }

}
