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
import org.projectjinxers.model.SettlementRequest;
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
        private IPLDObject<Document> unsealing;
        private Map<String, IPLDObject<Review>> reviews = new HashMap<>();

        private int approveCount;
        private int declineCount;
        private int neutralCount;

        private String falseClaim;
        private Map<String, IPLDObject<Review>> falseApprovals;
        private Map<String, IPLDObject<Review>> falseDeclinations;
        private String trueClaim;
        private Set<String> trueApprovals;
        private Set<String> trueDeclinations;

        private SealedDocument sealedDocument;

        void addReview(IPLDObject<Review> reviewObject) {
            Review review = reviewObject.getMapped();
            UserState reviewer = review.expectUserState();
            String reviewerHash = reviewer.getUser().getMultihash();
            reviews.put(reviewerHash, reviewObject);
        }

        void removeReview(String reviewerHash) {
            reviews.remove(reviewerHash);
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

        SealedDocument evaluate() {
            if (sealedDocument == null) {
                int totalCount = approveCount + declineCount + neutralCount;
                if (totalCount >= MIN_TOTAL_COUNT) {
                    if (approveCount > declineCount) {
                        if (approveCount * 1.0 / declineCount >= MIN_MARGIN) {
                            return approvalsWon();
                        }
                    }
                    else if (declineCount > approveCount) {
                        if (declineCount * 1.0 / approveCount >= MIN_MARGIN) {
                            return declinationsWon();
                        }
                    }
                }
            }
            return sealedDocument;
        }

        boolean isAffected(String userHash) {
            return userHash.equals(falseClaim) || falseApprovals != null && falseApprovals.containsKey(userHash)
                    || falseDeclinations != null && falseDeclinations.containsKey(userHash);
        }

        void update(Map<String, UserState> userStates) {
            if (falseClaim != null) {
                userStates.get(falseClaim).addFalseClaim(document);
                for (Entry<String, IPLDObject<Review>> entry : falseApprovals.entrySet()) {
                    userStates.get(entry.getKey()).addFalseApproval(entry.getValue());
                }
                if (trueDeclinations != null) {
                    for (String user : trueDeclinations) {
                        userStates.get(user).handleTrueDeclination();
                    }
                }
            }
            else if (falseDeclinations != null) {
                if (trueClaim != null) {
                    userStates.get(trueClaim).handleTrueClaim();
                    for (String user : trueApprovals) {
                        userStates.get(user).handleTrueApproval();
                    }
                }
                for (Entry<String, IPLDObject<Review>> entry : falseDeclinations.entrySet()) {
                    userStates.get(entry.getKey()).addFalseDeclination(entry.getValue());
                }
            }
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

        private SealedDocument approvalsWon() {
            falseDeclinations = new HashMap<>();
            if (unsealing == null) {
                trueClaim = document.getMapped().expectUserState().getUser().getMultihash();
                trueApprovals = new HashSet<>();
                for (Entry<String, IPLDObject<Review>> entry : reviews.entrySet()) {
                    IPLDObject<Review> value = entry.getValue();
                    if (Boolean.FALSE.equals(value.getMapped().getApprove())) {
                        falseDeclinations.put(entry.getKey(), value);
                    }
                    else {
                        trueApprovals.add(entry.getKey());
                    }
                }
            }
            else {
                for (Entry<String, IPLDObject<Review>> entry : reviews.entrySet()) {
                    IPLDObject<Review> value = entry.getValue();
                    if (Boolean.FALSE.equals(value.getMapped().getApprove())) {
                        falseDeclinations.put(entry.getKey(), value);
                    }
                }
            }
            sealedDocument = new SealedDocument(document);
            return sealedDocument;
        }

        private SealedDocument declinationsWon() {
            falseClaim = document.getMapped().expectUserState().getUser().getMultihash();
            falseApprovals = new HashMap<>();
            if (unsealing == null) {
                trueDeclinations = new HashSet<>();
                for (Entry<String, IPLDObject<Review>> entry : reviews.entrySet()) {
                    IPLDObject<Review> value = entry.getValue();
                    if (Boolean.TRUE.equals(value.getMapped().getApprove())) {
                        falseApprovals.put(entry.getKey(), value);
                    }
                    else {
                        trueDeclinations.add(entry.getKey());
                    }
                }
            }
            else {
                for (Entry<String, IPLDObject<Review>> entry : reviews.entrySet()) {
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

    private static final Long SETTLEMENT_THRESHOLD = 1000L * 60 * 60 * 24 * 4;

    private boolean main;
    private long timestamp;

    private boolean validationMode = true;

    private Set<String> documentOwners = new TreeSet<>();
    private Set<String> invalidRequests = new TreeSet<>();
    private Map<String, SettlementData> eligibleSettlements = new HashMap<>();
    private Set<String> unbanned;
    private Map<String, String> grantedClaimUnbans;
    private Map<String, String> grantedApprovalUnbans;
    private Map<String, String> grantedDeclinationUnbans;

    private Map<String, String> invalidByReview;
    private Set<String> removedDocuments;
    private Set<String> documentOwnersMainValidation;
    private Map<String, SettlementData> eligibleMainValidation;

    SettlementController(boolean main, long timestamp) {
        this.main = main;
        this.timestamp = timestamp;
        if (main) {
            invalidByReview = new HashMap<>();
            removedDocuments = new TreeSet<>();
        }
    }

    public boolean checkRequest(SettlementRequest request, boolean forMainValidation) {
        IPLDObject<Document> document = request.getDocument();
        String documentMultihash = document.getMultihash();
        if (invalidRequests.contains(documentMultihash)) {
            return false;
        }
        Document doc = document.getMapped();
        long time = doc.getDate().getTime();
        if (timestamp - time < SETTLEMENT_THRESHOLD) {
            invalidRequests.add(documentMultihash);
            return false;
        }
        SettlementData data = addEligibleSettlement(document, false);
        data.requested = true;
        if (forMainValidation) {
            data.forMainValidation = true;
        }
        eligibleSettlements.put(documentMultihash, data);
        return true;
    }

    public boolean checkDocument(IPLDObject<Document> document, boolean needRequest) {
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
            if (invalidRequests.contains(documentMultihash)) {
                return false;
            }
            if (Boolean.FALSE.equals(review.getApprove())) {
                long time = review.getDate().getTime();
                if (timestamp - time < SETTLEMENT_THRESHOLD) {
                    if (main) {
                        if (validationMode) {
                            invalidByReview.put(document.getMultihash(), documentMultihash);
                        }
                        else if (removedDocuments.contains(document.getMultihash())) {
                            return false;
                        }
                        else {
                            invalidRequests.add(documentMultihash);
                            eligibleSettlements.remove(documentMultihash);
                            return false;
                        }
                    }
                    else {
                        invalidRequests.add(documentMultihash);
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
        return false;
    }

    public boolean checkRemovedDocument(IPLDObject<Document> removed, ModelState current) {
        String removedHash = removed.getMultihash();
        if (main) {
            removedDocuments.add(removedHash);
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
                data.removeReview(document.expectUserState().getUser().getMultihash());
            }
        }
        return true;
    }

    public void checkGrantedUnban(GrantedUnban unban, String userHash) {
        IPLDObject<Document> document = unban.getUnbanRequest().getMapped().getDocument();
        String documentHash = document.getMultihash();
        Document doc = document.getMapped();
        if (doc instanceof Review) {
            if (((Review) doc).getApprove()) {
                if (grantedApprovalUnbans == null) {
                    grantedApprovalUnbans = new HashMap<>();
                }
                grantedApprovalUnbans.put(documentHash, userHash);
            }
            else {
                if (grantedDeclinationUnbans == null) {
                    grantedDeclinationUnbans = new HashMap<>();
                }
                grantedDeclinationUnbans.put(documentHash, userHash);
            }
        }
        else {
            if (grantedClaimUnbans == null) {
                grantedClaimUnbans = new HashMap<>();
            }
            grantedClaimUnbans.put(documentHash, userHash);
        }
        if (unbanned == null) {
            unbanned = new HashSet<>();
        }
        unbanned.add(userHash);
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
                if (review.isUnseal()) {
                    data.unsealing = review.getDocument();
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
        if (main && validationMode) {
            return evaluateMainValidation(sealedDocuments);
        }
        boolean res = false;
        Set<String> toRemove = new HashSet<>();
        for (Entry<String, SettlementData> entry : eligibleSettlements.entrySet()) {
            SettlementData data = entry.getValue();
            if (data.requested && data.count()) {
                SealedDocument evaluated = data.evaluate();
                if (evaluated != null) {
                    res = true;
                    sealedDocuments.put(entry.getKey(), evaluated);
                    if (data.unsealing != null) {
                        // TODO: create unsealing tree and traverse it
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
        return res || unbanned != null;
    }

    private boolean evaluateMainValidation(Map<String, SealedDocument> sealedDocuments) {
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
                            SealedDocument evaluated = data.evaluate();
                            if (evaluated != null) {
                                eligibleMainValidation.put(documentHash, data);
                                documentOwnersMainValidation.add(data.documentOwner);
                                sealedDocuments.put(documentHash, evaluated);
                            }
                        }
                    }
                }
            }
        }
        return unbanned != null || eligibleMainValidation.size() > 0;
    }

    public boolean isAffected(String userHash) {
        if (unbanned != null && unbanned.contains(userHash)) {
            return true;
        }
        Map<String, SettlementData> eligible = main && validationMode ? eligibleMainValidation : eligibleSettlements;
        for (SettlementData data : eligible.values()) {
            if (data.isAffected(userHash)) {
                return true;
            }
        }
        return false;
    }

    public void update(Map<String, UserState> userStates) {
        Map<String, SettlementData> eligible = main && validationMode ? eligibleMainValidation : eligibleSettlements;
        for (SettlementData data : eligible.values()) {
            data.update(userStates);
        }
        if (unbanned != null) {
            if (grantedClaimUnbans != null) {
                for (Entry<String, String> entry : grantedClaimUnbans.entrySet()) {
                    userStates.get(entry.getValue()).removeFalseClaim(entry.getKey());
                }
            }
            if (grantedApprovalUnbans != null) {
                for (Entry<String, String> entry : grantedApprovalUnbans.entrySet()) {
                    userStates.get(entry.getValue()).removeFalseApproval(entry.getKey());
                }
            }
            if (grantedDeclinationUnbans != null) {
                for (Entry<String, String> entry : grantedDeclinationUnbans.entrySet()) {
                    userStates.get(entry.getValue()).removeFalseDeclination(entry.getKey());
                }
            }
        }
    }

    boolean enterMergeMode() {
        if (main && validationMode) {
            for (SettlementData data : eligibleMainValidation.values()) {
                data.reset();
            }
            validationMode = false;
            return true;
        }
        return false;
    }

}
