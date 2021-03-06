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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.codec.binary.Base64;
import org.projectjinxers.account.Signer;
import org.projectjinxers.config.SecretConfig;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDObject.ProgressListener;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDReader.KeyProvider;
import org.projectjinxers.model.Votable.TieBreaker;
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
public class Voting implements IPLDSerializable, Loader<Voting>, TieBreaker {

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

    private long seed;
    private int obfuscationVersion;
    private IPLDObject<Votable> subject;
    private IPLDObject<ModelState> initialModelState;
    private Map<String, IPLDObject<Vote>> votes;
    private IPLDObject<Tally> tally;

    private long hashSeed;
    private long resolvedSeed;

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
            this.seed = (long) (Math.random() * Long.MAX_VALUE);
        }
        while (seed == 0);
        this.obfuscationVersion = obfuscationVersion;
        this.subject = subject;
        this.initialModelState = initialModelState;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.seed = reader.readNumber(KEY_SEED).longValue();
        if (validationContext != null && seed == 0) {
            throw new ValidationException("invalid seed");
        }
        this.obfuscationVersion = reader.readNumber(KEY_OBFUSCATION_VERSION).intValue();
        this.subject = reader.readLinkObject(KEY_OBFUSCATION_VERSION, context, validationContext, LoaderFactory.VOTABLE,
                eager);
        this.initialModelState = reader.readLinkObject(KEY_INITIAL_MODEL_STATE, context, null,
                LoaderFactory.MODEL_STATE, eager);
        this.votes = reader.readLinkObjects(KEY_VOTES, context, validationContext, LoaderFactory.VOTE, eager,
                subject.getMapped().isAnonymous() ? ANONYMOUS_VOTE_KEY_PROVIDER : NON_ANONYMOUS_VOTE_KEY_PROVIDER);
        this.tally = reader.readLinkObject(KEY_TALLY, context, validationContext, LoaderFactory.TALLY, eager);
        if (validationContext != null) {
            validationContext.addMustValidateModelState(initialModelState);
        }
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context, ProgressListener progressListener)
            throws IOException {
        writer.writeNumber(KEY_SEED, seed);
        writer.writeNumber(KEY_OBFUSCATION_VERSION, obfuscationVersion);
        writer.writeLink(KEY_SUBJECT, subject, null, context, progressListener);
        writer.writeLink(KEY_INITIAL_MODEL_STATE, initialModelState, null, null, null);
        writer.writeLinkObjects(KEY_VOTES, votes, signer, context, progressListener);
    }

    public int getObfuscationVersion() {
        return obfuscationVersion;
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

    public int getProgressSteps() {
        return votes != null && isAnonymous() ? votes.size() : 0;
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
    public byte[] getInvitationKey(String userHash, SecretConfig secretConfig) {
        if (initialModelState.getMapped().containsUserState(userHash)) {
            byte[] toHash = userHash.getBytes(StandardCharsets.UTF_8);
            if (isAnonymous()) {
                if (hashSeed == 0) {
                    hashSeed = subject.getMapped().getHashSeed();
                }
                return ModelUtility.obfuscateHash(toHash, hashSeed, obfuscationVersion, 0, secretConfig);
            }
            return toHash;
        }
        return null;
    }

    public Voting addVote(String userHash, int valueIndex, int valueHashObfuscation, long timestamp,
            long timestampTolerance, SecretConfig secretConfig) {
        if (tally != null || subject.getMapped().getDeadline().getTime() < timestamp + timestampTolerance) {
            return null;
        }
        byte[] invitationKey = getInvitationKey(userHash, secretConfig);
        if (invitationKey != null) {
            String check = getInvitationKeyString(invitationKey);
            if (!votes.containsKey(check)) {
                Vote vote = subject.getMapped().createVote(invitationKey, valueIndex, obfuscationVersion,
                        valueHashObfuscation, secretConfig);
                IPLDObject<Vote> voteObject = new IPLDObject<Vote>(vote);
                Voting copy = copy();
                if (copy.votes == null) {
                    copy.votes = new LinkedHashMap<>();
                }
                copy.votes.put(check, voteObject);
                return copy;
            }
        }
        return null;
    }

    public Voting tally(long timestamp, long timestampTolerance, SecretConfig secretConfig,
            ProgressListener progressListener) {
        if (tally != null && subject.getMapped().getDeadline().getTime() < timestamp + timestampTolerance) {
            int[] counts = tally(secretConfig, progressListener);
            Voting res = copy();
            res.tally = new IPLDObject<>(new Tally(counts));
            return res;
        }
        return null;
    }

    public void expectWinner(Object value) {
        int[] counts = tally.getMapped().getCounts();
        subject.getMapped().expectWinner(value, counts, this);
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
                if (hashSeed == 0) {
                    hashSeed = subject.getHashSeed();
                }
                outer: for (Entry<String, IPLDObject<Vote>> entry : newVotes.entrySet()) {
                    String key = entry.getKey();
                    IPLDObject<Vote> value = entry.getValue();
                    String multihash = value.getMultihash();
                    if (validationContext.addValidated(multihash + "@" + key)) {
                        validVersion = currentState.validateUnchangedVote(key, value.getMultihash(), votingKey,
                                validVersion);
                        for (Entry<String, User> userEntry : entrySet) {
                            String userHash = userEntry.getKey();
                            String invitationKey = getInvitationKeyString(userHash,
                                    validationContext.getSecretConfig());
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

    public void validateTally(SecretConfig secretConfig) {
        int[] expectedCounts = tally.getMapped().getCounts();
        int[] counts = tally(secretConfig, null);
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

    @Override
    public int getWinner(int sameCounts) {
        if (resolvedSeed == 0) {
            long seed = this.seed;
            for (IPLDObject<Vote> vote : votes.values()) {
                // s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]
                long hashCode = vote.getMultihash().hashCode();
                // ignore negative hash codes
                if (hashCode > 0) {
                    // java long overflow does not break the algorithm (max + max = -2)
                    seed += hashCode;
                }
            }
            // this.seed is positive, <0 can only be caused by overflow
            if (seed < 0) {
                if (seed == Long.MIN_VALUE) {
                    seed += this.seed;
                }
                seed = -seed;
            }
            resolvedSeed = seed;
        }
        // might be unfair, but we can't use Random class here, since that probably works with java only
        // probably not manipulable (users on the same team don't know what the other team does, and you can't guarantee
        // that your vote be the last one)
        long seed = resolvedSeed;
        while (seed < sameCounts) {
            seed += this.seed;
        }
        return (int) (seed % sameCounts);
    }

    private String getInvitationKeyString(String userHash, SecretConfig secretConfig) {
        byte[] hashBytes = userHash.getBytes(StandardCharsets.UTF_8);
        byte[] invitationKeyBytes = ModelUtility.obfuscateHash(hashBytes, hashSeed, obfuscationVersion, 0,
                secretConfig);
        return getInvitationKeyString(invitationKeyBytes);
    }

    private String getInvitationKeyString(byte[] invitationKey) {
        return Base64.encodeBase64String(invitationKey);
    }

    private Voting copy() {
        Voting res = new Voting();
        res.seed = seed;
        res.obfuscationVersion = obfuscationVersion;
        res.subject = subject;
        res.initialModelState = initialModelState;
        if (votes != null) {
            res.votes = new LinkedHashMap<>(votes);
        }
        res.tally = tally;
        res.hashSeed = hashSeed;
        return res;
    }

    private int[] tally(SecretConfig secretConfig, ProgressListener progressListener) {
        int[] counts = new int[votes.size()];
        Votable subject = this.subject.getMapped();
        if (subject.isAnonymous()) {
            if (hashSeed == 0) {
                hashSeed = subject.getHashSeed();
            }
            byte[][] allValueHashBases = subject.getAllValueHashBases();
            for (String userHash : initialModelState.getMapped().expectAllUserHashes()) {
                String invitationKey = getInvitationKeyString(userHash, secretConfig);
                IPLDObject<Vote> voteObject = votes.get(invitationKey);
                if (voteObject != null) {
                    Vote vote = voteObject.getMapped();
                    int valueHashObfuscation = vote.getValueHashObfuscation();
                    byte[] value = (byte[]) vote.getValue();
                    int i = 0;
                    for (byte[] valueHashBase : allValueHashBases) {
                        byte[] obfuscatedHash = ModelUtility.obfuscateHash(valueHashBase, hashSeed, obfuscationVersion,
                                valueHashObfuscation, secretConfig);
                        if (Arrays.equals(obfuscatedHash, value)) {
                            counts[i]++;
                        }
                        i++;
                    }
                }
                if (progressListener != null) {
                    progressListener.nextStep();
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
        return counts;
    }

}
