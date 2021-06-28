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
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDReader.KeyProvider;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.ValidationContext;
import org.projectjinxers.controller.ValidationException;
import org.projectjinxers.util.ModelUtility;

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
    private static final String KEY_INITIAL_MODEL_STATE = "m";
    private static final String KEY_VOTES = "v";
    private static final String KEY_TALLY = "t";

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

    public static final KeyCollector<Voting> VOTE_KEY_COLLECTOR = new KeyCollector<>() {

        @Override
        public Set<String> getHashes(Voting instance) {
            return instance.votes == null ? null : instance.votes.keySet();
        }

    };

    private int seed;
    private int obfuscationVersion;
    private IPLDObject<Votable> subject;
    private IPLDObject<ModelState> initialModelState;
    private Map<String, IPLDObject<Vote>> votes;
    private IPLDObject<Tally> tally;

    Voting() {

    }

    /**
     * Constructor.
     * 
     * @param subject            the subject
     * @param initialModelState  the initial model state
     * @param obfuscationVersion the version of the hash obfuscation algorithm
     */
    public Voting(IPLDObject<Votable> subject, IPLDObject<ModelState> initialModelState, int obfuscationVersion) {
        do {
            this.seed = (int) (Math.random() * Integer.MAX_VALUE);
        }
        while (seed == 0);
        this.obfuscationVersion = obfuscationVersion;
        this.subject = subject;
        this.initialModelState = initialModelState;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.seed = reader.readNumber(KEY_SEED).intValue();
        this.obfuscationVersion = reader.readNumber(KEY_OBFUSCATION_VERSION).intValue();
        this.subject = reader.readLinkObject(KEY_OBFUSCATION_VERSION, context, validationContext, LoaderFactory.VOTABLE,
                eager);
        this.initialModelState = reader.readLinkObject(KEY_INITIAL_MODEL_STATE, context, null,
                LoaderFactory.MODEL_STATE, eager);
        this.votes = reader.readLinkObjects(KEY_VOTES, context, validationContext, LoaderFactory.VOTE, eager,
                subject.getMapped().isAnonymous() ? ANONYMOUS_VOTE_KEY_PROVIDER : NON_ANONYMOUS_VOTE_KEY_PROVIDER);
        this.tally = reader.readLinkObject(KEY_TALLY, context, validationContext, LoaderFactory.TALLY, eager);
        if (validationContext != null) {
            validationContext.addMustKeepModelState(initialModelState);
        }
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeNumber(KEY_SEED, seed);
        writer.writeNumber(KEY_OBFUSCATION_VERSION, obfuscationVersion);
        writer.writeLink(KEY_SUBJECT, subject, signer, context);
        writer.writeLink(KEY_INITIAL_MODEL_STATE, initialModelState, null, null);
        writer.writeLinkObjects(KEY_VOTES, votes, signer, context);
    }

    /**
     * @return the subject of the voting
     */
    public IPLDObject<Votable> getSubject() {
        return subject;
    }

    public IPLDObject<ModelState> getInitialModelState() {
        return initialModelState;
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

    public boolean hasVotes() {
        return votes != null;
    }

    public IPLDObject<Vote> getVote(String key) {
        return votes == null ? null : votes.get(key);
    }

    public IPLDObject<Tally> getTally() {
        return tally;
    }

    /**
     * Calculates the invitation key for a given user. The result depends on the anonymity of the voting. If it is
     * anonymous, the result is calculated by a public algorithm with secret parameters. Otherwise the result is simply
     * the UTF-8 byte array for the user hash. In both cases, the user must exist in the given modelState.
     * 
     * @param userHash   the multihash of the user
     * @param modelState the model state
     * @return the invitation key for the user or null, if the user is not eligible to vote
     */
    public byte[] getInvitationKey(String userHash, ModelState modelState) {
        if (modelState.containsUserState(userHash)) {
            byte[] toHash = userHash.getBytes(StandardCharsets.UTF_8);
            if (isAnonymous()) {
                return ModelUtility.obfuscateHash(toHash, seed, obfuscationVersion, 0);
            }
            return toHash;
        }
        return null;
    }

    public void expectWinner(Object value) {
        int[] counts = tally.getMapped().getCounts();
        subject.getMapped().expectWinner(value, counts);
    }

    public boolean validateNewVotes(ModelState since, ModelState currentState, ValidationContext validationContext) {
        String votingKey = subject.getMultihash();
        IPLDObject<Voting> sinceVoting = since == null ? null : since.getVoting(votingKey);
        Map<String, IPLDObject<Vote>> newVotes = ModelUtility.getNewForeignKeyLinksMap(votes,
                sinceVoting == null ? null : sinceVoting.getMapped().votes);
        if (newVotes != null && newVotes.size() > 0) {
            long validVersion = since == null ? -1 : since.getVersion();
            Votable subject = this.subject.getMapped();
            Collection<IPLDObject<UserState>> allUserStates = initialModelState.getMapped().expectAllUserStates();
            Map<String, User> allUsers = new HashMap<>();
            for (IPLDObject<UserState> userState : allUserStates) {
                IPLDObject<User> userObject = userState.getMapped().getUser();
                String userHash = userObject.getMultihash();
                if (currentState.expectUserState(userHash).getMapped().checkRequiredRating()) {
                    allUsers.put(userHash, userObject.getMapped());
                }
            }
            Set<Entry<String, User>> entrySet = allUsers.entrySet();
            IPLDContext context = validationContext.getContext();
            if (subject.isAnonymous()) {
                outer: for (Entry<String, IPLDObject<Vote>> entry : newVotes.entrySet()) {
                    String key = entry.getKey();
                    IPLDObject<Vote> value = entry.getValue();
                    String multihash = value.getMultihash();
                    if (validationContext.addValidated(multihash + "@" + key)) {
                        validVersion = currentState.validateUnchangedVote(key, value.getMultihash(), votingKey,
                                validVersion);
                        for (Entry<String, User> userEntry : entrySet) {
                            String userHash = userEntry.getKey();
                            String invitationKey = getInvitationKey(userHash);
                            if (key.equals(invitationKey)) {
                                context.verifySignature(value, Signer.VERIFIER, userEntry.getValue());
                                continue outer;
                            }
                        }
                        throw new ValidationException("found unexpected voter - might be banned");
                    }
                }
            }
            else {
                for (Entry<String, IPLDObject<Vote>> entry : newVotes.entrySet()) {
                    String key = entry.getKey();
                    IPLDObject<Vote> value = entry.getValue();
                    String multihash = value.getMultihash();
                    if (validationContext.addValidated(multihash + "@" + key)) {
                        currentState.validateUnchangedVote(key, value.getMultihash(), votingKey, validVersion);
                        User user = allUsers.get(key);
                        context.verifySignature(value, Signer.VERIFIER, user);
                    }
                }
            }
            return true;
        }
        return false;
    }

    public void validateTally() {
        int[] expectedCounts = tally.getMapped().getCounts();
        Votable subject = this.subject.getMapped();
        int[] counts = new int[expectedCounts.length];
        if (subject.isAnonymous()) {
            byte[][] allValueHashBases = subject.getAllValueHashBases();
            for (String userHash : initialModelState.getMapped().expectAllUserHashes()) {
                String invitationKey = getInvitationKey(userHash);
                IPLDObject<Vote> voteObject = votes.get(invitationKey);
                if (voteObject != null) {
                    Vote vote = voteObject.getMapped();
                    int valueHashObfuscation = vote.getValueHashObfuscation();
                    byte[] value = (byte[]) vote.getValue();
                    int i = 0;
                    for (byte[] valueHashBase : allValueHashBases) {
                        byte[] obfuscatedHash = ModelUtility.obfuscateHash(valueHashBase, seed, obfuscationVersion,
                                valueHashObfuscation);
                        if (Arrays.equals(obfuscatedHash, value)) {
                            counts[i]++;
                        }
                        i++;
                    }
                }
            }
        }
        else {
            for (String userHash : initialModelState.getMapped().expectAllUserHashes()) {
                IPLDObject<Vote> voteObject = votes.get(userHash);
                if (voteObject != null) {
                    Vote vote = voteObject.getMapped();
                    int index = subject.getPlainTextValueIndex(vote.getValue());
                    if (index >= 0) {
                        counts[index]++;
                    }
                }
            }
        }
        if (!Arrays.equals(expectedCounts, counts)) {
            throw new ValidationException("unexpected tally counts");
        }
    }

    @Override
    public Voting getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public Voting getLoaded() {
        return this;
    }

    private String getInvitationKey(String userHash) {
        byte[] hashBytes = userHash.getBytes(StandardCharsets.UTF_8);
        byte[] invitationKeyBytes = ModelUtility.obfuscateHash(hashBytes, seed, obfuscationVersion, 0);
        return Base64.encodeBase64String(invitationKeyBytes);
    }

}
