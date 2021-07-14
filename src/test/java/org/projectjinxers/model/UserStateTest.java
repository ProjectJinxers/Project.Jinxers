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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDReader.KeyProvider;
import org.projectjinxers.controller.TestIPLDObject;

/**
 * @author ProjectJinxers
 *
 */
class UserStateTest {

    @Test
    void testTrivialNonNegativeAndNonPositiveReview() {
        UserState userState = new UserState();
        assertFalse(userState.checkNonNegativeReview(""));
        assertFalse(userState.checkNonPositiveReview(""));
    }

    @Test
    void testNonNegativeAndNonPositiveReview() {
        Review review = new Review(null, null, null, null, null, null,
                new TestIPLDObject<Document>("hash", new Document()), false, null, null);
        UserState userState = new UserState();
        UserState updated = userState.updateLinks(
                map(Arrays.asList(new TestIPLDObject<>("review", review)), UserState.DOCUMENT_KEY_PROVIDER), null, null,
                null, null, null, null, null, null, null, null, null);
        assertTrue(updated.checkNonNegativeReview("hash"));
        assertTrue(updated.checkNonPositiveReview("hash"));
    }

    @Test
    void testNegativeReview() {
        IPLDObject<Document> reviewed = new TestIPLDObject<Document>("hash", new Document());
        Review review = new Review(null, null, null, null, null, null, reviewed, false, Boolean.TRUE, null);
        Review negative = new Review(null, null, null, null, null, null, reviewed, false, Boolean.FALSE, null);
        UserState userState = new UserState();
        userState.updateLinks(
                map(Arrays.asList(new TestIPLDObject<>("review", review), new TestIPLDObject<>("negative", negative)),
                        UserState.DOCUMENT_KEY_PROVIDER),
                null, null, null, null, null, null, null, null, null, null, null);
        assertFalse(userState.checkNonNegativeReview("hash"));
    }

    @Test
    void testPositiveReview() {
        IPLDObject<Document> reviewed = new TestIPLDObject<Document>("hash", new Document());
        Review review = new Review(null, null, null, null, null, null, reviewed, false, Boolean.FALSE, null);
        Review positive = new Review(null, null, null, null, null, null, reviewed, false, Boolean.TRUE, null);
        UserState userState = new UserState();
        userState.updateLinks(
                map(Arrays.asList(new TestIPLDObject<>("review", review), new TestIPLDObject<>("positive", positive)),
                        UserState.DOCUMENT_KEY_PROVIDER),
                null, null, null, null, null, null, null, null, null, null, null);
        assertFalse(userState.checkNonPositiveReview("hash"));
    }

    @Test
    void testNonNegativeReviewSandwich() {
        IPLDObject<Document> reviewed = new TestIPLDObject<Document>("hash", new Document());
        Review review = new Review(null, null, null, null, null, null, reviewed, false, Boolean.TRUE, null);
        Review negative = new Review(null, null, null, null, null, null, reviewed, false, Boolean.FALSE, null);
        Review restoring = new Review(null, null, null, null, null, null, reviewed, false, null, null);
        UserState userState = new UserState();
        UserState updated = userState.updateLinks(
                map(Arrays.asList(new TestIPLDObject<>("review", review), new TestIPLDObject<>("negative", negative),
                        new TestIPLDObject<>("restoring", restoring)), UserState.DOCUMENT_KEY_PROVIDER),
                null, null, null, null, null, null, null, null, null, null, null);
        assertTrue(updated.checkNonNegativeReview("hash"));
    }

    @Test
    void testNonPositiveReviewSandwich() {
        IPLDObject<Document> reviewed = new TestIPLDObject<Document>("hash", new Document());
        Review review = new Review(null, null, null, null, null, null, reviewed, false, Boolean.FALSE, null);
        Review positive = new Review(null, null, null, null, null, null, reviewed, false, Boolean.TRUE, null);
        Review restoring = new Review(null, null, null, null, null, null, reviewed, false, null, null);
        UserState userState = new UserState();
        UserState updated = userState.updateLinks(
                map(Arrays.asList(new TestIPLDObject<>("review", review), new TestIPLDObject<>("positive", positive),
                        new TestIPLDObject<>("restoring", restoring)), UserState.DOCUMENT_KEY_PROVIDER),
                null, null, null, null, null, null, null, null, null, null, null);
        assertTrue(updated.checkNonPositiveReview("hash"));
    }

    @Test
    void testSimpleLastActivity() {
        UserState userState = new UserState(new TestIPLDObject<>("user", new User()));
        IPLDObject<UserState> userStateObject = new IPLDObject<>(userState);
        IPLDObject<Document> document = new TestIPLDObject<>("",
                new Document(null, null, null, null, null, null, userStateObject));
        UserState updated = userState.updateLinks(map(Arrays.asList(document), UserState.DOCUMENT_KEY_PROVIDER), null,
                null, null, null, null, null, null, null, null, null, null);
        ModelState modelState = new ModelState();
        modelState.updateUserState(new IPLDObject<>(updated), null, null, null, null, null, null, 0, null);
        assertEquals(document.getMapped().getDate(), modelState.getLastActivityDate(document));
    }

    @Test
    void testLastActivityOneNextVersion() {
        UserState userState = new UserState(new TestIPLDObject<>("user", new User()));
        IPLDObject<UserState> userStateObject = new IPLDObject<>(userState);
        Document previousVersion = new Document(null, null, null, null, null, null, userStateObject);
        IPLDObject<Document> prev = new TestIPLDObject<>("hash", previousVersion);
        Date previousVersionDate = previousVersion.getDate();
        waitFor(1);
        Document document = previousVersion.update(null, null, null, null, null, null, prev, null);
        Date updatedDate = document.getDate();
        assertNotNull(updatedDate);
        assertNotEquals(previousVersionDate, updatedDate);
        UserState updated = userState.updateLinks(
                map(Arrays.asList(prev, new TestIPLDObject<>("updated", document)), UserState.DOCUMENT_KEY_PROVIDER),
                null, null, null, null, null, null, null, null, null, null, null);
        ModelState modelState = new ModelState();
        modelState.updateUserState(new IPLDObject<>(updated), null, null, null, null, null, null, 0, null);
        assertNull(modelState.getLastActivityDate(prev));
    }

    @Test
    void testLastActivityWithReviews() {
        UserState userState = new UserState(new TestIPLDObject<>("user", new User()));
        IPLDObject<UserState> userStateObject = new IPLDObject<>(userState);
        Document reviewed = new Document(null, null, null, null, null, null, userStateObject);
        IPLDObject<Document> reviewedObject = new TestIPLDObject<>("reviewed", reviewed);
        waitFor(1);
        Review review = new Review(null, null, null, null, null, null, reviewedObject, false, null, userStateObject);
        IPLDObject<Document> reviewObject = new TestIPLDObject<>("review", review);
        waitFor(1);
        Review ofReview = new Review(null, null, null, null, null, null, reviewObject, false, null, userStateObject);
        IPLDObject<Document> reviewOfReview = new TestIPLDObject<>("ofReview", ofReview);
        Date expected = ofReview.getDate();
        waitFor(1);
        UserState updated = userState.updateLinks(
                map(Arrays.asList(reviewedObject, reviewOfReview), UserState.DOCUMENT_KEY_PROVIDER), null, null, null,
                null, null, null, null, null, null, null, null);
        ModelState modelState = new ModelState();
        modelState.updateUserState(new IPLDObject<>(updated), null, null, null, null,
                Map.of("reviewed", new String[] { "review" }, "review", new String[] { "ofReview" }), null, 0, null);
        assertEquals(expected, modelState.getLastActivityDate(reviewedObject));
    }

    @Test
    void testLastActivityComplex() {
        UserState userState = new UserState(new TestIPLDObject<>("user", new User()));
        IPLDObject<UserState> userStateObject = new IPLDObject<>(userState);
        Document unrelatedPrev = new Document(null, null, null, null, null, null, userStateObject);
        IPLDObject<Document> unrelated = new TestIPLDObject<>("unrelated", unrelatedPrev);
        waitFor(1);
        Document previousVersion = new Document(null, null, null, null, null, null, userStateObject);
        IPLDObject<Document> prev = new TestIPLDObject<>("hash", previousVersion);
        Date previousVersionDate = previousVersion.getDate();
        waitFor(1);
        IPLDObject<Document> unrelatedReviewed = new TestIPLDObject<>("unrelatedReviewed", new Document());
        Review unrelatedReview = new Review(null, null, null, null, null, null, unrelatedReviewed, false, null,
                userStateObject);
        IPLDObject<Document> unrelatedReviewObject = new TestIPLDObject<>("unrelatedReview", unrelatedReview);
        waitFor(1);
        Document firstUpdate = previousVersion.update(null, null, null, null, null, null, prev, null);
        Date updatedDate = firstUpdate.getDate();
        assertNotNull(updatedDate);
        assertNotEquals(previousVersionDate, updatedDate);
        IPLDObject<Document> first = new TestIPLDObject<>("first", firstUpdate);
        waitFor(1);
        Document secondUpdate = firstUpdate.update(null, null, null, null, null, null, first, userStateObject);
        Date secondUpdatedDate = secondUpdate.getDate();
        assertNotNull(secondUpdatedDate);
        assertNotEquals(updatedDate, secondUpdatedDate);
        IPLDObject<Document> current = new TestIPLDObject<>("updated", secondUpdate);
        waitFor(1);
        Review review = new Review(null, null, null, null, null, null, current, false, null, userStateObject);
        IPLDObject<Document> reviewObject = new TestIPLDObject<>("review", review);
        waitFor(1);
        Review intermediate = new Review(null, null, null, null, null, null, reviewObject, false, null,
                userStateObject);
        IPLDObject<Document> intermediateObject = new TestIPLDObject<>("intermediate", intermediate);
        waitFor(1);
        Review ofReview = new Review(null, null, null, null, null, null, intermediateObject, false, null,
                userStateObject);
        Date expected = ofReview.getDate();
        IPLDObject<Document> reviewOfReview = new TestIPLDObject<>("ofReview", ofReview);
        waitFor(1);
        Document unrelatedUpdate = unrelatedPrev.update(null, null, null, null, null, null, unrelated, null);
        Review updatedUnrelatedReview = unrelatedReview.update(null, null, null, null, null, null, false, null,
                unrelatedReviewObject, null);
        IPLDObject<Document> updatedUnrelatedReviewObject = new TestIPLDObject<>("updatedUnrelatedReview",
                updatedUnrelatedReview);
        waitFor(1);
        Review updatedUnrelatedReviewUpdate = updatedUnrelatedReview.update(null, null, null, null, null, null, false,
                null, updatedUnrelatedReviewObject, null);
        Review ofUnrelatedReview = new Review(null, null, null, null, null, null, unrelatedReviewObject, false, null,
                userStateObject);
        IPLDObject<Document> reviewOfUnrelatedReview = new TestIPLDObject<Document>("ofUnrelatedReview",
                ofUnrelatedReview);
        Review hundo = new Review(null, null, null, null, null, null, reviewOfUnrelatedReview, false, null,
                userStateObject);
        UserState updated = userState.updateLinks(
                map(Arrays.asList(unrelated, prev, unrelatedReviewObject, first, current, reviewOfReview,
                        new TestIPLDObject<>("unrelatedUpdate", unrelatedUpdate), updatedUnrelatedReviewObject,
                        new TestIPLDObject<>("done", updatedUnrelatedReviewUpdate),
                        new TestIPLDObject<>("hundo", hundo)), UserState.DOCUMENT_KEY_PROVIDER),
                null, null, null, null, null, null, null, null, null, null, null);
        ModelState modelState = new ModelState();
        modelState.updateUserState(new IPLDObject<>(updated), null, null, null, null,
                Map.of("unrelatedReviewed", new String[] { "unrelatedReview", "updatedUnrelatedReview", "done" },
                        "updated", new String[] { "review" }, "review", new String[] { "intermediate" }, "intermediate",
                        new String[] { "ofReview" }, "unrelatedReview", new String[] { "ofUnrelatedReview" },
                        "ofUnrelatedReview", new String[] { "hundo" }),
                null, 0, null);
        assertEquals(expected, modelState.getLastActivityDate(current));
    }

    private <T extends IPLDSerializable> Map<String, IPLDObject<T>> map(Collection<IPLDObject<T>> collection,
            KeyProvider<T> keyProvider) {
        Map<String, IPLDObject<T>> res = new LinkedHashMap<>();
        for (IPLDObject<T> object : collection) {
            res.put(keyProvider.getKey(object), object);
        }
        return res;
    }

    private void waitFor(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException e) {

        }
    }

}
