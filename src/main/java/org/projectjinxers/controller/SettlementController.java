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
import org.projectjinxers.model.Review;
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
        private Map<String, IPLDObject<Review>> reviews = new HashMap<>();

        private int approveCount;
        private int declineCount;
        private int neutralCount;

        private String falseClaim;
        private Map<String, IPLDObject<Review>> falseApprovals;
        private Map<String, IPLDObject<Review>> falseDeclinations;

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

        boolean evaluate() {
            if (falseClaim == null && falseApprovals == null && falseDeclinations == null) {
                int totalCount = approveCount + declineCount + neutralCount;
                if (totalCount >= MIN_TOTAL_COUNT) {
                    if (approveCount > declineCount) {
                        if (approveCount * 1.0 / declineCount >= MIN_MARGIN) {
                            approvalsWon();
                            return true;
                        }
                    }
                    else if (declineCount > approveCount) {
                        if (declineCount * 1.0 / approveCount >= MIN_MARGIN) {
                            declinationsWon();
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        boolean isAffected(String userHash) {
            return userHash.equals(falseClaim) || falseApprovals != null && falseApprovals.containsKey(userHash)
                    || falseDeclinations != null && falseDeclinations.containsKey(userHash);
        }

        void update(Map<String, UserState> userStates) {
            if (falseClaim != null) {
                userStates.get(falseClaim).addFalseClaim(document);
            }
            if (falseApprovals != null) {
                for (Entry<String, IPLDObject<Review>> entry : falseApprovals.entrySet()) {
                    String key = entry.getKey();
                    userStates.get(key).addFalseApproval(entry.getValue());
                }
            }
            else if (falseDeclinations != null) {
                for (Entry<String, IPLDObject<Review>> entry : falseDeclinations.entrySet()) {
                    String key = entry.getKey();
                    userStates.get(key).addFalseDeclination(entry.getValue());
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
        }

        private void approvalsWon() {
            falseDeclinations = new HashMap<>();
            for (Entry<String, IPLDObject<Review>> entry : reviews.entrySet()) {
                IPLDObject<Review> value = entry.getValue();
                if (Boolean.FALSE.equals(value.getMapped().getApprove())) {
                    falseDeclinations.put(entry.getKey(), value);
                }
            }
        }

        private void declinationsWon() {
            falseClaim = document.getMapped().expectUserState().getUser().getMultihash();
            falseApprovals = new HashMap<>();
            for (Entry<String, IPLDObject<Review>> entry : reviews.entrySet()) {
                IPLDObject<Review> value = entry.getValue();
                if (Boolean.TRUE.equals(value.getMapped().getApprove())) {
                    falseApprovals.put(entry.getKey(), value);
                }
            }
        }

    }

    private static final Long SETTLEMENT_THRESHOLD = 1000L * 60 * 60 * 24 * 4;

    private boolean main;
    private long timestamp;

    private boolean validationMode = true;

    private Set<String> documentOwners = new TreeSet<>();
    private Set<String> invalidRequests = new TreeSet<>();
    private Map<String, SettlementData> eligibleSettlements = new HashMap<>();

    private Map<String, String> invalidByReview;
    private Set<String> removedDocuments;
    private Set<String> documentOwnersMainValidation;
    private Map<String, SettlementData> eligibleMainValidation;

    SettlementController(boolean main) {
        this.main = main;
        this.timestamp = System.currentTimeMillis() + ValidationContext.TIMESTAMP_TOLERANCE;
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

    public boolean checkReview(IPLDObject<Document> document, boolean needRequest) {
        Document doc = document.getMapped();
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

    public boolean checkRemovedDocument(IPLDObject<Document> removed) {
        String removedHash = removed.getMultihash();
        if (main) {
            removedDocuments.add(removedHash);
        }
        if (invalidRequests.add(removedHash)) {
            eligibleSettlements.remove(removedHash);
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
        return false;
    }

    private SettlementData addEligibleSettlement(IPLDObject<Document> document, boolean needRequest) {
        String documentMultihash = document.getMultihash();
        SettlementData data = eligibleSettlements.get(documentMultihash);
        if (data == null) {
            if (needRequest) {
                return null;
            }
            data = new SettlementData();
            String owner = document.getMapped().expectUserState().getUser().getMultihash();
            data.documentOwner = owner;
            data.document = document;
            documentOwners.add(owner);
            eligibleSettlements.put(documentMultihash, data);
        }
        else if (needRequest && !data.requested) {
            return null;
        }
        return data;
    }

    public boolean isDocumentOwner(String userHash) {
        if (main && validationMode && documentOwnersMainValidation != null) {
            return documentOwnersMainValidation.contains(userHash);
        }
        return documentOwners.contains(userHash);
    }

    public boolean evaluate() {
        if (main && validationMode) {
            return evaluateMainValidation();
        }
        boolean res = false;
        Set<String> toRemove = new HashSet<>();
        for (Entry<String, SettlementData> entry : eligibleSettlements.entrySet()) {
            SettlementData data = entry.getValue();
            if (data.requested && data.count() && data.evaluate()) {
                res = true;
            }
            else {
                toRemove.add(entry.getKey());
            }
        }
        for (String key : toRemove) {
            eligibleSettlements.remove(key);
        }
        return res;
    }

    private boolean evaluateMainValidation() {
        if (eligibleMainValidation == null) {
            eligibleMainValidation = new HashMap<>();
            documentOwnersMainValidation = new TreeSet<>();
            Set<String> invalid = new TreeSet<>(invalidByReview.values());
            for (Entry<String, SettlementData> entry : eligibleSettlements.entrySet()) {
                SettlementData data = entry.getValue();
                if (data.requested && data.forMainValidation) {
                    String documentHash = entry.getKey();
                    if (!invalid.contains(documentHash)) {
                        if (data.count() && data.evaluate()) {
                            eligibleMainValidation.put(documentHash, data);
                            documentOwnersMainValidation.add(data.documentOwner);
                        }
                    }
                }
            }
        }
        return eligibleMainValidation.size() > 0;
    }

    public boolean isAffected(String userHash) {
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
