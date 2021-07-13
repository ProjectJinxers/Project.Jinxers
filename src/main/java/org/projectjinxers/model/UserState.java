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

import static org.projectjinxers.util.ModelUtility.addProgressListeners;
import static org.projectjinxers.util.ModelUtility.isEqual;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDObject.ProgressListener;
import org.projectjinxers.controller.IPLDReader;
import org.projectjinxers.controller.IPLDReader.KeyProvider;
import org.projectjinxers.controller.IPLDWriter;
import org.projectjinxers.controller.SettlementController;
import org.projectjinxers.controller.ValidationContext;
import org.projectjinxers.controller.ValidationException;
import org.projectjinxers.util.ModelUtility;

/**
 * Instances of this class represent the state of a user (rating, documents, requests etc.) at a specific time.
 * 
 * @author ProjectJinxers
 */
public class UserState implements IPLDSerializable, Loader<UserState> {

    private static final int INITIAL_RATING = 50;
    private static final int REQUIRED_RATING = 10;
    private static final int FALSE_CLAIM_PENALTY = 20;
    private static final int FALSE_APPROVAL_PENALTY = 1;
    private static final int FALSE_DECLINATION_PENALTY = 5;
    private static final int TRUE_CLAIM_REWARD = 20;
    private static final int TRUE_APPROVAL_REWARD = 1;
    private static final int TRUE_DECLINATION_REWARD = 5;

    private static final String KEY_VERSION = "v";
    private static final String KEY_RATING = "r";
    private static final String KEY_USER = "u";
    private static final String KEY_PREVIOUS_VERSION = "p";
    private static final String KEY_DOCUMENTS = "d";
    private static final String KEY_REMOVED_DOCUMENTS = "m";
    private static final String KEY_FALSE_CLAIMS = "c";
    private static final String KEY_FALSE_APPROVALS = "a";
    private static final String KEY_FALSE_DECLINATIONS = "e";
    private static final String KEY_SETTLEMENT_REQUESTS = "s";
    private static final String KEY_OWNERSHIP_REQUESTS = "o";
    private static final String KEY_UNBAN_REQUESTS = "n";
    private static final String KEY_GRANTED_OWNERSHIPS = "g";
    private static final String KEY_GRANTED_UNBANS = "b";

    public static final KeyProvider<Document> DOCUMENT_KEY_PROVIDER = new KeyProvider<>() {

        @Override
        public String getKey(IPLDObject<Document> object) {
            String firstVersionHash = object.getMapped().getFirstVersionHash();
            return firstVersionHash == null ? object.getMultihash() : firstVersionHash;
        }

    };
    private static final KeyProvider<Review> REVIEW_KEY_PROVIDER = new KeyProvider<>() {

        @Override
        public String getKey(IPLDObject<Review> object) {
            String firstVersionHash = object.getMapped().getFirstVersionHash();
            return firstVersionHash == null ? object.getMultihash() : firstVersionHash;
        }

    };
    public static final KeyProvider<DocumentRemoval> DOCUMENT_REMOVAL_KEY_PROVIDER = new KeyProvider<>() {

        @Override
        public String getKey(IPLDObject<DocumentRemoval> object) {
            IPLDObject<Document> document = object.getMapped().getDocument();
            String firstVersionHash = document.getMapped().getFirstVersionHash();
            return firstVersionHash == null ? document.getMultihash() : firstVersionHash;
        }

    };
    public static final KeyProvider<SettlementRequest> SETTLEMENT_REQUEST_KEY_PROVIDER = new KeyProvider<>() {

        @Override
        public String getKey(IPLDObject<SettlementRequest> object) {
            return object.getMapped().getDocument().getMultihash();
        }

    };
    private static final KeyProvider<OwnershipRequest> OWNERSHIP_REQUEST_KEY_PROVIDER = new KeyProvider<>() {

        @Override
        public String getKey(IPLDObject<OwnershipRequest> object) {
            return object.getMapped().getDocument().getMultihash();
        }

    };
    public static final KeyProvider<UnbanRequest> UNBAN_REQUEST_KEY_PROVIDER = new KeyProvider<>() {

        @Override
        public String getKey(IPLDObject<UnbanRequest> object) {
            return object.getMapped().getDocument().getMultihash();
        }

    };
    private static final KeyProvider<GrantedOwnership> GRANTED_OWNERSHIP_KEY_PROVIDER = new KeyProvider<>() {

        @Override
        public String getKey(IPLDObject<GrantedOwnership> object) {
            return object.getMapped().getDocument().getMapped().getPreviousVersionHash();
        }

    };
    private static final KeyProvider<GrantedUnban> GRANTED_UNBAN_KEY_PROVIDER = new KeyProvider<>() {

        @Override
        public String getKey(IPLDObject<GrantedUnban> object) {
            return object.getMapped().getUnbanRequest().getMapped().getDocument().getMultihash();
        }

    };

    public static final KeyCollector<UserState> DOCUMENT_KEY_COLLECTOR = new KeyCollector<>() {

        @Override
        public Set<String> getHashes(UserState instance) {
            return getSplitHashes(instance);
        }

        @Override
        public Set<String> getFirstHashes(UserState instance) {
            return instance.documents == null ? null : instance.documents.keySet();
        }

        @Override
        public Set<String> getSecondHashes(UserState instance) {
            return instance.removedDocuments == null ? null : instance.removedDocuments.keySet();
        }

    };

    private static final KeyCollector<UserState> SETTLEMENT_REQUEST_KEY_COLLECTOR = new KeyCollector<>() {

        @Override
        public Set<String> getHashes(UserState instance) {
            return instance.settlementRequests == null ? null : instance.settlementRequests.keySet();
        }

    };

    public static final SplitSourceKeyCollector<UserState, ModelState> SETTLEMENT_KEY_COLLECTOR = new SplitSourceKeyCollector<>(
            SETTLEMENT_REQUEST_KEY_COLLECTOR, ModelState.SEALED_DOCUMENT_KEY_COLLECTOR);

    public static final KeyCollector<UserState> OWNERSHIP_KEY_COLLECTOR = new KeyCollector<>() {

        @Override
        public Set<String> getHashes(UserState instance) {
            return getSplitHashes(instance);
        }

        @Override
        public Set<String> getFirstHashes(UserState instance) {
            return instance.ownershipRequests == null ? null : instance.ownershipRequests.keySet();
        }

        @Override
        public Set<String> getSecondHashes(UserState instance) {
            return instance.grantedOwnerships == null ? null : instance.grantedOwnerships.keySet();
        };

    };

    public static final KeyCollector<UserState> UNBAN_KEY_COLLECTOR = new KeyCollector<>() {

        @Override
        public Set<String> getHashes(UserState instance) {
            return getSplitHashes(instance);
        }

        @Override
        public Set<String> getFirstHashes(UserState instance) {
            return instance.unbanRequests == null ? null : instance.unbanRequests.keySet();
        }

        @Override
        public Set<String> getSecondHashes(UserState instance) {
            return instance.grantedUnbans == null ? null : instance.grantedUnbans.keySet();
        }

    };

    public static IPLDObject<Document> expandDocuments(Map<String, IPLDObject<Document>> documents,
            Map<String, ?> exclude, Map<String, IPLDObject<Document>> expandInto, String requestedHash) {
        IPLDObject<Document> res = null;
        String request = requestedHash;
        if (request != null) {
            res = documents.get(requestedHash);
            if (res != null) {
                if (expandInto == null) {
                    return res;
                }
                request = null;
            }
        }
        Deque<IPLDObject<Document>> versions = expandInto == null ? null : new ArrayDeque<>();
        for (IPLDObject<Document> document : documents.values()) {
            Document doc;
            do {
                if (versions != null) {
                    versions.push(document);
                }
                doc = document.getMapped();
                if (exclude != null && exclude.containsKey(doc.getPreviousVersionHash())) {
                    break;
                }
                document = doc.getPreviousVersionObject();
                if (document == null) {
                    break;
                }
                if (request != null && request.equals(document.getMultihash())) {
                    if (expandInto == null) {
                        return document;
                    }
                    res = document;
                    request = null;
                }
                Document prev = document.getMapped();
                if (doc.getDate().equals(prev.getDate())
                        && isEqual(doc.expectUserState().getUser(), prev.expectUserState().getUser())) {
                    throw new ValidationException("date unchanged");
                }
                doc = prev;
            }
            while (true);
            if (expandInto != null) {
                for (IPLDObject<Document> version : versions) {
                    expandInto.put(version.getMultihash(), version);
                }
            }
        }
        return res;
    }

    public static Map<String, IPLDObject<Document>> expandDocuments(Map<String, IPLDObject<Document>> documents,
            Map<String, ?> exclude, Map<String, Set<String>> reviewHashes,
            Map<String, Set<String>> obsoleteReviewVersions) {
        Map<String, IPLDObject<Document>> res = new LinkedHashMap<>();
        for (IPLDObject<Document> document : documents.values()) {
            Deque<IPLDObject<Document>> versions = new ArrayDeque<>();
            Document doc;
            String documentHash = null;
            Set<String> hashes = null;
            Set<String> obsoleteHashes = null;
            boolean first = true;
            do {
                versions.push(document);
                String multihash = document.getMultihash();
                doc = document.getMapped();

                document = doc.getPreviousVersionObject();
                if (doc instanceof Review) {
                    if (documentHash == null) {
                        documentHash = ((Review) doc).getDocument().getMultihash();
                        hashes = reviewHashes.get(documentHash);
                    }
                    if (!hashes.remove(multihash) && first) {
                        throw new ValidationException("unexpected review hash");
                    }
                    if (hashes.size() == 0) {
                        reviewHashes.remove(documentHash);
                    }
                    if (document == null) {
                        break;
                    }
                    if (obsoleteHashes == null) {
                        obsoleteHashes = obsoleteReviewVersions.get(documentHash);
                        if (obsoleteHashes == null) {
                            obsoleteHashes = new TreeSet<>();
                            obsoleteReviewVersions.put(documentHash, obsoleteHashes);
                        }
                    }
                    obsoleteHashes.add(document.getMultihash());
                }
                else if (document == null) {
                    break;
                }
                Document prev = document.getMapped();
                if (doc.getDate().equals(prev.getDate())
                        && isEqual(doc.expectUserState().getUser(), prev.expectUserState().getUser())) {
                    throw new ValidationException("date unchanged");
                }
                if (exclude != null && exclude.containsKey(document.getMultihash())) {
                    break;
                }
                doc = prev;
                first = false;
            }
            while (true);
            for (IPLDObject<Document> version : versions) {
                res.put(version.getMultihash(), version);
            }
        }
        return res;
    }

    public static Map<String, IPLDObject<Document>> expandRemovedDocuments(
            Map<String, IPLDObject<DocumentRemoval>> removed) {
        Map<String, IPLDObject<Document>> res = new LinkedHashMap<>();
        for (IPLDObject<DocumentRemoval> rem : removed.values()) {
            Deque<IPLDObject<Document>> versions = new ArrayDeque<>();
            IPLDObject<Document> document = rem.getMapped().getDocument();
            do {
                versions.push(document);
                document = document.getMapped().getPreviousVersionObject();
            }
            while (document != null);
            for (IPLDObject<Document> version : versions) {
                res.put(version.getMultihash(), version);
            }
        }
        return res;
    }

    public static Map<String, IPLDObject<Document>> expandRemovedDocuments(
            Map<String, IPLDObject<DocumentRemoval>> documents, Map<String, ?> exclude,
            Map<String, Set<String>> reviewHashes, Map<String, Set<String>> obsoleteReviewVersions) {
        Map<String, IPLDObject<Document>> res = new LinkedHashMap<>();
        for (IPLDObject<DocumentRemoval> removed : documents.values()) {
            Deque<IPLDObject<Document>> versions = new ArrayDeque<>();
            Document doc;
            String documentHash = null;
            Set<String> hashes = null;
            Set<String> obsoleteHashes = null;
            IPLDObject<Document> document = removed.getMapped().getDocument();
            boolean first = true;
            do {
                versions.push(document);
                String multihash = document.getMultihash();
                doc = document.getMapped();

                document = doc.getPreviousVersionObject();
                if (doc instanceof Review) {
                    if (documentHash == null) {
                        documentHash = ((Review) doc).getDocument().getMultihash();
                        hashes = reviewHashes.get(documentHash);
                    }
                    if (!hashes.remove(multihash) && first) {
                        throw new ValidationException("unexpected review hash");
                    }
                    if (hashes.size() == 0) {
                        reviewHashes.remove(documentHash);
                    }
                    if (document == null) {
                        break;
                    }
                    if (obsoleteHashes == null) {
                        obsoleteHashes = obsoleteReviewVersions.get(documentHash);
                        if (obsoleteHashes == null) {
                            obsoleteHashes = new TreeSet<>();
                            obsoleteReviewVersions.put(documentHash, obsoleteHashes);
                        }
                    }
                    obsoleteHashes.add(document.getMultihash());
                }
                else if (document == null) {
                    break;
                }
                Document prev = document.getMapped();
                if (doc.getDate().equals(prev.getDate())
                        && isEqual(doc.expectUserState().getUser(), prev.expectUserState().getUser())) {
                    throw new ValidationException("date unchanged");
                }
                if (exclude != null && exclude.containsKey(document.getMultihash())) {
                    break;
                }
                doc = prev;
                first = false;
            }
            while (true);
            for (IPLDObject<Document> version : versions) {
                res.put(version.getMultihash(), version);
            }
        }
        return res;
    }

    private long version;
    private int rating;
    private IPLDObject<User> user;
    private IPLDObject<UserState> previousVersion;
    private Map<String, IPLDObject<Document>> documents;
    private Map<String, IPLDObject<DocumentRemoval>> removedDocuments;
    private Map<String, IPLDObject<Document>> falseClaims;
    private Map<String, IPLDObject<Review>> falseApprovals;
    private Map<String, IPLDObject<Review>> falseDeclinations;
    private Map<String, IPLDObject<SettlementRequest>> settlementRequests;
    private Map<String, IPLDObject<OwnershipRequest>> ownershipRequests;
    private Map<String, IPLDObject<UnbanRequest>> unbanRequests;
    private Map<String, IPLDObject<GrantedOwnership>> grantedOwnerships; // key at request time, value.document
                                                                         // transferred
    private Map<String, IPLDObject<GrantedUnban>> grantedUnbans;

    private Collection<IPLDObject<Document>> newDocuments;
    private Map<String, IPLDObject<DocumentRemoval>> newRemovedDocuments;
    private Collection<IPLDObject<SettlementRequest>> newSettlementRequests;
    private Collection<IPLDObject<OwnershipRequest>> newOwnershipRequests;
    private Collection<IPLDObject<UnbanRequest>> newUnbanRequests;
    private Collection<IPLDObject<GrantedOwnership>> newGrantedOwnerships;
    private Collection<IPLDObject<GrantedUnban>> newGrantedUnbans;

    private long newDocumentsSince;
    private long newRemovedDocumentsSince;
    private long newSettlementRequestsSince;
    private long newOwnershipRequestsSince;
    private long newUnbanRequestsSince;
    private long newGrantedOwnershipsSince;
    private long newGrantedUnbansSince;

    private Map<String, IPLDObject<Document>> invertedFalseClaims;
    private Map<String, IPLDObject<Review>> invertedFalseApprovals;
    private Map<String, IPLDObject<Review>> invertedFalseDeclinations;

    public UserState() {

    }

    public UserState(IPLDObject<User> user) {
        this.rating = INITIAL_RATING;
        this.user = user;
    }

    @Override
    public void read(IPLDReader reader, IPLDContext context, ValidationContext validationContext, boolean eager,
            Metadata metadata) {
        this.version = reader.readNumber(KEY_VERSION).intValue();
        this.rating = reader.readNumber(KEY_RATING).intValue();
        this.user = reader.readLinkObject(KEY_USER, context, validationContext, LoaderFactory.USER, eager);
        this.previousVersion = reader.readLinkObject(KEY_PREVIOUS_VERSION, context, null, LoaderFactory.USER_STATE,
                false);
        if (validationContext != null && previousVersion != null && previousVersion.getMapped().version >= version) {
            throw new ValidationException("version must be increased");
        }
        this.documents = reader.readLinkObjects(KEY_DOCUMENTS, context, validationContext, LoaderFactory.DOCUMENT,
                eager, DOCUMENT_KEY_PROVIDER);
        this.removedDocuments = reader.readLinkObjects(KEY_REMOVED_DOCUMENTS, context, validationContext,
                LoaderFactory.DOCUMENT_REMOVAL, eager, DOCUMENT_REMOVAL_KEY_PROVIDER);
        this.falseClaims = reader.readLinkObjects(KEY_FALSE_CLAIMS, context, validationContext, LoaderFactory.DOCUMENT,
                eager, DOCUMENT_KEY_PROVIDER);
        this.falseApprovals = reader.readLinkObjects(KEY_FALSE_APPROVALS, context, validationContext,
                LoaderFactory.REVIEW, eager, REVIEW_KEY_PROVIDER);
        this.falseDeclinations = reader.readLinkObjects(KEY_FALSE_DECLINATIONS, context, validationContext,
                LoaderFactory.REVIEW, eager, REVIEW_KEY_PROVIDER);
        this.settlementRequests = reader.readLinkObjects(KEY_SETTLEMENT_REQUESTS, context, validationContext,
                LoaderFactory.SETTLEMENT_REQUEST, eager, SETTLEMENT_REQUEST_KEY_PROVIDER);
        this.ownershipRequests = reader.readLinkObjects(KEY_OWNERSHIP_REQUESTS, context, validationContext,
                LoaderFactory.OWNERSHIP_REQUEST, eager, OWNERSHIP_REQUEST_KEY_PROVIDER);
        this.unbanRequests = reader.readLinkObjects(KEY_UNBAN_REQUESTS, context, validationContext,
                LoaderFactory.UNBAN_REQUEST, eager, UNBAN_REQUEST_KEY_PROVIDER);
        this.grantedOwnerships = reader.readLinkObjects(KEY_GRANTED_OWNERSHIPS, context, validationContext,
                LoaderFactory.GRANTED_OWNERSHIP, eager, GRANTED_OWNERSHIP_KEY_PROVIDER);
        this.grantedUnbans = reader.readLinkObjects(KEY_GRANTED_UNBANS, context, validationContext,
                LoaderFactory.GRANTED_UNBAN, eager, GRANTED_UNBAN_KEY_PROVIDER);
    }

    @Override
    public void write(IPLDWriter writer, Signer signer, IPLDContext context, ProgressListener progressListener)
            throws IOException {
        writer.writeNumber(KEY_VERSION, version);
        writer.writeNumber(KEY_RATING, rating);
        writer.writeLink(KEY_USER, user, signer, context, null);
        writer.writeLink(KEY_PREVIOUS_VERSION, previousVersion, signer, context, null);
        writer.writeLinkObjects(KEY_DOCUMENTS, documents, null, null, null);
        writer.writeLinkObjects(KEY_REMOVED_DOCUMENTS, removedDocuments, null, null, null);
        writer.writeLinkObjects(KEY_FALSE_CLAIMS, falseClaims, null, null, null);
        writer.writeLinkObjects(KEY_FALSE_APPROVALS, falseApprovals, null, null, null);
        writer.writeLinkObjects(KEY_FALSE_DECLINATIONS, falseDeclinations, null, null, null);
        writer.writeLinkObjects(KEY_SETTLEMENT_REQUESTS, settlementRequests, null, null, null);
        writer.writeLinkObjects(KEY_OWNERSHIP_REQUESTS, ownershipRequests, null, null, null);
        writer.writeLinkObjects(KEY_UNBAN_REQUESTS, unbanRequests, null, null, null);
        writer.writeLinkObjects(KEY_GRANTED_OWNERSHIPS, grantedOwnerships, signer, context, progressListener);
        writer.writeLinkObjects(KEY_GRANTED_UNBANS, grantedUnbans, signer, context, progressListener);
    }

    public long getVersion() {
        return version;
    }

    public IPLDObject<UserState> getPreviousVersion() {
        return previousVersion;
    }

    /**
     * @return the rating
     */
    public int getRating() {
        return rating;
    }

    /**
     * @return the user
     */
    public IPLDObject<User> getUser() {
        return user;
    }

    /**
     * @param firstVersionHash the hash of the first version of the document
     * @return the document wrapper stored in this instance with the given first version hash (null-safe)
     */
    public IPLDObject<Document> getDocumentByFirstVersionHash(String firstVersionHash) {
        return documents == null ? null : documents.get(firstVersionHash);
    }

    /**
     * @param documentHash the document hash
     * @return the document wrapper stored in this instance with the given hash (null-safe)
     */
    public IPLDObject<Document> getDocument(String documentHash) {
        return documents == null ? null : expandDocuments(documents, null, null, documentHash);
    }

    /**
     * @param firstVersionHash the hash of the first version of the document
     * @return the document stored in this instance with the given first version hash (no null checks!)
     */
    public Document expectDocumentByFirstVersionHash(String firstVersionHash) {
        return documents.get(firstVersionHash).getMapped();
    }

    /**
     * @param documentHash the document hash
     * @return the document stored in this instance with the given hash (no null checks!)
     */
    public IPLDObject<Document> expectDocumentObject(String documentHash) {
        return expandDocuments(documents, null, null, documentHash);
    }

    /**
     * @param documentHash the document hash
     * @return the document stored in this instance with the given hash (no null checks!)
     */
    public Document expectDocument(String documentHash) {
        return expandDocuments(documents, null, null, documentHash).getMapped();
    }

    public Map<String, IPLDObject<Document>> getAllDocuments() {
        if (documents == null) {
            return null;
        }
        Map<String, IPLDObject<Document>> res = new LinkedHashMap<>();
        expandDocuments(documents, null, res, null);
        return res;
    }

    /**
     * Checks if the user has posted a non-negative review, that has not been followed by a negative review, for the
     * document with the given hash. Neutral reviews are currently included.
     * 
     * @param documentHash the document hash
     * @return true iff the user has posted a non-negative review and there is no negative review afterwards
     */
    public boolean checkNonNegativeReview(String documentHash) {
        return checkReview(documentHash, Boolean.FALSE);
    }

    /**
     * Checks if the user has posted a non-positive review, that has not been followed by a positive review, for the
     * document with the given hash. Neutral reviews are currently included.
     * 
     * @param documentHash the document hash
     * @return true iff the user has posted a non-positive review and there is no positive review afterwards
     */
    public boolean checkNonPositiveReview(String documentHash) {
        return checkReview(documentHash, Boolean.TRUE);
    }

    private boolean checkReview(String documentHash, Boolean disallowedApproveValue) {
        if (documents != null) {
            for (IPLDObject<Document> document : documents.values()) {
                Document doc = document.getMapped();
                if (doc instanceof Review) {
                    Review review = (Review) doc;
                    if (documentHash.equals(review.getDocument().getMultihash())) {
                        Boolean approve = review.getApprove();
                        if (disallowedApproveValue.equals(approve)) {
                            return false;
                        }
                        else { // neutral reviews allowed - currently, might change
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public void validateRelatedDocument(String firstDocumentVersionHash, String[] reviewHashes) {
        if (documents != null) {
            if (documents.containsKey(firstDocumentVersionHash)) {
                return;
            }
            if (reviewHashes != null) {
                Map<String, IPLDObject<Document>> allDocuments = null;
                for (String reviewHash : reviewHashes) {
                    if (allDocuments == null) {
                        allDocuments = new HashMap<>();
                        if (expandDocuments(documents, null, allDocuments, reviewHash) != null) {
                            return;
                        }
                    }
                    else if (allDocuments.containsKey(reviewHash)) {
                        return;
                    }
                }
            }
        }
        throw new ValidationException("unrelated document");
    }

    /**
     * Finds related documents and returns the date of the most recent one or null, if there is none (in which case the
     * date of the document with the given hash can be assumed as the last activity date). TODO: use reviewTable (model
     * state) and expandDocuments without expandInto
     * 
     * @param documentHash the document hash
     * @return the last activity date or null, if the document itself is the last activity
     */
    public Date getLastActivityDate(String documentHash) {
        Set<String> relatedHashes = new HashSet<>();
        Set<String> unrelatedHashes = new HashSet<>();
        relatedHashes.add(documentHash);
        Document lastActivity = null;
        Map<String, IPLDObject<Document>> allDocuments = new LinkedHashMap<>();
        expandDocuments(documents, null, allDocuments, null);
        main: for (Entry<String, IPLDObject<Document>> entry : allDocuments.entrySet()) {
            String docHash = entry.getKey();
            IPLDObject<Document> document = entry.getValue();
            Document doc = document.getMapped();
            String hash = doc.getPreviousVersionHash();
            if (hash != null) {
                if (relatedHashes.contains(hash)) {
                    relatedHashes.add(docHash);
                    lastActivity = doc;
                    continue;
                }
                unrelatedHashes.add(docHash);
            }
            else if (doc instanceof Review) {
                Review review = (Review) doc;
                do {
                    IPLDObject<Document> reviewed = review.getDocument();
                    String reviewedHash = reviewed.getMultihash();
                    if (unrelatedHashes.contains(reviewedHash)) {
                        unrelatedHashes.add(docHash);
                        continue main;
                    }
                    if (relatedHashes.contains(reviewedHash)) {
                        relatedHashes.add(docHash);
                        lastActivity = doc;
                        continue main;
                    }
                    Document reviewedDoc = reviewed.getMapped();
                    if (reviewedDoc instanceof Review) {
                        review = (Review) reviewedDoc;
                    }
                    else {
                        break;
                    }
                }
                while (true);
                unrelatedHashes.add(docHash);
            }
        }
        return lastActivity == null ? null : lastActivity.getDate();
    }

    /**
     * @param documentHash the document hash
     * @return the ownership request for the document with the given hash (null-safe)
     */
    public IPLDObject<OwnershipRequest> getOwnershipRequest(String documentHash) {
        return ownershipRequests == null ? null : ownershipRequests.get(documentHash);
    }

    public UnbanRequest getUnbanRequest(String documentHash) {
        IPLDObject<UnbanRequest> req = unbanRequests == null ? null : unbanRequests.get(documentHash);
        return req == null ? null : req.getMapped();
    }

    public IPLDObject<UnbanRequest> expectUnbanRequest(String documentHash) {
        return unbanRequests.get(documentHash);
    }

    /**
     * @param documentHash the document hash (prior to transfer)
     * @return the granted ownership for the document with the given hash (null-safe)
     */
    public IPLDObject<GrantedOwnership> getGrantedOwnership(String documentHash) {
        return grantedOwnerships == null ? null : grantedOwnerships.get(documentHash);
    }

    public IPLDObject<GrantedUnban> getGrantedUnban(String documentHash) {
        return grantedUnbans == null ? null : grantedUnbans.get(documentHash);
    }

    /**
     * Creates a copy of this instance, updates and returns it. The given current version is set as its previousVersion.
     * 
     * @param docs                       the documents to add
     * @param oreqs                      the ownership request to add
     * @param granted                    the granted ownerships to add
     * @param transferredOwnershipHashes the hashes of documents that have been transferred to other users
     * @param current                    the current wrapper (should not be null)
     */
    public UserState updateLinks(Map<String, IPLDObject<Document>> docs,
            Map<String, IPLDObject<DocumentRemoval>> removals, Map<String, IPLDObject<SettlementRequest>> sreqs,
            Collection<IPLDObject<OwnershipRequest>> oreqs, Map<String, IPLDObject<GrantedOwnership>> granted,
            Collection<String> transferredOwnershipHashes, Collection<String> sealedDocuments,
            UserState settlementValues, Map<String, IPLDObject<UnbanRequest>> unbanRequests,
            Collection<IPLDObject<GrantedUnban>> unbans, IPLDObject<UserState> current,
            Collection<ProgressListener> progressListeners) {
        UserState updated = copy();
        updated.version = this.version + 1;
        updated.previousVersion = current;
        if (transferredOwnershipHashes != null) {
            for (String hash : transferredOwnershipHashes) {
                updated.documents.remove(hash);
            }
        }
        if (sreqs != null) {
            updated.settlementRequests = addProgressListeners(sreqs, updated.settlementRequests, null,
                    progressListeners);
        }
        if (oreqs != null) {
            if (updated.ownershipRequests == null) {
                updated.ownershipRequests = new LinkedHashMap<>();
            }
            for (IPLDObject<OwnershipRequest> ownershipRequest : oreqs) {
                updated.ownershipRequests.put(OWNERSHIP_REQUEST_KEY_PROVIDER.getKey(ownershipRequest),
                        ownershipRequest);
            }
        }
        if (granted != null) {
            if (updated.grantedOwnerships == null) {
                updated.grantedOwnerships = new LinkedHashMap<>();
            }
            for (IPLDObject<GrantedOwnership> grantedOwnership : granted.values()) {
                String key = GRANTED_OWNERSHIP_KEY_PROVIDER.getKey(grantedOwnership);
                updated.grantedOwnerships.put(key, grantedOwnership);
                if (updated.ownershipRequests != null) { // might be OP reclaim
                    updated.ownershipRequests.remove(key);
                }
            }
        }
        if (docs != null) {
            updated.documents = addProgressListeners(docs, updated.documents, null, progressListeners);
        }
        if (removals != null) {
            updated.removedDocuments = addProgressListeners(removals, updated.removedDocuments, updated.documents,
                    progressListeners);
        }
        if (sealedDocuments != null) {
            if (updated.settlementRequests != null) { // null happens when sealed document is not an original
                for (String hash : sealedDocuments) {
                    updated.settlementRequests.remove(hash);
                }
            }
            if (settlementValues != null) {
                updated.applySettlement(settlementValues);
            }
        }
        if (unbanRequests != null) {
            updated.unbanRequests = addProgressListeners(unbanRequests, updated.unbanRequests, null, progressListeners);
        }
        if (unbans != null) {
            if (updated.grantedUnbans == null) {
                updated.grantedUnbans = new LinkedHashMap<>();
            }
            for (IPLDObject<GrantedUnban> unban : unbans) {
                String key = GRANTED_UNBAN_KEY_PROVIDER.getKey(unban);
                updated.grantedUnbans.put(key, unban);
                updated.unbanRequests.remove(key);
            }
        }
        return updated;
    }

    public void addFalseClaim(IPLDObject<Document> falseClaim) {
        if (falseClaims == null) {
            falseClaims = new LinkedHashMap<>();
        }
        falseClaims.put(falseClaim.getMultihash(), falseClaim);
        rating -= FALSE_CLAIM_PENALTY;
    }

    public void addFalseApproval(IPLDObject<Review> falseApproval) {
        if (falseApprovals == null) {
            falseApprovals = new LinkedHashMap<>();
        }
        falseApprovals.put(falseApproval.getMultihash(), falseApproval);
        rating -= FALSE_APPROVAL_PENALTY;
    }

    public void addFalseDeclination(IPLDObject<Review> falseDeclination) {
        if (falseDeclinations == null) {
            falseDeclinations = new LinkedHashMap<>();
        }
        falseDeclinations.put(falseDeclination.getMultihash(), falseDeclination);
        rating -= FALSE_DECLINATION_PENALTY;
    }

    public void handleTrueClaim() {
        rating += TRUE_CLAIM_REWARD;
    }

    public void handleTrueApproval() {
        rating += TRUE_APPROVAL_REWARD;
    }

    public void handleTrueDeclination() {
        rating += TRUE_DECLINATION_REWARD;
    }

    public void removeFalseClaim(String documentHash) {
        rating += FALSE_CLAIM_PENALTY;
        falseClaims.remove(documentHash);
        rating += TRUE_CLAIM_REWARD;
    }

    public void removeFalseApproval(String documentHash) {
        rating += FALSE_APPROVAL_PENALTY;
        falseApprovals.remove(documentHash);
        rating += TRUE_APPROVAL_REWARD;
    }

    public void removeFalseDeclination(String documentHash) {
        rating += FALSE_DECLINATION_PENALTY;
        falseDeclinations.remove(documentHash);
        rating += TRUE_DECLINATION_REWARD;
    }

    public void removeFalseClaim(IPLDObject<Document> document) {
        rating += FALSE_CLAIM_PENALTY;
        String documentHash = document.getMultihash();
        if (falseClaims != null) { // false... maps are null if this is a new temp instance for truth inversion
            falseClaims.remove(documentHash);
        }
        if (invertedFalseClaims == null) {
            invertedFalseClaims = new HashMap<>();
        }
        invertedFalseClaims.put(documentHash, document);
        rating += TRUE_CLAIM_REWARD;
    }

    public void removeFalseApproval(IPLDObject<Review> review) {
        rating += FALSE_APPROVAL_PENALTY;
        String documentHash = review.getMultihash();
        if (falseApprovals != null) {
            falseApprovals.remove(documentHash);
        }
        if (invertedFalseApprovals == null) {
            invertedFalseApprovals = new HashMap<>();
        }
        invertedFalseApprovals.put(documentHash, review);
        rating += TRUE_APPROVAL_REWARD;
    }

    public void removeFalseDeclination(IPLDObject<Review> review) {
        rating += FALSE_DECLINATION_PENALTY;
        String documentHash = review.getMultihash();
        if (falseDeclinations != null) {
            falseDeclinations.remove(documentHash);
        }
        if (invertedFalseDeclinations == null) {
            invertedFalseDeclinations = new HashMap<>();
        }
        invertedFalseDeclinations.put(documentHash, review);
        rating += TRUE_DECLINATION_REWARD;
    }

    public void removeTrueClaim(IPLDObject<Document> falseClaim) {
        rating -= TRUE_CLAIM_REWARD;
        if (invertedFalseClaims != null) {
            invertedFalseClaims.remove(falseClaim.getMultihash());
        }
        addFalseClaim(falseClaim);
    }

    public void removeTrueApproval(IPLDObject<Review> falseApproval) {
        rating -= TRUE_APPROVAL_REWARD;
        if (invertedFalseApprovals != null) {
            invertedFalseApprovals.remove(falseApproval.getMultihash());
        }
        addFalseApproval(falseApproval);
    }

    public void removeTrueDeclination(IPLDObject<Review> falseDeclination) {
        rating -= TRUE_DECLINATION_REWARD;
        if (invertedFalseDeclinations != null) {
            invertedFalseDeclinations.remove(falseDeclination.getMultihash());
        }
        addFalseDeclination(falseDeclination);
    }

    public Collection<IPLDObject<Document>> getNewDocuments(UserState since, boolean ignoreCached) {
        if (ignoreCached || newDocuments == null || since == null && newDocumentsSince >= 0
                || since != null && since.getVersion() != newDocumentsSince) {
            if (since == null || since.documents == null) {
                if (documents == null) {
                    newDocuments = null;
                }
                else {
                    Map<String, IPLDObject<Document>> tmp = new LinkedHashMap<>();
                    expandDocuments(documents, null, tmp, null);
                    newDocuments = tmp.values();
                }
            }
            else {
                Map<String, IPLDObject<Document>> newLinksMap = ModelUtility.getNewForeignKeyLinksMap(documents,
                        since.documents);
                if (newLinksMap == null) {
                    newDocuments = null;
                }
                else {
                    Map<String, IPLDObject<Document>> tmp = new LinkedHashMap<>();
                    expandDocuments(newLinksMap, since.documents, tmp, null);
                    newDocuments = tmp.values();
                }
            }
            newDocumentsSince = since == null ? -1 : since.getVersion();
        }
        return newDocuments;
    }

    public Collection<IPLDObject<Document>> getNewDocuments(UserState since, Map<String, Set<String>> reviewHashes,
            Map<String, Set<String>> obsoleteReviewVersions) {
        if (since == null || since.documents == null) {
            if (documents == null) {
                newDocuments = null;
            }
            else {
                newDocuments = expandDocuments(documents, null, reviewHashes, obsoleteReviewVersions).values();
            }
        }
        else {
            Map<String, IPLDObject<Document>> newLinksMap = ModelUtility.getNewForeignKeyLinksMap(documents,
                    since.documents);
            if (newLinksMap == null) {
                newDocuments = null;
            }
            else {
                Map<String, IPLDObject<Document>> allCurrentDocuments = new HashMap<>();
                expandDocuments(since.documents, null, allCurrentDocuments, null);
                newDocuments = expandDocuments(newLinksMap, allCurrentDocuments, reviewHashes, obsoleteReviewVersions)
                        .values();
            }
        }
        newDocumentsSince = since == null ? -1 : since.getVersion();
        return newDocuments;
    }

    public Map<String, IPLDObject<DocumentRemoval>> getNewRemovedDocuments(UserState since, boolean ignoreCached) {
        if (ignoreCached || newRemovedDocuments == null || since == null && newRemovedDocumentsSince >= 0
                || since != null && since.getVersion() != newRemovedDocumentsSince) {
            if (since == null) {
                newRemovedDocuments = ModelUtility.getNewForeignKeyLinksMap(removedDocuments, null);
                newRemovedDocumentsSince = -1;
            }
            else {
                newRemovedDocuments = ModelUtility.getNewForeignKeyLinksMap(removedDocuments, since.removedDocuments);
                newRemovedDocumentsSince = since.getVersion();
            }
        }
        return newRemovedDocuments;
    }

    public Map<String, IPLDObject<DocumentRemoval>> getNewRemovedDocuments(UserState since,
            Map<String, Set<String>> reviewHashes, Map<String, Set<String>> obsoleteReviewVersions) {
        newRemovedDocuments = ModelUtility.getNewForeignKeyLinksMap(removedDocuments,
                since == null ? null : since.removedDocuments);
        if (newRemovedDocuments != null) {
            expandRemovedDocuments(newRemovedDocuments, since == null ? null : since.removedDocuments, reviewHashes,
                    obsoleteReviewVersions);
        }
        newRemovedDocumentsSince = since == null ? -1 : since.getVersion();
        return newRemovedDocuments;
    }

    public Collection<IPLDObject<SettlementRequest>> getNewSettlementRequests(UserState since, boolean ignoreCached) {
        if (ignoreCached || newSettlementRequests == null || since == null && newSettlementRequestsSince >= 0
                || since != null && since.getVersion() != newSettlementRequestsSince) {
            if (since == null) {
                newSettlementRequests = ModelUtility.getNewForeignKeyLinks(settlementRequests, null);
                newSettlementRequestsSince = -1;
            }
            else {
                newSettlementRequests = ModelUtility.getNewForeignKeyLinks(settlementRequests,
                        since.settlementRequests);
                newSettlementRequestsSince = since.getVersion();
            }
        }
        return newSettlementRequests;
    }

    public Collection<IPLDObject<OwnershipRequest>> getNewOwnershipRequests(UserState since, boolean ignoreCached) {
        if (ignoreCached || newOwnershipRequests == null || since == null && newOwnershipRequestsSince >= 0
                || since != null && since.getVersion() != newOwnershipRequestsSince) {
            if (since == null) {
                newOwnershipRequests = ModelUtility.getNewForeignKeyLinks(ownershipRequests, null);
                newOwnershipRequestsSince = -1;
            }
            else {
                newOwnershipRequests = ModelUtility.getNewForeignKeyLinks(ownershipRequests, since.ownershipRequests);
                newOwnershipRequestsSince = since.getVersion();
            }
        }
        return newOwnershipRequests;
    }

    public Collection<IPLDObject<UnbanRequest>> getNewUnbanRequests(UserState since, boolean ignoreCached) {
        if (ignoreCached || newUnbanRequests == null || since == null && newUnbanRequestsSince >= 0
                || since != null && since.getVersion() != newUnbanRequestsSince) {
            if (since == null) {
                newUnbanRequests = ModelUtility.getNewForeignKeyLinks(unbanRequests, null);
                newUnbanRequestsSince = -1;
            }
            else {
                newUnbanRequests = ModelUtility.getNewForeignKeyLinks(unbanRequests, since.unbanRequests);
                newUnbanRequestsSince = since.getVersion();
            }
        }
        return newUnbanRequests;
    }

    public Collection<IPLDObject<GrantedOwnership>> getNewGrantedOwnerships(UserState since, boolean ignoreCached) {
        if (ignoreCached || newGrantedOwnerships == null || since == null && newGrantedOwnershipsSince >= 0
                || since != null && since.getVersion() != newGrantedOwnershipsSince) {
            if (since == null) {
                newGrantedOwnerships = ModelUtility.getNewForeignKeyLinks(grantedOwnerships, null);
                newGrantedOwnershipsSince = -1;
            }
            else {
                newGrantedOwnerships = ModelUtility.getNewForeignKeyLinks(grantedOwnerships, since.grantedOwnerships);
                newGrantedOwnershipsSince = since.getVersion();
            }
        }
        return newGrantedOwnerships;
    }

    public Collection<IPLDObject<GrantedUnban>> getNewGrantedUnbans(UserState since, boolean ignoreCached) {
        if (ignoreCached || newGrantedUnbans == null || since == null && newGrantedUnbansSince >= 0
                || since != null && since.getVersion() != newGrantedUnbansSince) {
            if (since == null) {
                newGrantedUnbans = ModelUtility.getNewForeignKeyLinks(grantedUnbans, null);
                newGrantedUnbansSince = -1;
            }
            else {
                newGrantedUnbans = ModelUtility.getNewForeignKeyLinks(grantedUnbans, since.grantedUnbans);
                newGrantedUnbansSince = since.getVersion();
            }
        }
        return newGrantedUnbans;
    }

    boolean prepareSettlementValidation(SettlementController controller, Set<String> newReviewTableKeys,
            Map<String, Set<String>> newReviewTableValues, Map<String, Map<String, String>> reviewers) {
        if (documents != null) {
            boolean res = false;
            Map<String, IPLDObject<Document>> allDocuments = new HashMap<>();
            expandDocuments(documents, null, allDocuments, null);
            for (IPLDObject<Document> document : allDocuments.values()) {
                res = controller.checkDocument(document, true, newReviewTableKeys, newReviewTableValues, reviewers)
                        || res;
            }
            if (res) {
                return true;
            }
        }
        return controller.checkUser(user.getMultihash());
    }

    UserState mergeWith(IPLDObject<UserState> otherObject, ValidationContext validationContext) {
        UserState other = otherObject.getMapped();
        UserState res = new UserState(user);
        if (this.documents == null) {
            res.documents = other.documents;
        }
        else {
            res.documents = new LinkedHashMap<>(documents);
            Collection<IPLDObject<Document>> newDocuments = other.newDocuments;
            if (newDocuments != null) {
                for (IPLDObject<Document> document : newDocuments) {
                    String hash = document.getMapped().getFirstVersionHash();
                    if (hash == null) {
                        hash = document.getMultihash();
                    }
                    if (removedDocuments == null || !removedDocuments.containsKey(hash)) {
                        res.documents.put(hash, document);
                    }
                }
            }
        }
        Map<String, IPLDObject<DocumentRemoval>> newRemovedDocuments = other.newRemovedDocuments;
        if (this.removedDocuments == null) {
            res.removedDocuments = other.removedDocuments;
            if (newRemovedDocuments != null) {
                for (String key : newRemovedDocuments.keySet()) {
                    res.documents.remove(key);
                }
            }
        }
        else {
            res.removedDocuments = new LinkedHashMap<>(removedDocuments);
            if (newRemovedDocuments != null) {
                for (Entry<String, IPLDObject<DocumentRemoval>> entry : newRemovedDocuments.entrySet()) {
                    String hash = entry.getKey();
                    res.removedDocuments.put(hash, entry.getValue());
                    res.documents.remove(hash);
                }
            }
        }
        if (this.settlementRequests == null) {
            res.settlementRequests = other.settlementRequests;
        }
        else {
            res.settlementRequests = new LinkedHashMap<>(settlementRequests);
            Collection<IPLDObject<SettlementRequest>> newSettlementRequests = other.newSettlementRequests;
            if (newSettlementRequests != null) {
                for (IPLDObject<SettlementRequest> request : newSettlementRequests) {
                    res.settlementRequests.put(SETTLEMENT_REQUEST_KEY_PROVIDER.getKey(request), request);
                }
            }
        }
        if (this.ownershipRequests == null) {
            res.ownershipRequests = other.ownershipRequests;
        }
        else {
            res.ownershipRequests = new LinkedHashMap<>(ownershipRequests);
            Collection<IPLDObject<OwnershipRequest>> newOwnershipRequests = other.newOwnershipRequests;
            if (newOwnershipRequests != null) {
                for (IPLDObject<OwnershipRequest> request : newOwnershipRequests) {
                    res.ownershipRequests.put(OWNERSHIP_REQUEST_KEY_PROVIDER.getKey(request), request);
                }
            }
        }
        if (this.unbanRequests == null) {
            res.unbanRequests = other.unbanRequests;
        }
        else {
            res.unbanRequests = new LinkedHashMap<>(unbanRequests);
            Collection<IPLDObject<UnbanRequest>> newUnbanRequests = other.newUnbanRequests;
            if (newUnbanRequests != null) {
                for (IPLDObject<UnbanRequest> request : newUnbanRequests) {
                    res.unbanRequests.put(UNBAN_REQUEST_KEY_PROVIDER.getKey(request), request);
                }
            }
        }
        if (this.grantedOwnerships == null) {
            res.grantedOwnerships = other.grantedOwnerships;
        }
        else {
            res.grantedOwnerships = new LinkedHashMap<>(grantedOwnerships);
            Collection<IPLDObject<GrantedOwnership>> newGrantedOwnerships = other.newGrantedOwnerships;
            if (newGrantedOwnerships != null) {
                for (IPLDObject<GrantedOwnership> granted : newGrantedOwnerships) {
                    String key = GRANTED_OWNERSHIP_KEY_PROVIDER.getKey(granted);
                    res.grantedOwnerships.put(key, granted);
                    res.ownershipRequests.remove(key);
                }
            }
        }
        if (this.grantedUnbans == null) {
            res.grantedUnbans = other.grantedUnbans;
        }
        else {
            res.grantedUnbans = new LinkedHashMap<>(grantedUnbans);
            Collection<IPLDObject<GrantedUnban>> newGrantedUnbans = other.newGrantedUnbans;
            if (newGrantedUnbans != null) {
                for (IPLDObject<GrantedUnban> granted : newGrantedUnbans) {
                    String key = GRANTED_UNBAN_KEY_PROVIDER.getKey(granted);
                    res.grantedUnbans.put(key, granted);
                    res.unbanRequests.remove(key);
                }
            }
        }
        UserState previous;
        IPLDObject<UserState> previousUserState = validationContext.getPreviousUserState(otherObject.getMultihash());
        if (previousUserState == null) {
            previous = null;
        }
        else {
            previous = previousUserState.getMapped();
            res.rating = previous.rating;
            res.previousVersion = previousUserState;
            if (previous.falseClaims != null) {
                res.falseClaims = new LinkedHashMap<>(previous.falseClaims);
            }
            if (previous.falseApprovals != null) {
                res.falseApprovals = new LinkedHashMap<>(previous.falseApprovals);
            }
            if (previous.falseDeclinations != null) {
                res.falseDeclinations = new LinkedHashMap<>(previous.falseDeclinations);
            }
            Collection<IPLDObject<GrantedUnban>> newGrantedUnbans = getNewGrantedUnbans(previous, true);
            if (newGrantedUnbans != null) {
                SettlementController mainSettlementController = validationContext.getMainSettlementController();
                for (IPLDObject<GrantedUnban> request : newGrantedUnbans) {
                    mainSettlementController.checkGrantedUnban(request.getMapped(), user);
                }
            }
        }
        return res;
    }

    boolean checkSettlementDocuments(ValidationContext validationContext) {
        SettlementController mainSettlementController = validationContext.getMainSettlementController();
        if (documents != null) {
            boolean res = false;
            Map<String, IPLDObject<Document>> allDocuments = new HashMap<>();
            expandDocuments(documents, null, allDocuments, null);
            for (IPLDObject<Document> document : allDocuments.values()) {
                res = mainSettlementController.checkDocument(document, true, null, null, null) || res;
            }
            if (res) {
                return true;
            }
        }
        return mainSettlementController.checkUser(user.getMultihash());
    }

    void checkSettlementDocuments(UserState previous, ModelState current, ValidationContext validationContext) {
        SettlementController mainSettlementController = validationContext.getMainSettlementController();
        Map<String, IPLDObject<DocumentRemoval>> newRemovedDocs = getNewRemovedDocuments(previous, true);
        if (newRemovedDocs != null) {
            this.newRemovedDocuments = null;
            for (Entry<String, IPLDObject<Document>> entry : expandRemovedDocuments(newRemovedDocs).entrySet()) {
                mainSettlementController.checkRemovedDocument(entry.getValue(), current);
            }
        }
        Collection<IPLDObject<Document>> newDocuments = getNewDocuments(previous, true);
        if (newDocuments != null) {
            this.newDocuments = null;
            for (IPLDObject<Document> document : newDocuments) {
                mainSettlementController.checkDocument(document, true, null, null, null);
            }
        }
    }

    public UserState settlementCopy() {
        UserState copy = new UserState();
        copy.rating = this.rating;
        if (falseClaims != null) {
            copy.falseClaims = new LinkedHashMap<>(falseClaims);
        }
        if (falseApprovals != null) {
            copy.falseApprovals = new LinkedHashMap<>(falseApprovals);
        }
        if (falseDeclinations != null) {
            copy.falseDeclinations = new LinkedHashMap<>(falseDeclinations);
        }
        return copy;
    }

    public boolean checkRequiredRating() {
        return rating >= REQUIRED_RATING;
    }

    public void validateRequiredRating() {
        if (rating < REQUIRED_RATING) {
            throw new ValidationException("user had been banned");
        }
    }

    public void validateUnbanRequest(String documentHash) {
        if (rating >= REQUIRED_RATING) {
            throw new ValidationException("user had not been banned");
        }
        if ((falseClaims == null || !falseClaims.containsKey(documentHash))
                && (falseApprovals == null || !falseApprovals.containsKey(documentHash))
                && (falseDeclinations == null || !falseDeclinations.containsKey(documentHash))) {
            throw new ValidationException("user had not been banned for the document");
        }
    }

    public void validateSettlement(UserState expectedValues) {
        if (rating != expectedValues.rating) {
            throw new ValidationException("unexpected rating");
        }
        validateFalseMap(expectedValues.falseClaims, falseClaims);
        validateFalseMap(expectedValues.falseApprovals, falseApprovals);
        validateFalseMap(expectedValues.falseDeclinations, falseDeclinations);
    }

    public void applySettlement(UserState settlementValues) {
        this.rating += settlementValues.rating;
        if (settlementValues.invertedFalseClaims != null) {
            for (String invertedFalseClaim : settlementValues.invertedFalseClaims.keySet()) {
                falseClaims.remove(invertedFalseClaim);
            }
        }
        if (settlementValues.falseClaims != null) {
            if (falseClaims == null) {
                falseClaims = new LinkedHashMap<>();
            }
            falseClaims.putAll(settlementValues.falseClaims);
        }
        if (settlementValues.invertedFalseApprovals != null) {
            for (String invertedFalseApproval : settlementValues.invertedFalseApprovals.keySet()) {
                falseApprovals.remove(invertedFalseApproval);
            }
        }
        if (settlementValues.falseApprovals != null) {
            if (falseApprovals == null) {
                falseApprovals = new LinkedHashMap<>();
            }
            falseApprovals.putAll(settlementValues.falseApprovals);
        }
        if (settlementValues.invertedFalseDeclinations != null) {
            for (String invertedFalseDeclination : settlementValues.invertedFalseDeclinations.keySet()) {
                falseDeclinations.remove(invertedFalseDeclination);
            }
        }
        if (settlementValues.falseDeclinations != null) {
            if (falseDeclinations == null) {
                falseDeclinations = new LinkedHashMap<>();
            }
            falseDeclinations.putAll(settlementValues.falseDeclinations);
        }
    }

    public void revertSettlement(UserState settlementValues) {
        this.rating -= settlementValues.rating;
        if (settlementValues.invertedFalseClaims != null) {
            falseClaims.putAll(settlementValues.invertedFalseClaims);
        }
        if (settlementValues.falseClaims != null) {
            for (String falseClaim : settlementValues.falseClaims.keySet()) {
                falseClaims.remove(falseClaim);
            }
        }
        if (settlementValues.invertedFalseApprovals != null) {
            falseApprovals.putAll(settlementValues.invertedFalseApprovals);
        }
        if (settlementValues.falseApprovals != null) {
            for (String falseApproval : settlementValues.falseApprovals.keySet()) {
                falseApprovals.remove(falseApproval);
            }
        }
        if (settlementValues.invertedFalseDeclinations != null) {
            falseDeclinations.putAll(settlementValues.invertedFalseDeclinations);
        }
        if (settlementValues.falseDeclinations != null) {
            for (String falseDeclination : settlementValues.falseDeclinations.keySet()) {
                falseDeclinations.remove(falseDeclination);
            }
        }
    }

    private <D extends IPLDSerializable> void validateFalseMap(Map<String, IPLDObject<D>> expected,
            Map<String, IPLDObject<D>> actual) {
        if (expected == null) {
            if (actual != null) {
                throw new ValidationException("unexpected present false map");
            }
        }
        else if (actual == null) {
            if (expected.size() > 0) {
                throw new ValidationException("unexpected missing false map");
            }
        }
        else if (expected.size() != actual.size()) {
            throw new ValidationException("unexpected size of false map");
        }
        else {
            for (String key : expected.keySet()) {
                if (!actual.containsKey(key)) {
                    throw new ValidationException("unexpected missing false map entry");
                }
            }
        }
    }

    private UserState copy() {
        UserState copy = new UserState();
        copy.version = version;
        copy.rating = rating;
        copy.user = user;
        copy.previousVersion = previousVersion;
        if (documents != null) {
            copy.documents = new LinkedHashMap<>(documents);
        }
        if (removedDocuments != null) {
            copy.removedDocuments = new LinkedHashMap<>(removedDocuments);
        }
        if (falseClaims != null) {
            copy.falseClaims = new LinkedHashMap<>(falseClaims);
        }
        if (falseApprovals != null) {
            copy.falseApprovals = new LinkedHashMap<>(falseApprovals);
        }
        if (falseDeclinations != null) {
            copy.falseDeclinations = new LinkedHashMap<>(falseDeclinations);
        }
        if (settlementRequests != null) {
            copy.settlementRequests = new LinkedHashMap<>(settlementRequests);
        }
        if (ownershipRequests != null) {
            copy.ownershipRequests = new LinkedHashMap<>(ownershipRequests);
        }
        if (unbanRequests != null) {
            copy.unbanRequests = new LinkedHashMap<>(unbanRequests);
        }
        if (grantedOwnerships != null) {
            copy.grantedOwnerships = new LinkedHashMap<>(grantedOwnerships);
        }
        if (grantedUnbans != null) {
            copy.grantedUnbans = new LinkedHashMap<>(grantedUnbans);
        }
        return copy;
    }

    @Override
    public UserState getOrCreateDataInstance(IPLDReader reader, Metadata metadata) {
        return this;
    }

    @Override
    public UserState getLoaded() {
        return this;
    }

}
