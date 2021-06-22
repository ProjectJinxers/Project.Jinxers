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

        private IPLDObject<Document> document;
        private Map<String, UserState> userStates = new HashMap<>();
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
            userStates.put(reviewerHash, reviewer);
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

    private long timestamp;

    private Set<String> invalidRequests = new TreeSet<>();
    private Map<String, SettlementData> eligibleSettlements = new HashMap<>();

    SettlementController() {
        this.timestamp = System.currentTimeMillis() + ValidationContext.TIMESTAMP_TOLERANCE;
    }

    public boolean checkRequest(SettlementRequest request, UserState userState) {
        IPLDObject<Document> document = request.getDocument();
        String documentMultihash = document.getMultihash();
        if (invalidRequests.contains(documentMultihash)) {
            return false;
        }
        long time = document.getMapped().getDate().getTime();
        if (timestamp - time < SETTLEMENT_THRESHOLD) {
            invalidRequests.add(documentMultihash);
            return false;
        }
        SettlementData data = addEligibleSettlement(document);
        data.userStates.put(userState.getUser().getMultihash(), userState);
        eligibleSettlements.put(document.getMultihash(), data);
        return true;
    }

    public boolean checkReview(IPLDObject<Review> reviewObject) {
        Review review = reviewObject.getMapped();
        IPLDObject<Document> document = review.getDocument();
        String documentMultihash = document.getMultihash();
        if (invalidRequests.contains(documentMultihash)) {
            return false;
        }
        if (Boolean.FALSE.equals(review.getApprove())) {
            long time = review.getDate().getTime();
            if (timestamp - time < SETTLEMENT_THRESHOLD) {
                invalidRequests.add(documentMultihash);
                eligibleSettlements.remove(documentMultihash);
                return false;
            }
        }
        SettlementData data = addEligibleSettlement(document);
        data.addReview(reviewObject);
        return true;
    }

    private SettlementData addEligibleSettlement(IPLDObject<Document> document) {
        String documentMultihash = document.getMultihash();
        SettlementData data = eligibleSettlements.get(documentMultihash);
        if (data == null) {
            data = new SettlementData();
            data.document = document;
            UserState owner = document.getMapped().expectUserState();
            data.userStates.put(owner.getUser().getMultihash(), owner);
            eligibleSettlements.put(documentMultihash, data);
        }
        return data;
    }

    public boolean evaluate() {
        boolean res = false;
        Set<String> toRemove = new HashSet<>();
        for (Entry<String, SettlementData> entry : eligibleSettlements.entrySet()) {
            SettlementData data = entry.getValue();
            if (data.count() && data.evaluate()) {
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

    public boolean isAffected(String userHash) {
        for (SettlementData data : eligibleSettlements.values()) {
            if (data.isAffected(userHash)) {
                return true;
            }
        }
        return false;
    }

    public void update(Map<String, UserState> userStates) {
        for (SettlementData data : eligibleSettlements.values()) {
            data.update(userStates);
        }
    }

}
