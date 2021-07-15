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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.projectjinxers.account.Signer;
import org.projectjinxers.config.SecretConfig;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDObject.ProgressListener;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDReader.KeyProvider;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.OwnershipTransferController;
import org.projectjinxers.controller.ValidationContext;
import org.projectjinxers.controller.ValidationException;
import org.projectjinxers.util.ModelUtility;

/**
 * OwnershipSelection instances can be voted for if more than one user requests ownership of an abandoned document.
 * 
 * @author ProjectJinxers
 */
public class OwnershipSelection implements Votable {

    // if changed in a running system, all affected model meta versions must be changed as well and validation must be
    // adjusted
    public static final int DURATION = 1000 * 60 * 60 * 24 * 10;

    private static final String KEY_ANONYMOUS = "a";
    private static final String KEY_HASH_SEED = "h";
    private static final String KEY_DEADLINE = "d";
    private static final String KEY_DOCUMENT = "o";
    static final String KEY_SELECTION = "s";

    private static final KeyProvider<OwnershipRequest> SELECTION_KEY_PROVIDER = new KeyProvider<>() {
    };

    private boolean anonymous;
    private long hashSeed;
    private Date deadline;
    private IPLDObject<Document> document;
    private Map<String, IPLDObject<OwnershipRequest>> selection;

    private Object[] allDisplayValues;
    private Integer[] allInternalValues;
    private byte[][] allValueHashBases;

    OwnershipSelection() {

    }

    public OwnershipSelection(IPLDObject<Document> document, Collection<IPLDObject<OwnershipRequest>> selection,
            boolean anonymous, long timestamp) {
        this.anonymous = anonymous;
        this.deadline = new Date(timestamp + DURATION);
        this.document = document;
        this.selection = new LinkedHashMap<>();
        long hashSeed = 1;
        for (IPLDObject<OwnershipRequest> request : selection) {
            this.selection.put(request.getMultihash(), request);
            hashSeed *= request.getMapped().getVotingHashSeed();
        }
        this.hashSeed = hashSeed;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.anonymous = Boolean.TRUE.equals(reader.readBoolean(KEY_ANONYMOUS));
        this.hashSeed = reader.readNumber(KEY_HASH_SEED).longValue();
        this.deadline = new Date(reader.readNumber(KEY_DEADLINE).longValue());
        this.document = reader.readLinkObject(KEY_DOCUMENT, context, validationContext, LoaderFactory.DOCUMENT, eager);
        this.selection = reader.readLinkObjects(KEY_SELECTION, context, validationContext,
                LoaderFactory.OWNERSHIP_REQUEST, eager, SELECTION_KEY_PROVIDER);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context, ProgressListener progressListener)
            throws IOException {
        writer.writeIfTrue(KEY_ANONYMOUS, anonymous);
        writer.writeNumber(KEY_HASH_SEED, hashSeed);
        writer.writeNumber(KEY_DEADLINE, deadline.getTime());
        writer.writeLink(KEY_DOCUMENT, document, null, null, null);
        writer.writeLinkObjects(KEY_SELECTION, selection, null, null, null);
    }

    @Override
    public boolean isAnonymous() {
        return anonymous;
    }

    @Override
    public long getHashSeed() {
        return hashSeed;
    }

    @Override
    public Date getDeadline() {
        return deadline;
    }

    /**
     * @return the abandoned document
     */
    public IPLDObject<Document> getDocument() {
        return document;
    }

    @Override
    public Vote createVote(byte[] invitationKey, int valueIndex, int obfuscationVersion, int valueHashObfuscation,
            SecretConfig secretConfig) {
        if (anonymous) {
            return new ValueVote(invitationKey,
                    ModelUtility.obfuscateHash(String.valueOf(valueIndex).getBytes(StandardCharsets.UTF_8), hashSeed,
                            obfuscationVersion, valueHashObfuscation, secretConfig),
                    false, valueHashObfuscation);
        }
        return new ValueVote(invitationKey, valueIndex, false, 0);
    }

    @Override
    public Object[] getAllValues(boolean forDisplay) {
        if (forDisplay) {
            if (allDisplayValues == null && selection != null) {
                allDisplayValues = new Object[selection.size()];
                int i = 0;
                for (IPLDObject<OwnershipRequest> request : selection.values()) {
                    allDisplayValues[i++] = request.getMapped().expectUser();
                }
            }
            return allDisplayValues;
        }
        if (allInternalValues == null && selection != null) {
            int length = selection.size();
            allInternalValues = new Integer[length];
            for (int i = 0; i < length; i++) {
                allInternalValues[i] = i;
            }
        }
        return allInternalValues;
    }

    @Override
    public byte[][] getAllValueHashBases() {
        if (allValueHashBases == null && selection != null) {
            int length = selection.size();
            allValueHashBases = new byte[length][];
            for (int i = 0; i < length; i++) {
                allValueHashBases[i] = String.valueOf(i).getBytes(StandardCharsets.UTF_8);
            }
        }
        return allValueHashBases;
    }

    @Override
    public int getPlainTextValueIndex(Object value) {
        return (Integer) value;
    }

    @Override
    public void expectWinner(Object value, int[] counts, TieBreaker tieBreaker) {
        int maxIndex = getMaxIndex(counts, tieBreaker);
        if (((Integer) value).intValue() != maxIndex) {
            throw new ValidationException("expected count for " + value + " to be the max");
        }
    }

    @Override
    public void validate(Voting voting, ValidationContext validationContext) {
        // TODO: can voting.initialModelState be invalid?!?
        OwnershipTransferController checkController = new OwnershipTransferController(this,
                voting.getInitialModelState());
        Voting reconstructedVoting = checkController.getVoting().getMapped();
        OwnershipSelection reconstructed = (OwnershipSelection) reconstructedVoting.getSubject().getMapped();
        if (selection.size() != reconstructed.selection.size()) {
            throw new ValidationException("expected same selection size");
        }
        Set<String> reconstructedHashes = new HashSet<>();
        for (IPLDObject<OwnershipRequest> request : reconstructed.selection.values()) {
            reconstructedHashes.add(request.getMultihash());
        }
        for (IPLDObject<OwnershipRequest> request : selection.values()) {
            if (!reconstructedHashes.contains(request.getMultihash())) {
                throw new ValidationException("expected same ownership requests");
            }
        }
    }

    public IPLDObject<OwnershipRequest> getWinner(int[] counts, TieBreaker tieBreaker) {
        int maxIndex = getMaxIndex(counts, tieBreaker);
        Iterator<Entry<String, IPLDObject<OwnershipRequest>>> iterator = selection.entrySet().iterator();
        for (int i = 0; i < maxIndex; i++) {
            iterator.next();
        }
        return iterator.next().getValue();
    }

    private int getMaxIndex(int[] counts, TieBreaker tieBreaker) {
        int max = 0;
        int maxIndex = -1;
        List<Integer> sameCount = null;
        int i = 0;
        for (int count : counts) {
            if (count > 0) {
                if (count > max) {
                    max = count;
                    maxIndex = i;
                    if (sameCount != null) {
                        sameCount.clear();
                    }
                }
                else if (count == max) {
                    if (sameCount == null) {
                        sameCount = new ArrayList<>();
                        sameCount.add(maxIndex);
                    }
                    sameCount.add(i);
                }
            }
            i++;
        }
        if (sameCount != null && sameCount.size() > 0) {
            int winner = tieBreaker.getWinner(sameCount.size());
            maxIndex = sameCount.get(winner);
        }
        return maxIndex;
    }

}
