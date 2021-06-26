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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
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

    private static final KeyProvider<Document> DOCUMENT_KEY_PROVIDER = new KeyProvider<>() {
    };
    private static final KeyProvider<Review> REVIEW_KEY_PROVIDER = new KeyProvider<>() {
    };
    private static final KeyProvider<DocumentRemoval> DOCUMENT_REMOVAL_KEY_PROVIDER = new KeyProvider<>() {
        public String getKey(IPLDObject<DocumentRemoval> object) {
            return object.getMapped().getDocument().getMultihash();
        }
    };
    static final KeyProvider<SettlementRequest> SETTLEMENT_REQUEST_KEY_PROVIDER = new KeyProvider<>() {
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
    private static final KeyProvider<UnbanRequest> UNBAN_REQUEST_KEY_PROVIDER = new KeyProvider<>() {
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

    private Set<String> invertedFalseClaims;
    private Set<String> invertedFalseApprovals;
    private Set<String> invertedFalseDeclinations;

    UserState() {

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
            throw new ValidationException("Version must be increased");
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
    public void write(IPLDWriter writer, Signer signer, IPLDContext context) throws IOException {
        writer.writeNumber(KEY_VERSION, version);
        writer.writeNumber(KEY_RATING, rating);
        writer.writeLink(KEY_USER, user, signer, context);
        writer.writeLink(KEY_PREVIOUS_VERSION, previousVersion, signer, null);
        writer.writeLinkObjects(KEY_DOCUMENTS, documents, signer, context);
        writer.writeLinkObjects(KEY_REMOVED_DOCUMENTS, removedDocuments, signer, null);
        writer.writeLinkObjects(KEY_FALSE_CLAIMS, falseClaims, signer, null);
        writer.writeLinkObjects(KEY_FALSE_APPROVALS, falseApprovals, signer, null);
        writer.writeLinkObjects(KEY_FALSE_DECLINATIONS, falseDeclinations, signer, null);
        writer.writeLinkObjects(KEY_SETTLEMENT_REQUESTS, settlementRequests, signer, context);
        writer.writeLinkObjects(KEY_OWNERSHIP_REQUESTS, ownershipRequests, signer, context);
        writer.writeLinkObjects(KEY_UNBAN_REQUESTS, unbanRequests, signer, context);
        writer.writeLinkObjects(KEY_GRANTED_OWNERSHIPS, grantedOwnerships, signer, context);
        writer.writeLinkObjects(KEY_GRANTED_UNBANS, grantedUnbans, signer, context);
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
     * @param documentHash the document hash
     * @return the document wrapper stored in this instance with the given hash (null-safe)
     */
    public IPLDObject<Document> getDocument(String documentHash) {
        return documents == null ? null : documents.get(documentHash);
    }

    /**
     * @param documentHash the document hash
     * @return the document stored in this instance with the given hash (no null checks!)
     */
    public Document expectDocument(String documentHash) {
        return documents.get(documentHash).getMapped();
    }

    public Map<String, IPLDObject<Document>> getAllDocuments() {
        return documents == null ? null : Collections.unmodifiableMap(documents);
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
        boolean res = false;
        if (documents != null) {
            for (IPLDObject<Document> document : documents.values()) {
                Document doc = document.getMapped();
                if (doc instanceof Review) {
                    Review review = (Review) doc;
                    if (documentHash.equals(review.getDocument().getMultihash())) {
                        Boolean approve = review.getApprove();
                        if (disallowedApproveValue.equals(approve)) {
                            res = false;
                        }
                        else { // neutral reviews allowed - currently, might change
                            res = true;
                        }
                    }
                }
            }
        }
        return res;
    }

    /**
     * Finds related documents and returns the date of the most recent one or null, if there is none (in which case the
     * date of the document with the given hash can be assumed as the last activity date).
     * 
     * @param documentHash the document hash
     * @return the last activity date or null, if the document itself is the last activity
     */
    public Date getLastActivityDate(String documentHash) {
        Set<String> relatedHashes = new HashSet<>();
        Set<String> unrelatedHashes = new HashSet<>();
        relatedHashes.add(documentHash);
        Document lastActivity = null;
        main: for (IPLDObject<Document> document : documents.values()) {
            String docHash = document.getMultihash();
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

    /**
     * Creates a copy of this instance, updates and returns it. The given current version is set as its previousVersion.
     * 
     * @param docs                       the documents to add
     * @param requests                   the ownership request to add
     * @param granted                    the granted ownerships to add
     * @param transferredOwnershipHashes the hashes of documents that have been transferred to other users
     * @param current                    the current wrapper (should not be null)
     */
    public UserState updateLinks(Collection<IPLDObject<Document>> docs,
            Collection<IPLDObject<OwnershipRequest>> requests, Collection<IPLDObject<GrantedOwnership>> granted,
            Collection<String> transferredOwnershipHashes, IPLDObject<UserState> current) {
        UserState updated = copy();
        updated.version = this.version + 1;
        updated.previousVersion = current;
        if (transferredOwnershipHashes != null && transferredOwnershipHashes.size() > 0) {
            for (String hash : transferredOwnershipHashes) {
                updated.documents.remove(hash);
                if (updated.grantedOwnerships != null && updated.grantedOwnerships.size() > 0) {
                    String toRemove = null;
                    for (Entry<String, IPLDObject<GrantedOwnership>> entry : updated.grantedOwnerships.entrySet()) {
                        IPLDObject<Document> document = entry.getValue().getMapped().getDocument();
                        if (document.getMultihash().equals(hash)) {
                            toRemove = entry.getKey();
                            break;
                        }
                    }
                    if (toRemove != null) {
                        updated.grantedOwnerships.remove(toRemove);
                    }
                }
            }
        }
        if (requests != null && requests.size() > 0) {
            if (updated.ownershipRequests == null) {
                updated.ownershipRequests = new LinkedHashMap<>();
            }
            for (IPLDObject<OwnershipRequest> ownershipRequest : requests) {
                updated.ownershipRequests.put(OWNERSHIP_REQUEST_KEY_PROVIDER.getKey(ownershipRequest),
                        ownershipRequest);
            }
        }
        if (granted != null && granted.size() > 0) {
            if (updated.grantedOwnerships == null) {
                updated.grantedOwnerships = new LinkedHashMap<>();
            }
            for (IPLDObject<GrantedOwnership> grantedOwnership : granted) {
                updated.grantedOwnerships.put(GRANTED_OWNERSHIP_KEY_PROVIDER.getKey(grantedOwnership),
                        grantedOwnership);
            }
        }
        if (docs != null && docs.size() > 0) {
            if (updated.documents == null) {
                updated.documents = new LinkedHashMap<>();
            }
            for (IPLDObject<Document> document : docs) {
                updated.documents.put(DOCUMENT_KEY_PROVIDER.getKey(document), document);
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

    public void removeTrueClaim(IPLDObject<Document> falseClaim) {
        rating -= TRUE_CLAIM_REWARD;
        addFalseClaim(falseClaim);
    }

    public void removeTrueApproval(IPLDObject<Review> falseApproval) {
        rating -= TRUE_APPROVAL_REWARD;
        addFalseApproval(falseApproval);
    }

    public void removeTrueDeclination(IPLDObject<Review> falseDeclination) {
        rating -= TRUE_DECLINATION_REWARD;
        addFalseDeclination(falseDeclination);
    }

    public Collection<IPLDObject<Document>> getNewDocuments(UserState since, boolean ignoreCached) {
        if (ignoreCached || newDocuments == null) {
            newDocuments = ModelUtility.getNewForeignKeyLinks(documents, since == null ? null : since.documents);
        }
        return newDocuments;
    }

    public Map<String, IPLDObject<DocumentRemoval>> getNewRemovedDocuments(UserState since, boolean ignoreCached) {
        if (ignoreCached || newRemovedDocuments == null) {
            newRemovedDocuments = ModelUtility.getNewForeignKeyLinksMap(removedDocuments,
                    since == null ? null : since.removedDocuments);
        }
        return newRemovedDocuments;
    }

    public Collection<IPLDObject<SettlementRequest>> getNewSettlementRequests(UserState since, boolean ignoreCached) {
        if (ignoreCached || newSettlementRequests == null) {
            newSettlementRequests = ModelUtility.getNewForeignKeyLinks(settlementRequests,
                    since == null ? null : since.settlementRequests);
        }
        return newSettlementRequests;
    }

    public Collection<IPLDObject<OwnershipRequest>> getNewOwnershipRequests(UserState since, boolean ignoreCached) {
        if (ignoreCached || newOwnershipRequests == null) {
            newOwnershipRequests = ModelUtility.getNewForeignKeyLinks(ownershipRequests,
                    since == null ? null : since.ownershipRequests);
        }
        return newOwnershipRequests;
    }

    public Collection<IPLDObject<UnbanRequest>> getNewUnbanRequests(UserState since, boolean ignoreCached) {
        if (ignoreCached || newUnbanRequests == null) {
            newUnbanRequests = ModelUtility.getNewForeignKeyLinks(unbanRequests,
                    since == null ? null : since.unbanRequests);
        }
        return newUnbanRequests;
    }

    public Collection<IPLDObject<GrantedOwnership>> getNewGrantedOwnerships(UserState since, boolean ignoreCached) {
        if (ignoreCached || newGrantedOwnerships == null) {
            newGrantedOwnerships = ModelUtility.getNewForeignKeyLinks(grantedOwnerships,
                    since == null ? null : since.grantedOwnerships);
        }
        return newGrantedOwnerships;
    }

    public Collection<IPLDObject<GrantedUnban>> getNewGrantedUnbans(UserState since, boolean ignoreCached) {
        if (ignoreCached || newGrantedUnbans == null) {
            newGrantedUnbans = ModelUtility.getNewForeignKeyLinks(grantedUnbans,
                    since == null ? null : since.grantedUnbans);
        }
        return newGrantedUnbans;
    }

    boolean prepareSettlementValidation(SettlementController controller) {
        if (documents != null) {
            boolean res = false;
            for (IPLDObject<Document> document : documents.values()) {
                res = controller.checkDocument(document, true) || res;
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
                    String hash = document.getMultihash();
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
                    res.grantedOwnerships.put(GRANTED_OWNERSHIP_KEY_PROVIDER.getKey(granted), granted);
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
                    res.grantedUnbans.put(GRANTED_UNBAN_KEY_PROVIDER.getKey(granted), granted);
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
                String userHash = user.getMultihash();
                for (IPLDObject<GrantedUnban> request : newGrantedUnbans) {
                    mainSettlementController.checkGrantedUnban(request.getMapped(), userHash);
                }
            }
        }
        return res;
    }

    boolean checkSettlementDocuments(ValidationContext validationContext) {
        SettlementController mainSettlementController = validationContext.getMainSettlementController();
        if (documents != null) {
            boolean res = false;
            for (IPLDObject<Document> document : documents.values()) {
                res = mainSettlementController.checkDocument(document, true) || res;
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
            for (Entry<String, IPLDObject<DocumentRemoval>> entry : newRemovedDocs.entrySet()) {
                mainSettlementController.checkRemovedDocument(entry.getValue().getMapped().getDocument(), current);
            }
        }
        Collection<IPLDObject<Document>> newDocuments = getNewDocuments(previous, true);
        if (newDocuments != null) {
            this.newDocuments = null;
            for (IPLDObject<Document> document : newDocuments) {
                mainSettlementController.checkDocument(document, true);
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

    public void validateSettlement(UserState expectedValues) {
        if (rating != expectedValues.rating) {
            throw new ValidationException("Unexpected rating");
        }
        validateFalseMap(expectedValues.falseClaims, falseClaims);
        validateFalseMap(expectedValues.falseApprovals, falseApprovals);
        validateFalseMap(expectedValues.falseDeclinations, falseDeclinations);
    }

    public void applySettlement(UserState settlementValues) {
        this.rating += settlementValues.rating;
        if (settlementValues.invertedFalseClaims != null) {
            for (String invertedFalseClaim : settlementValues.invertedFalseClaims) {
                falseClaims.remove(invertedFalseClaim);
            }
        }
        if (settlementValues.falseClaims != null) {
            falseClaims.putAll(settlementValues.falseClaims);
        }
        if (settlementValues.invertedFalseApprovals != null) {
            for (String invertedFalseApproval : settlementValues.invertedFalseApprovals) {
                falseApprovals.remove(invertedFalseApproval);
            }
        }
        if (settlementValues.falseApprovals != null) {
            falseApprovals.putAll(settlementValues.falseApprovals);
        }
        if (settlementValues.invertedFalseDeclinations != null) {
            for (String invertedFalseDeclination : settlementValues.invertedFalseDeclinations) {
                falseDeclinations.remove(invertedFalseDeclination);
            }
        }
        if (settlementValues.falseDeclinations != null) {
            falseDeclinations.putAll(settlementValues.falseDeclinations);
        }
    }

    private <D extends IPLDSerializable> void validateFalseMap(Map<String, IPLDObject<D>> expected,
            Map<String, IPLDObject<D>> actual) {
        if (expected == null) {
            if (actual != null) {
                throw new ValidationException("Unexpected present false map");
            }
        }
        else if (actual == null) {
            throw new ValidationException("Unexpected missing false map");
        }
        else if (expected.size() != actual.size()) {
            throw new ValidationException("Unexpected size of false map");
        }
        else {
            for (String key : expected.keySet()) {
                if (!actual.containsKey(key)) {
                    throw new ValidationException("Unexpected missing false map entry");
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
