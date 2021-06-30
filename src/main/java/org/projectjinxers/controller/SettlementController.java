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
package org.projectjinxers.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.projectjinxers.model.Document;
import org.projectjinxers.model.GrantedUnban;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.SealedDocument;
import org.projectjinxers.model.User;
import org.projectjinxers.model.UserState;

/**
 * @author ProjectJinxers
 *
 */
public class SettlementController {

    static class SettlementData {

        private static final int MIN_TOTAL_COUNT = 10;
        private static final double MIN_MARGIN = 2.5;

        private boolean requested;
        private boolean forMainValidation;
        private String documentOwner;
        private IPLDObject<Document> document;
        private IPLDObject<Document> invertTruth;
        private Map<IPLDObject<User>, IPLDObject<Review>> reviews = new HashMap<>();
        private Map<String, IPLDObject<Review>> reviewsByDocumentHash = new HashMap<>();

        private int approveCount;
        private int declineCount;
        private int neutralCount;

        private IPLDObject<User> falseClaim;
        private Map<IPLDObject<User>, IPLDObject<Review>> falseApprovals;
        private Map<IPLDObject<User>, IPLDObject<Review>> falseDeclinations;
        private IPLDObject<User> trueClaim;
        private Set<IPLDObject<User>> trueApprovals;
        private Set<IPLDObject<User>> trueDeclinations;

        private SealedDocument sealedDocument;

        void addReview(IPLDObject<Review> reviewObject) {
            Review review = reviewObject.getMapped();
            UserState reviewer = review.expectUserState();
            IPLDObject<Review> replaced = reviews.put(reviewer.getUser(), reviewObject);
            if (replaced != null) {
                String firstVersionHash = reviewObject.getMapped().getFirstVersionHash();
                String fvh = replaced.getMapped().getFirstVersionHash();
                if (fvh == null) {
                    fvh = replaced.getMultihash();
                }
                if (!firstVersionHash.equals(fvh)) {
                    throw new ValidationException("multiple unrelated reviews by same user");
                }
            }
            reviewsByDocumentHash.put(reviewObject.getMultihash(), reviewObject);
        }

        void removeReview(IPLDObject<User> reviewer) {
            IPLDObject<Review> removed = reviews.remove(reviewer);
            if (removed != null) {
                reviewsByDocumentHash.remove(removed.getMultihash());
            }
        }

        boolean count() {
            int sum = approveCount + declineCount + neutralCount;
            if (sum == 0) {
                for (IPLDObject<Review> review : reviews.values()) {
                    Boolean approve = review.getMapped().getApprove();
                    if (approve == null) {
                        neutralCount++;
                    }
                    else if (Boolean.TRUE.equals(approve)) {
                        approveCount++;
                    }
                    else {
                        declineCount++;
                    }
                }
                sum = approveCount + declineCount + neutralCount;
            }
            return sum >= MIN_TOTAL_COUNT;
        }

        SealedDocument evaluate(ModelState modelState) {
            if (sealedDocument == null) {
                int totalCount = approveCount + declineCount + neutralCount;
                if (totalCount >= MIN_TOTAL_COUNT) {
                    if (approveCount > declineCount) {
                        if (approveCount * 1.0 / declineCount >= MIN_MARGIN) {
                            Document doc = document.getMapped();
                            Review review = doc instanceof Review ? (Review) doc : null;
                            boolean noNeutralReview = review == null || review.getApprove() != null;
                            if (review != null && Boolean.TRUE.equals(review.getApprove())
                                    && modelState.isTruthInverted(review.getDocument().getMultihash())) {
                                return declinersWon(noNeutralReview);
                            }
                            return approversWon(noNeutralReview);
                        }
                    }
                    else if (declineCount > approveCount) {
                        if (declineCount * 1.0 / approveCount >= MIN_MARGIN) {
                            Document doc = document.getMapped();
                            Review review = doc instanceof Review ? (Review) doc : null;
                            boolean noNeutralReview = review == null || review.getApprove() != null;
                            if (review != null && Boolean.FALSE.equals(review.getApprove())
                                    && modelState.isTruthInverted(review.getDocument().getMultihash())) {
                                return approversWon(noNeutralReview);
                            }
                            return declinersWon(noNeutralReview);
                        }
                    }
                }
            }
            return sealedDocument;
        }

        boolean isAffected(IPLDObject<User> user) {
            return user.equals(falseClaim) || falseApprovals != null && falseApprovals.containsKey(user)
                    || falseDeclinations != null && falseDeclinations.containsKey(user);
        }

        void update(Map<String, UserState> userStates) {
            if (falseClaim != null) {
                ensureUserState(userStates, falseClaim).addFalseClaim(document);
                for (Entry<IPLDObject<User>, IPLDObject<Review>> entry : falseApprovals.entrySet()) {
                    ensureUserState(userStates, entry.getKey()).addFalseApproval(entry.getValue());
                }
                if (trueDeclinations != null) {
                    for (IPLDObject<User> user : trueDeclinations) {
                        ensureUserState(userStates, user).handleTrueDeclination();
                    }
                }
            }
            else if (falseDeclinations != null) {
                if (trueClaim != null) {
                    ensureUserState(userStates, trueClaim).handleTrueClaim();
                    for (IPLDObject<User> user : trueApprovals) {
                        ensureUserState(userStates, user).handleTrueApproval();
                    }
                }
                for (Entry<IPLDObject<User>, IPLDObject<Review>> entry : falseDeclinations.entrySet()) {
                    ensureUserState(userStates, entry.getKey()).addFalseDeclination(entry.getValue());
                }
            }
        }

        SettlementData createPreEvaluationSnapshot() {
            SettlementData res = new SettlementData();
            res.requested = requested;
            res.forMainValidation = forMainValidation;
            res.documentOwner = documentOwner;
            res.document = document;
            res.invertTruth = invertTruth;
            res.reviews.putAll(reviews);
            return res;
        }

        void reset() {
            this.approveCount = 0;
            this.declineCount = 0;
            this.neutralCount = 0;
            this.falseClaim = null;
            this.falseApprovals = null;
            this.falseDeclinations = null;
            this.sealedDocument = null;
        }

        /*
         * non-review: approved by majority -> owner true claim, approvers true approval, decliners false declination
         * [non-inverting] neutral review: approved by majority -> [owner nothing, approvers true approval,] decliners
         * false declination [non-inverting] approving review: approved by majority -> [owner true claim, approvers true
         * approval,] decliners false declination [non-inverting] declining review: approved by majority -> [owner true
         * claim, approvers true approval,] decliners false declination [non-inverting] neutral review of lie: approved
         * by majority -> [owner nothing, approvers true approval,] decliners false declination [non-inverting]
         * declining review of lie: declined by majority -> [owner true claim, approvers true approval,] decliners false
         * declination approved by majority -> [owner true claim, approvers true approval,] decliners false declination
         */
        private SealedDocument approversWon(boolean noNeutralReview) {
            falseDeclinations = new HashMap<>();
            if (invertTruth == null) {
                if (noNeutralReview) {
                    trueClaim = document.getMapped().expectUserState().getUser();
                }
                trueApprovals = new HashSet<>();
                for (Entry<IPLDObject<User>, IPLDObject<Review>> entry : reviews.entrySet()) {
                    IPLDObject<Review> value = entry.getValue();
                    Boolean approve = value.getMapped().getApprove();
                    if (approve != null) {
                        if (approve) {
                            trueApprovals.add(entry.getKey());
                        }
                        else {
                            falseDeclinations.put(entry.getKey(), value);
                        }
                    }
                }
            }
            else {
                for (Entry<IPLDObject<User>, IPLDObject<Review>> entry : reviews.entrySet()) {
                    IPLDObject<Review> value = entry.getValue();
                    if (Boolean.FALSE.equals(value.getMapped().getApprove())) {
                        falseDeclinations.put(entry.getKey(), value);
                    }
                }
            }
            sealedDocument = new SealedDocument(document);
            return sealedDocument;
        }

        /*
         * non-review: declined by majority -> owner false claim, decliners true declination, approvers false approval
         * [non-inverting] neutral review: declined by majority -> owner nothing, [decliners true declination,]
         * approvers false approval [non-inverting] approving review: declined by majority -> owner false claim,
         * [decliners true declination,] approvers false approval [non-inverting] declining review: declined by majority
         * -> owner false claim, [decliners true declination,] approvers false approval [non-inverting] neutral review
         * of lie: declined by majority -> owner nothing, [decliners true declination,] approvers false approval
         * [non-inverting] approving review of lie: declined by majority -> owner false claim, [decliners true
         * declination,] approvers false approval approved by majority -> owner false claim, [decliners true
         * declination,] approvers false approval
         */
        private SealedDocument declinersWon(boolean noNeutralReview) {
            if (noNeutralReview) {
                falseClaim = document.getMapped().expectUserState().getUser();
            }
            falseApprovals = new HashMap<>();
            if (invertTruth == null) {
                trueDeclinations = new HashSet<>();
                for (Entry<IPLDObject<User>, IPLDObject<Review>> entry : reviews.entrySet()) {
                    IPLDObject<Review> value = entry.getValue();
                    Boolean approve = value.getMapped().getApprove();
                    if (approve != null) {
                        if (approve) {
                            falseApprovals.put(entry.getKey(), value);
                        }
                        else {
                            trueDeclinations.add(entry.getKey());
                        }
                    }
                }
            }
            else {
                for (Entry<IPLDObject<User>, IPLDObject<Review>> entry : reviews.entrySet()) {
                    IPLDObject<Review> value = entry.getValue();
                    if (Boolean.TRUE.equals(value.getMapped().getApprove())) {
                        falseApprovals.put(entry.getKey(), value);
                    }
                }
            }
            sealedDocument = new SealedDocument(document);
            return sealedDocument;
        }

    }

    static class TruthInversionGraphNode {

        IPLDObject<Document> document;
        Review review;
        Map<String, TruthInversionGraphNode> children; // reviews
        Map<String, TruthInversionGraphNode> links;
        TruthInversionGraphNode parent; // reviewed

        void traverse(Map<String, UserState> userStates, ModelState modelState,
                Map<String, SealedDocument> sealedDocuments, Set<String> visited, boolean isDefinitelyReview) {
            SealedDocument sealed = invertTruth(userStates, modelState, sealedDocuments, visited, null,
                    isDefinitelyReview);
            sealedDocuments.put(document.getMultihash(), sealed);
            if (!isDefinitelyReview && parent != null) {
                String key = parent.document.getMultihash();
                if (visited.add(key)) {
                    sealed = parent.invertTruth(userStates, modelState, sealedDocuments, visited, this, false);
                    sealedDocuments.put(key, sealed);
                    parent.traverse(userStates, modelState, sealedDocuments, visited, false);
                }
            }
            if (links != null) {
                for (Entry<String, TruthInversionGraphNode> entry : links.entrySet()) {
                    if (visited.add(entry.getKey())) {
                        entry.getValue().traverse(userStates, modelState, sealedDocuments, visited, false);
                    }
                }
            }
        }

        private SealedDocument invertTruth(Map<String, UserState> userStates, ModelState modelState,
                Map<String, SealedDocument> sealedDocuments, Set<String> visited, TruthInversionGraphNode exclude,
                boolean isDefinitelyReview) {
            SealedDocument sealed = modelState.expectSealedDocument(document.getMultihash());
            boolean wasInverted = sealed.isTruthInverted();
            SealedDocument res = sealed.invertTruth();
            Document doc = document.getMapped();
            Review review = (isDefinitelyReview || doc instanceof Review) ? (Review) doc : null;
            boolean noNeutralReview = review == null || review.getApprove() != null;
            IPLDObject<User> user = doc.expectUserState().getUser();
            String userHash = user.getMultihash();
            UserState userState = ensureUserState(userStates, user);
            Map<String, UserState> approvers = new HashMap<>();
            Map<String, UserState> decliners = new HashMap<>();
            Map<String, IPLDObject<Review>> reviews = new HashMap<>();
            for (Entry<String, TruthInversionGraphNode> entry : children.entrySet()) {
                TruthInversionGraphNode child = entry.getValue();
                Boolean approve = child.review.getApprove();
                if (approve != null) {
                    user = child.review.expectUserState().getUser();
                    userHash = user.getMultihash();
                    UserState reviewerState = ensureUserState(userStates, user);
                    if (approve) {
                        approvers.put(userHash, reviewerState);
                    }
                    else {
                        decliners.put(userHash, reviewerState);
                    }
                    @SuppressWarnings("rawtypes")
                    IPLDObject childDoc = child.document;
                    @SuppressWarnings("unchecked")
                    IPLDObject<Review> reviewObject = childDoc;
                    reviews.put(userHash, reviewObject);
                }
                if (child != exclude && visited.add(entry.getKey()) && modelState.isSealedDocument(entry.getKey())) {
                    child.traverse(userStates, modelState, sealedDocuments, visited, true);
                }
            }
            if (approvers.size() > decliners.size()) {
                if (review != null && Boolean.TRUE.equals(review.getApprove())
                        && modelState.isTruthInverted(review.getDocument().getMultihash())) {
                    if (wasInverted) {
                        restoreDeclinersWon(userState, approvers, decliners, reviews, noNeutralReview);
                    }
                    else {
                        invertDeclinersWon(userState, approvers, decliners, reviews, noNeutralReview);
                    }
                }
                else if (wasInverted) {
                    restoreApproversWon(userState, approvers, decliners, reviews, noNeutralReview);
                }
                else {
                    invertApproversWon(userState, approvers, decliners, reviews, noNeutralReview);
                }
            }
            else {
                if (review != null && Boolean.FALSE.equals(review.getApprove())
                        && modelState.isTruthInverted(review.getDocument().getMultihash())) {
                    if (wasInverted) {
                        restoreApproversWon(userState, approvers, decliners, reviews, noNeutralReview);
                    }
                    else {
                        invertApproversWon(userState, approvers, decliners, reviews, noNeutralReview);
                    }
                }
                else if (wasInverted) {
                    restoreDeclinersWon(userState, approvers, decliners, reviews, noNeutralReview);
                }
                else {
                    invertDeclinersWon(userState, approvers, decliners, reviews, noNeutralReview);
                }
            }
            return res;
        }

        private void invertApproversWon(UserState ownerState, Map<String, UserState> approvers,
                Map<String, UserState> decliners, Map<String, IPLDObject<Review>> reviews, boolean noNeutralReview) {
            for (Entry<String, UserState> entry : decliners.entrySet()) {
                entry.getValue().removeFalseDeclination(reviews.get(entry.getKey()));
            }
            if (review == null || !review.isInvertTruth()) {
                if (noNeutralReview) {
                    ownerState.removeTrueClaim(document);
                }
                for (Entry<String, UserState> entry : approvers.entrySet()) {
                    entry.getValue().removeTrueApproval(reviews.get(entry.getKey()));
                }
            }
        }

        private void restoreApproversWon(UserState ownerState, Map<String, UserState> approvers,
                Map<String, UserState> decliners, Map<String, IPLDObject<Review>> reviews, boolean noNeutralReview) {
            for (Entry<String, UserState> entry : decliners.entrySet()) {
                entry.getValue().addFalseDeclination(reviews.get(entry.getKey()));
            }
            if (review == null || !review.isInvertTruth()) {
                if (noNeutralReview) {
                    ownerState.removeFalseClaim(document);
                }
                for (Entry<String, UserState> entry : approvers.entrySet()) {
                    entry.getValue().removeFalseApproval(reviews.get(entry.getKey()));
                }
            }
        }

        private void invertDeclinersWon(UserState ownerState, Map<String, UserState> approvers,
                Map<String, UserState> decliners, Map<String, IPLDObject<Review>> reviews, boolean noNeutralReview) {
            for (Entry<String, UserState> entry : approvers.entrySet()) {
                entry.getValue().removeFalseApproval(reviews.get(entry.getKey()));
            }
            if (review == null || !review.isInvertTruth()) {
                if (noNeutralReview) {
                    ownerState.removeFalseClaim(document);
                }
                for (Entry<String, UserState> entry : decliners.entrySet()) {
                    entry.getValue().removeTrueDeclination(reviews.get(entry.getKey()));
                }
            }
        }

        private void restoreDeclinersWon(UserState ownerState, Map<String, UserState> approvers,
                Map<String, UserState> decliners, Map<String, IPLDObject<Review>> reviews, boolean noNeutralReview) {
            for (Entry<String, UserState> entry : approvers.entrySet()) {
                entry.getValue().addFalseApproval(reviews.get(entry.getKey()));
            }
            if (review == null || !review.isInvertTruth()) {
                if (noNeutralReview) {
                    ownerState.removeTrueClaim(document);
                }
                for (Entry<String, UserState> entry : decliners.entrySet()) {
                    entry.getValue().removeFalseDeclination(reviews.get(entry.getKey()));
                }
            }
        }

    }

    static class TruthInversionGraph extends TruthInversionGraphNode {

        static TruthInversionGraph createGraph(IPLDObject<Document> invertTruth) {
            TruthInversionGraph res = new TruthInversionGraph();
            res.index = new HashMap<>();
            res.secondaryEntryPoints = new HashMap<>();
            res.document = invertTruth;
            res.index.put(invertTruth.getMultihash(), res);
            Document doc = invertTruth.getMapped();
            if (doc instanceof Review) {
                res.review = (Review) doc;
            }
            return res;
        }

        private Map<String, TruthInversionGraphNode> index;
        private Map<String, TruthInversionGraphNode> secondaryEntryPoints;

        // initialization
        TruthInversionGraphNode addTruthInversion(IPLDObject<Document> document) {
            String multihash = document.getMultihash();
            TruthInversionGraphNode node = index.get(multihash);
            TruthInversionGraphNode res;
            if (node == null) {
                node = new TruthInversionGraphNode();
                node.document = document;
                Document doc = document.getMapped();
                if (doc instanceof Review) {
                    node.review = (Review) doc;
                }
                else if (doc == null) {
                    throw new ValidationException("Can't validate potientially cascading truth inversion");
                }
                index.put(multihash, node);
                secondaryEntryPoints.put(multihash, node);
                res = node;
            }
            else {
                res = null;
            }
            return res;
        }

        // first pass - all old (i.e. not newly settled) sealed truth inversion reviews
        TruthInversionGraphNode addTruthInversionReview(IPLDObject<Document> reviewObject) {
            Review review = (Review) reviewObject.getMapped();
            String multihash = reviewObject.getMultihash();
            TruthInversionGraphNode node = index.get(multihash);
            TruthInversionGraphNode res;
            if (node == null) {
                node = new TruthInversionGraphNode();
                node.document = reviewObject;
                node.review = review;
                index.put(multihash, node);
                res = node;
            }
            else {
                res = null;
            }
            IPLDObject<Document> parent = review.getDocument();
            String parentHash = parent.getMultihash();
            TruthInversionGraphNode parentNode = index.get(parentHash);
            if (parentNode == null) {
                parentNode = new TruthInversionGraphNode();
                parentNode.document = parent;
                Document doc = parent.getMapped();
                if (doc instanceof Review) {
                    parentNode.review = (Review) doc;
                }
                else if (doc == null) {
                    throw new ValidationException("Can't validate potientially cascading truth inversion");
                }
                index.put(parentHash, parentNode);
            }
            if (parentNode.children == null) {
                parentNode.children = new HashMap<>();
            }
            parentNode.children.put(multihash, node);
            node.parent = parentNode;
            addInvertTruthLinks(review, node);
            return res;
        }

        // second pass - all other reviews (including the newly settled truth inversion reviews)
        TruthInversionGraphNode addReview(IPLDObject<Document> reviewObject) {
            Review review = (Review) reviewObject.getMapped();
            IPLDObject<Document> document = review.getDocument();
            String parentHash = document.getMultihash();
            TruthInversionGraphNode reviewedNode = index.get(parentHash);
            if (reviewedNode != null) { // no immediate review of an affected document => irrelevant
                if (reviewedNode == this || (secondaryEntryPoints != null
                        && reviewedNode == secondaryEntryPoints.get(reviewObject.getMultihash()))) {
                    // we don't want the newly settled truth inversion reviews in this graph, but the links have to be
                    // somewhere, so we add them to the reviewed document
                    addInvertTruthLinks(review, reviewedNode);
                }
                else {
                    String multihash = reviewObject.getMultihash();
                    TruthInversionGraphNode reviewNode = index.get(multihash);
                    TruthInversionGraphNode res;
                    if (reviewNode == null) {
                        reviewNode = new TruthInversionGraphNode();
                        reviewNode.document = reviewObject;
                        reviewNode.review = review;
                        index.put(multihash, reviewNode);
                        res = reviewNode;
                    }
                    else {
                        res = null;
                    }
                    if (reviewedNode.children == null) {
                        reviewedNode.children = new HashMap<>();
                    }
                    reviewedNode.children.put(multihash, reviewNode);
                    reviewNode.parent = reviewedNode;
                    return res;
                }
            }
            return null;
        }

        private void addInvertTruthLinks(Review review, TruthInversionGraphNode node) {
            Map<String, IPLDObject<Document>> allInvertTruthLinks = review.getAllInvertTruthLinks();
            if (allInvertTruthLinks != null) {
                for (Entry<String, IPLDObject<Document>> entry : allInvertTruthLinks.entrySet()) {
                    String key = entry.getKey();
                    IPLDObject<Document> value = entry.getValue();
                    Document doc = value.getMapped();
                    if (!(doc instanceof Review) || !((Review) doc).isInvertTruth()) {
                        TruthInversionGraphNode linkNode = index.get(key);
                        if (linkNode == null) {
                            linkNode = new TruthInversionGraphNode();
                            linkNode.document = value;
                            if (doc instanceof Review) {
                                linkNode.review = (Review) doc;
                            }
                            index.put(key, linkNode);
                        }
                        if (node.links == null) {
                            node.links = new HashMap<>();
                        }
                        node.links.put(key, linkNode);
                    }
                }
            }
        }

        void traverse(Map<String, UserState> userStates, ModelState modelState,
                Map<String, SealedDocument> sealedDocuments) {
            Set<String> visited = new HashSet<>();
            visited.add(document.getMultihash());
            traverse(userStates, modelState, sealedDocuments, visited, false);
            if (secondaryEntryPoints != null) {
                for (Entry<String, TruthInversionGraphNode> entry : secondaryEntryPoints.entrySet()) {
                    if (visited.add(entry.getKey())) {
                        TruthInversionGraphNode secondary = entry.getValue();
                        secondary.traverse(userStates, modelState, sealedDocuments, visited, false);
                    }
                }
            }
        }

    }

    private static final Long REQUEST_THRESHOLD = 1000L * 60 * 60 * 24 * 4;
    private static final Long SETTLEMENT_THRESHOLD = 1000L * 60 * 60 * 24 * 4;

    private static UserState ensureUserState(Map<String, UserState> userStates, IPLDObject<User> user) {
        String key = user.getMultihash();
        UserState res = userStates.get(key);
        if (res == null) {
            res = new UserState(user);
            userStates.put(key, res);
        }
        return res;
    }

    private boolean main;
    private long timestamp;

    private boolean validationMode = true;
    private boolean forMainValidation;
    private boolean resetSettlementData;

    private Set<String> documentOwners = new TreeSet<>();
    private Set<String> invalidRequests = new TreeSet<>();
    private Map<String, SettlementData> eligibleSettlements = new HashMap<>();
    private Set<String> unbanned;
    private Map<String, IPLDObject<User>> grantedClaimUnbans;
    private Map<String, IPLDObject<User>> grantedApprovalUnbans;
    private Map<String, IPLDObject<User>> grantedDeclinationUnbans;

    private Map<String, String> invalidByReview;
    private Set<String> invalidByTimestamp;
    private Set<String> removedDocuments;
    private Set<String> documentOwnersMainValidation;
    private Map<String, SettlementData> eligibleMainValidation;

    private TruthInversionGraph truthInversionGraph;

    SettlementController(boolean main, long timestamp) {
        this.main = main;
        this.timestamp = timestamp;
        if (main) {
            this.forMainValidation = true;
            this.invalidByReview = new HashMap<>();
            this.invalidByTimestamp = new TreeSet<>();
            this.removedDocuments = new TreeSet<>();
        }
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean checkRequest(IPLDObject<Document> document, boolean sealed, boolean forMainValidation) {
        if (main && forMainValidation != this.forMainValidation) {
            this.timestamp = System.currentTimeMillis();
            this.forMainValidation = forMainValidation;
        }
        String documentMultihash = document.getMultihash();
        if (invalidRequests.contains(documentMultihash)) {
            if (validationMode) {
                throw new ValidationException(
                        "settlement request on removed, replaced or too recently negatively reviewed document");
            }
            return false;
        }
        Document doc = document.getMapped();
        long time = doc.getDate().getTime();
        long diff = timestamp - time;
        if (invalidByTimestamp != null && invalidByTimestamp.contains(documentMultihash)) {
            if (diff >= SETTLEMENT_THRESHOLD) {
                invalidByTimestamp.remove(documentMultihash);
                return true;
            }
            return false;
        }
        if (diff < REQUEST_THRESHOLD) {
            if (validationMode) {
                throw new ValidationException("settlement request too early");
            }
            invalidRequests.add(documentMultihash);
            return false;
        }
        SettlementData data = addEligibleSettlement(document, false);
        data.requested = true;
        if (forMainValidation) {
            data.forMainValidation = true;
        }
        eligibleSettlements.put(documentMultihash, data);
        if (diff < SETTLEMENT_THRESHOLD) {
            if (sealed && validationMode) {
                throw new ValidationException("invalid sealing");
            }
            if (main) {
                invalidByTimestamp.add(documentMultihash);
            }
            return false;
        }
        return true;
    }

    public boolean checkDocument(IPLDObject<Document> document, boolean needRequest, Set<String> newReviewTableKeys,
            Map<String, Set<String>> newReviewTableValues) {
        Document doc = document.getMapped();
        String previousVersionHash = doc.getPreviousVersionHash();
        if (previousVersionHash != null) {
            invalidRequests.add(previousVersionHash);
            eligibleSettlements.remove(previousVersionHash);
        }
        if (doc instanceof Review) {
            Review review = (Review) doc;
            IPLDObject<Document> reviewed = review.getDocument();
            String documentMultihash = reviewed.getMultihash();
            if (newReviewTableValues != null) {
                Set<String> values = newReviewTableValues.get(documentMultihash);
                if (values != null && values.remove(document.getMultihash()) && values.size() == 0) {
                    newReviewTableValues.remove(documentMultihash);
                }
            }
            if (invalidRequests.contains(documentMultihash)) {
                return false;
            }
            if (Boolean.FALSE.equals(review.getApprove())) {
                long time = review.getDate().getTime();
                long diff = timestamp - time;
                if (diff < REQUEST_THRESHOLD) {
                    if (validationMode) {
                        throw new ValidationException("settlement postponed by review");
                    }
                    if (main) {
                        if (removedDocuments.contains(document.getMultihash())) {
                            return false;
                        }
                        invalidByReview.put(document.getMultihash(), documentMultihash);
                    }
                    else {
                        invalidRequests.add(documentMultihash);
                        eligibleSettlements.remove(documentMultihash);
                        return false;
                    }
                }
                else if (diff < SETTLEMENT_THRESHOLD) {
                    if (main) {
                        invalidByReview.put(document.getMultihash(), documentMultihash);
                    }
                    else {
                        eligibleSettlements.remove(documentMultihash);
                        return false;
                    }
                }
            }
            SettlementData data = addEligibleSettlement(reviewed, needRequest);
            if (data != null) {
                @SuppressWarnings("rawtypes")
                IPLDObject tmp = document;
                @SuppressWarnings("unchecked")
                IPLDObject<Review> reviewObject = tmp;
                data.addReview(reviewObject);
                return true;
            }
        }
        else if (newReviewTableKeys != null) {
            newReviewTableKeys.remove(document.getMultihash());
        }
        return false;
    }

    public boolean checkRemovedDocument(IPLDObject<Document> removed, ModelState current) {
        String removedHash = removed.getMultihash();
        if (main) {
            removedDocuments.add(removedHash);
        }
        if (current == null) {
            invalidRequests.add(removedHash);
            return false;
        }
        if (!current.isSealedDocument(removedHash)) {
            if (invalidRequests.add(removedHash)) {
                eligibleSettlements.remove(removedHash);
            }
            else {
                return false;
            }
        }
        Document document = removed.getMapped();
        if (document instanceof Review) {
            Review review = (Review) document;
            IPLDObject<Document> reviewed = review.getDocument();
            if (main) {
                invalidByReview.remove(removedHash);
            }
            SettlementData data = eligibleSettlements.get(reviewed.getMultihash());
            if (data != null) {
                data.removeReview(document.expectUserState().getUser());
            }
        }
        return true;
    }

    public void checkGrantedUnban(GrantedUnban unban, IPLDObject<User> user) {
        IPLDObject<Document> document = unban.getUnbanRequest().getMapped().getDocument();
        String documentHash = document.getMultihash();
        Document doc = document.getMapped();
        if (doc instanceof Review) {
            if (((Review) doc).getApprove()) {
                if (grantedApprovalUnbans == null) {
                    grantedApprovalUnbans = new HashMap<>();
                }
                grantedApprovalUnbans.put(documentHash, user);
            }
            else {
                if (grantedDeclinationUnbans == null) {
                    grantedDeclinationUnbans = new HashMap<>();
                }
                grantedDeclinationUnbans.put(documentHash, user);
            }
        }
        else {
            if (grantedClaimUnbans == null) {
                grantedClaimUnbans = new HashMap<>();
            }
            grantedClaimUnbans.put(documentHash, user);
        }
        if (unbanned == null) {
            unbanned = new HashSet<>();
        }
        unbanned.add(user.getMultihash());
    }

    private SettlementData addEligibleSettlement(IPLDObject<Document> document, boolean needRequest) {
        String documentMultihash = document.getMultihash();
        SettlementData data = eligibleSettlements.get(documentMultihash);
        if (data == null) {
            if (needRequest) {
                return null;
            }
            data = new SettlementData();
            Document doc = document.getMapped();
            String owner = doc.expectUserState().getUser().getMultihash();
            data.documentOwner = owner;
            data.document = document;
            if (doc instanceof Review) {
                Review review = (Review) doc;
                if (review.isInvertTruth()) {
                    data.invertTruth = review.getDocument();
                }
            }
            documentOwners.add(owner);
            eligibleSettlements.put(documentMultihash, data);
        }
        else if (needRequest && !data.requested) {
            return null;
        }
        return data;
    }

    public boolean checkUser(String userHash) {
        if (unbanned != null && unbanned.contains(userHash)) {
            return true;
        }
        if (main && validationMode && documentOwnersMainValidation != null) {
            return documentOwnersMainValidation.contains(userHash);
        }
        return documentOwners.contains(userHash);
    }

    public boolean evaluate(Map<String, SealedDocument> sealedDocuments, ModelState modelState) {
        boolean res;
        if (main && validationMode) {
            res = evaluateMainValidation(sealedDocuments, modelState);
        }
        else {
            if (resetSettlementData) {
                for (SettlementData data : eligibleMainValidation.values()) {
                    data.reset();
                }
                resetSettlementData = false;
            }
            res = false;
            Set<String> toRemove = new HashSet<>();
            Set<String> invalid;
            if (main) {
                invalid = new TreeSet<>(invalidByReview.values());
                invalid.addAll(invalidByTimestamp);
            }
            else {
                invalid = null;
            }
            for (Entry<String, SettlementData> entry : eligibleSettlements.entrySet()) {
                String key = entry.getKey();
                SettlementData data = entry.getValue();
                if (data.requested && (invalid == null || !invalid.contains(key)) && data.count()) {
                    SealedDocument evaluated = data.evaluate(modelState);
                    if (evaluated != null) {
                        res = true;
                        sealedDocuments.put(key, evaluated);
                        if (data.falseClaim == null && data.invertTruth != null) {
                            if (truthInversionGraph == null) {
                                truthInversionGraph = TruthInversionGraph.createGraph(data.invertTruth);
                            }
                            else {
                                truthInversionGraph.addTruthInversion(data.invertTruth);
                            }
                        }
                    }
                }
                else {
                    toRemove.add(entry.getKey());
                }
            }
            for (String key : toRemove) {
                eligibleSettlements.remove(key);
            }
            res = res || unbanned != null;
        }
        if (truthInversionGraph != null) {
            try {
                Collection<IPLDObject<UserState>> userStates = modelState.expectAllUserStates();
                Collection<IPLDObject<Document>> reviews = new ArrayList<>();
                for (IPLDObject<UserState> userState : userStates) {
                    Map<String, IPLDObject<Document>> allDocuments = userState.getMapped().getAllDocuments();
                    if (allDocuments != null) {
                        for (Entry<String, IPLDObject<Document>> entry : allDocuments.entrySet()) {
                            IPLDObject<Document> document = entry.getValue();
                            Document doc = document.getMapped();
                            if (doc instanceof Review) {
                                if (((Review) doc).isInvertTruth() && modelState.isSealedDocument(entry.getKey())) {
                                    truthInversionGraph.addTruthInversionReview(document);
                                }
                                else {
                                    reviews.add(document);
                                }
                            }
                            else if (doc == null) {
                                throw new ValidationException("Can't validate potientially cascading truth inversion");
                            }
                        }
                    }
                }
                for (IPLDObject<Document> review : reviews) {
                    truthInversionGraph.addReview(review);
                }
            }
            catch (ValidationException e) {
                throw e;
            }
            catch (Exception e) {
                throw new ValidationException("Can't validate potientially cascading truth inversion", e);
            }
        }
        return res;
    }

    private boolean evaluateMainValidation(Map<String, SealedDocument> sealedDocuments, ModelState modelState) {
        if (eligibleMainValidation == null) {
            eligibleMainValidation = new HashMap<>();
            documentOwnersMainValidation = new TreeSet<>();
            Set<String> invalid = new TreeSet<>(invalidByReview.values());
            for (Entry<String, SettlementData> entry : eligibleSettlements.entrySet()) {
                SettlementData data = entry.getValue();
                if (data.requested && data.forMainValidation) {
                    String documentHash = entry.getKey();
                    if (!invalid.contains(documentHash)) {
                        if (data.count()) {
                            SealedDocument evaluated = data.evaluate(modelState);
                            if (evaluated != null) {
                                eligibleMainValidation.put(documentHash, data);
                                documentOwnersMainValidation.add(data.documentOwner);
                                sealedDocuments.put(documentHash, evaluated);
                                if (data.falseClaim == null && data.invertTruth != null) {
                                    if (truthInversionGraph == null) {
                                        truthInversionGraph = TruthInversionGraph.createGraph(data.invertTruth);
                                    }
                                    else {
                                        truthInversionGraph.addTruthInversion(data.invertTruth);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return unbanned != null || eligibleMainValidation.size() > 0;
    }

    public boolean isAffected(IPLDObject<User> user) {
        if (unbanned != null && unbanned.contains(user.getMultihash())) {
            return true;
        }
        Map<String, SettlementData> eligible = main && validationMode ? eligibleMainValidation : eligibleSettlements;
        for (SettlementData data : eligible.values()) {
            if (data.isAffected(user)) {
                return true;
            }
        }
        return false;
    }

    public void update(Map<String, UserState> userStates, ModelState modelState,
            Map<String, SealedDocument> sealedDocuments) {
        Map<String, SettlementData> eligible = main && validationMode ? eligibleMainValidation : eligibleSettlements;
        for (SettlementData data : eligible.values()) {
            data.update(userStates);
        }
        if (truthInversionGraph != null) {
            truthInversionGraph.traverse(userStates, modelState, sealedDocuments);
        }
        if (unbanned != null) {
            if (grantedClaimUnbans != null) {
                for (Entry<String, IPLDObject<User>> entry : grantedClaimUnbans.entrySet()) {
                    ensureUserState(userStates, entry.getValue()).removeFalseClaim(entry.getKey());
                }
            }
            if (grantedApprovalUnbans != null) {
                for (Entry<String, IPLDObject<User>> entry : grantedApprovalUnbans.entrySet()) {
                    ensureUserState(userStates, entry.getValue()).removeFalseApproval(entry.getKey());
                }
            }
            if (grantedDeclinationUnbans != null) {
                for (Entry<String, IPLDObject<User>> entry : grantedDeclinationUnbans.entrySet()) {
                    ensureUserState(userStates, entry.getValue()).removeFalseDeclination(entry.getKey());
                }
            }
        }
    }

    boolean enterMergeMode() {
        if (main && validationMode) {
            validationMode = false;
            resetSettlementData = true;
            if (forMainValidation) {
                this.timestamp = System.currentTimeMillis();
                forMainValidation = false;
            }
            return true;
        }
        return false;
    }

    public SettlementController createPreEvaluationSnapshot(long timestamp) {
        SettlementController res = new SettlementController(true, timestamp <= 0 ? this.timestamp : timestamp);
        res.documentOwners.addAll(documentOwners);
        res.invalidRequests.addAll(invalidRequests);
        for (Entry<String, SettlementData> entry : eligibleSettlements.entrySet()) {
            res.eligibleSettlements.put(entry.getKey(), entry.getValue().createPreEvaluationSnapshot());
        }
        if (unbanned != null) {
            res.unbanned = new HashSet<>(unbanned);
        }
        if (grantedClaimUnbans != null) {
            res.grantedClaimUnbans = new HashMap<>(grantedClaimUnbans);
        }
        if (grantedApprovalUnbans != null) {
            res.grantedApprovalUnbans = new HashMap<>(grantedApprovalUnbans);
        }
        if (grantedDeclinationUnbans != null) {
            res.grantedDeclinationUnbans = new HashMap<>(grantedDeclinationUnbans);
        }
        if (invalidByReview != null) {
            res.invalidByReview.putAll(invalidByReview);
        }
        if (invalidByTimestamp != null) {
            res.invalidByTimestamp.addAll(invalidByTimestamp);
        }
        if (removedDocuments != null) {
            res.removedDocuments = new HashSet<>(removedDocuments);
        }
        return res;
    }

    public boolean applyNewTimestamp() {
        boolean res = false;
        for (Entry<String, String> entry : invalidByReview.entrySet()) {
            SettlementData settlementData = eligibleSettlements.get(entry.getValue());
            res = checkRequest(settlementData.document, false, false) || res;
            IPLDObject<Review> review = settlementData.reviewsByDocumentHash.get(entry.getKey());
            @SuppressWarnings("rawtypes")
            IPLDObject tmp = review;
            @SuppressWarnings("unchecked")
            IPLDObject<Document> document = tmp;
            res = checkDocument(document, true, null, null) || res;
        }
        return res;
    }

}
