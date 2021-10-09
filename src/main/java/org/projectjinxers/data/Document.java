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
package org.projectjinxers.data;

import static org.projectjinxers.util.ModelUtility.loadObjects;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.projectjinxers.account.Signer;
import org.projectjinxers.config.Config;
import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDObject.ProgressTask;
import org.projectjinxers.controller.ModelController;
import org.projectjinxers.model.DocumentContents;
import org.projectjinxers.model.DocumentRemoval;
import org.projectjinxers.model.IPLDSerializable;
import org.projectjinxers.model.LoaderFactory;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.UserState;
import org.projectjinxers.util.ModelUtility.CompletionHandler;

/**
 * @author ProjectJinxers
 *
 */
public class Document extends ProgressObserver {

    public enum Kind {

        NEW, STANDALONE, LOADED

    }

    public static class ReviewInfo {

        private boolean available;
        private int totalCount;
        private int approvalsCount;
        private int declinationsCount;
        private boolean loading;
        private boolean sealed;
        private String statusMessage;

        public boolean isAvailable() {
            return available;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public int getApprovalsCount() {
            return approvalsCount;
        }

        public int getDeclinationsCount() {
            return declinationsCount;
        }

        public boolean isLoading() {
            return loading;
        }

        public boolean isSealed() {
            return sealed;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

    }

    private transient Group group;
    private transient User user;
    private String multihash;
    private transient String importURL;
    private transient IPLDObject<org.projectjinxers.model.Document> documentObject;
    private transient IPLDObject<ModelState> modelStateObject;
    private Kind kind;
    private transient boolean replaced;

    private transient ReviewInfo reviewInfo;
    private transient Map<String, Document> reviews;
    private transient CompletionHandler reviewsHandler;

    private transient boolean loading;
    private transient boolean saveCalled;
    private transient boolean removeCalled;
    private transient boolean removed;

    public Document(Group group, IPLDObject<org.projectjinxers.model.Document> documentObject, boolean replaced) {
        super(true);
        this.group = group;
        this.multihash = documentObject.getMultihash();
        this.documentObject = documentObject;
        this.kind = Kind.LOADED;
        this.replaced = replaced;
    }

    public Document(Group group, String multihash) {
        super(true);
        this.group = group;
        this.multihash = multihash;
        if (group != null) {
            group.addStandaloneDocument(this);
        }
        this.kind = Kind.STANDALONE;
    }

    public Document(Group group, User user, IPLDObject<org.projectjinxers.model.Document> documentObject,
            String importURL, IPLDObject<ModelState> modelStateObject) {
        super(true);
        this.group = group;
        this.user = user;
        this.documentObject = documentObject;
        this.importURL = importURL;
        this.modelStateObject = modelStateObject;
        this.kind = Kind.NEW;
    }

    public Group getGroup() {
        return group;
    }

    public User getUser() {
        return user;
    }

    public String getMultihash() {
        if (multihash == null && documentObject != null) {
            multihash = documentObject.getMultihash();
        }
        return multihash;
    }

    public String getImportURL() {
        return importURL;
    }

    public IPLDObject<org.projectjinxers.model.Document> getDocumentObject() {
        return documentObject;
    }

    public IPLDObject<org.projectjinxers.model.Document> getOrLoadDocumentObject() {
        if ((documentObject == null || !documentObject.isMapped()) && !loading && multihash != null) {
            loading = true;
            saveCalled = false;
            startOperation(() -> {
                getOrLoadDocumentObject();
                return true;
            });
            startedTask(ProgressTask.LOAD, -1);
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    if (documentObject == null) {
                        ModelController controller;
                        if (group == null) {
                            controller = ModelController.getModelController(Config.getSharedInstance());
                        }
                        else {
                            controller = group.getController();
                        }
                        IPLDObject<org.projectjinxers.model.Document> documentObject = new IPLDObject<>(multihash,
                                LoaderFactory.DOCUMENT.createLoader(), controller.getContext(), null);
                        this.documentObject = documentObject;
                    }
                    org.projectjinxers.model.Document mapped = documentObject.getMapped();
                    if (mapped == null) {
                        failedTask(ProgressTask.LOAD, "Failed to load the document.", null);
                    }
                    else {
                        if (group != null) {
                            String userHash = mapped.expectUserState().getUser().getMultihash();
                            ModelController controller = group.getOrCreateController();
                            if (group.isInitializingController()) {
                                failedTask(ProgressTask.LOAD,
                                        "Please wait until the model controller for the group has been initialized.",
                                        null);
                                return;
                            }
                            this.modelStateObject = controller.getCurrentValidatedState();
                            IPLDObject<UserState> userStateObject = modelStateObject == null ? null
                                    : modelStateObject.getMapped().getUserState(userHash);
                            if (userStateObject == null) {
                                failedTask(ProgressTask.LOAD, "The document is not contained in the selected group.",
                                        null);
                                this.group = null;
                                return;
                            }
                            String firstVersionHash = mapped.getFirstVersionHash();
                            if (firstVersionHash == null) {
                                firstVersionHash = multihash;
                            }
                            UserState userState = userStateObject.getMapped();
                            if (userState.isRemoved(firstVersionHash)) {
                                removed = true;
                            }
                            IPLDObject<org.projectjinxers.model.Document> latest = userState
                                    .getDocumentByFirstVersionHash(firstVersionHash);
                            if (!multihash.equals(latest.getMultihash())) {
                                replaced = true;
                            }
                        }
                        finishedTask(ProgressTask.LOAD);
                    }
                }
                catch (Exception e) {
                    failedTask(ProgressTask.LOAD, "Failed to load the document.", e);
                }
                loading = false;
            }).start();
        }
        return documentObject;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isReplaced() {
        return replaced;
    }

    public ReviewInfo getOrLoadReviewInfo(CompletionHandler completionHandler) {
        this.reviewsHandler = completionHandler;
        if (reviewInfo == null) {
            reviewInfo = new ReviewInfo();
            reviewInfo.loading = true;
            updateReviewsInfo();
        }
        return reviewInfo;
    }

    public Map<String, Document> getReviews() {
        return reviews;
    }

    public boolean isFalseClaim() {
        UserState userState = documentObject.getMapped().expectUserState();
        String userHash = userState.getUser().getMultihash();
        IPLDObject<UserState> userStateObject = group.getController().getCurrentValidatedState().getMapped()
                .expectUserState(userHash);
        return userStateObject.getMapped().isFalseClaim(multihash);
    }

    public boolean isLoading() {
        return loading;
    }

    public boolean isSaveCalled() {
        return saveCalled;
    }

    public boolean isRemoved() {
        return removed;
    }

    public boolean save(ModelController controller, Signer signer) {
        saveCalled = true;
        documentObject.setProgressListener(this);
        IPLDObject<ModelState> currentValidatedState = controller.getCurrentValidatedState();
        if (modelStateObject != currentValidatedState
                && !documentObject.getMapped().updateModelState(currentValidatedState.getMapped())) {
            failedTask(ProgressTask.SAVE,
                    "The reviewed document has just been sealed. You can only add truth inversion reviews now.", null);
            return false;
        }
        startOperation(() -> save(controller, signer));
        new Thread(() -> {
            try {
                controller.saveDocument(documentObject, signer);
                if (user.getMultihash() == null) {
                    user.didSaveUserObject();
                }
            }
            catch (IOException e) {

            }
        }).start();
        return true;
    }

    public void delete(User user, Signer signer) {
        DocumentRemoval removal = new DocumentRemoval(documentObject);
        IPLDObject<DocumentRemoval> removalObject = new IPLDObject<>(removal);
        removalObject.setProgressListener(this);
        ModelController controller = group.getController();
        requestRemoval(controller, removalObject, user, signer);
    }

    public void groupUpdated(Group group, IPLDObject<ModelState> valid) {
        if (this.group == group) {
            this.modelStateObject = valid;
            if (documentObject != null && documentObject.isMapped()) {
                org.projectjinxers.model.Document mapped = documentObject.getMapped();
                IPLDObject<UserState> userStateObject = mapped.getUserState();
                if (userStateObject != null) {
                    String userHash = userStateObject.getMapped().getUser().getMultihash();
                    IPLDObject<UserState> userState = valid.getMapped().expectUserState(userHash);
                    if (userState != null) {
                        String firstVersionHash = mapped.getFirstVersionHash();
                        if (firstVersionHash == null) {
                            firstVersionHash = multihash;
                        }
                        if (userState.getMapped().isRemoved(firstVersionHash)) {
                            removed = true;
                        }
                    }
                }
                updateReviewsInfo();
            }
        }
    }

    @Override
    public boolean isDestroying() {
        return removeCalled;
    }

    @Override
    public String getStatusMessagePrefix() {
        if (removed) {
            return "Removed";
        }
        if (removeCalled) {
            return "Removal requested";
        }
        if (saveCalled && getMultihash() != null) {
            return "Saved as " + multihash;
        }
        return null;
    }

    public boolean isSettlementRequested() {
        if (reviewInfo != null && reviewInfo.sealed) {
            return true;
        }
        if (group != null && multihash != null) {
            ModelController controller = group.getController();
            if (controller != null && controller.isInitialized()) {
                IPLDObject<ModelState> currentValidatedState = controller.getCurrentValidatedState();
                if (currentValidatedState != null) {
                    return currentValidatedState.getMapped().getSettlementRequest(multihash) != null;
                }
            }
        }
        return false;
    }

    public boolean isSealed() {
        if (reviewInfo != null && reviewInfo.sealed) {
            return true;
        }
        if (group != null && multihash != null) {
            ModelController controller = group.getController();
            if (controller != null && controller.isInitialized()) {
                IPLDObject<ModelState> currentValidatedState = controller.getCurrentValidatedState();
                if (currentValidatedState != null) {
                    return currentValidatedState.getMapped().isSealedDocument(multihash);
                }
            }
        }
        return false;
    }

    public boolean conformsToFilterQuery(String filterQuery, ProgressChangeListener listener) {
        if (documentObject != null && documentObject.isMapped()) {
            org.projectjinxers.model.Document document = documentObject.getMapped();
            String query = filterQuery.toLowerCase();
            String title = document.getTitle();
            if (title != null && title.toLowerCase().contains(query)) {
                return true;
            }
            String subtitle = document.getSubtitle();
            if (subtitle != null && subtitle.toLowerCase().contains(query)) {
                return true;
            }
            String version = document.getVersion();
            if (version != null && version.toLowerCase().contains(query)) {
                return true;
            }
            String tags = document.getTags();
            if (tags != null && tags.toLowerCase().contains(query)) {
                return true;
            }
            String source = document.getSource();
            if (source != null && source.toLowerCase().contains(query)) {
                return true;
            }
            IPLDObject<DocumentContents> contentsObject = document.getContents();
            if (contentsObject != null && contentsObject.isMapped()) {
                DocumentContents contents = contentsObject.getMapped();
                String abstr = contents.getAbstract();
                if (abstr != null && abstr.toLowerCase().contains(query)) {
                    return true;
                }
                String text = contents.getContents();
                if (text != null && text.toLowerCase().contains(query)) {
                    return true;
                }
            }
            return false;
        }
        setProgressChangeListener(listener);
        getOrLoadDocumentObject();
        return false;
    }

    private void updateReviewsInfo() {
        IPLDObject<ModelState> modelStateObject = this.modelStateObject;
        if (modelStateObject == null) {
            ModelController controller = group == null ? null : group.getController();
            modelStateObject = controller == null ? null : controller.getCurrentValidatedState();
        }
        if (group == null || modelStateObject == null) {
            reviewInfo.available = false;
            if (group == null) {
                reviewInfo.statusMessage = "Please associate the document to a group, if you want to see the reviews summary.";
            }
            else {
                reviewInfo.statusMessage = "Please wait until a model state has been validated.";
            }
            reviewInfo.loading = false;
            if (reviewsHandler != null) {
                reviewsHandler.completed(0);
            }
        }
        else {
            reviewInfo.available = true;
            ModelState modelState = modelStateObject.getMapped();
            String[] reviewTableEntries = modelState.getReviewTableEntries(multihash);
            reviewInfo.statusMessage = null;
            if (reviewTableEntries == null) {
                reviewInfo.totalCount = 0;
                reviewInfo.approvalsCount = 0;
                reviewInfo.declinationsCount = 0;
                reviewInfo.loading = false;
                if (reviewsHandler != null) {
                    reviewsHandler.completed(0);
                }
            }
            else {
                if (reviews == null) {
                    reviews = new HashMap<>();
                }
                IPLDContext context = group.getController().getContext();
                synchronized (reviews) {
                    for (String reviewHash : reviewTableEntries) {
                        if (!reviews.containsKey(reviewHash)) {
                            reviews.put(reviewHash, new Document(group,
                                    new IPLDObject<>(reviewHash, LoaderFactory.DOCUMENT.createLoader(), context, null),
                                    false));
                        }
                    }
                }
                boolean sealed = modelState.isSealedDocument(multihash);
                if (sealed) {
                    reviewInfo.sealed = true;
                }
                loadReviews(sealed, false, 0);
            }
        }
    }

    private void loadReviews(boolean sealed, boolean completeFailure, int attemptCountAfterLastSuccess) {
        if (completeFailure && attemptCountAfterLastSuccess == 3) {
            if (sealed) {
                reviewInfo.statusMessage = "Incomplete evaluation - " + reviews.size()
                        + " total review table entries (might include older versions)";
            }
            else {
                reviewInfo.statusMessage = "Incomplete - gave up after 3 complete loading failures";
            }
            reviewInfo.loading = false;
            if (reviewsHandler != null) {
                reviewsHandler.completed(0);
            }
        }
        else {
            reviewInfo.totalCount = reviews.size();
            reviewInfo.approvalsCount = 0;
            reviewInfo.declinationsCount = 0;
            Collection<IPLDObject<? extends IPLDSerializable>> toLoad = new ArrayList<>();
            Collection<String> obsoleteVersionHashes = new ArrayList<>();
            synchronized (reviews) {
                for (Document reviewDocument : reviews.values()) {
                    if (reviewDocument.documentObject.isMapped()) {
                        Review review = (Review) reviewDocument.documentObject.getMapped();
                        String previousVersionHash = review.getPreviousVersionHash();
                        if (previousVersionHash != null && reviews.containsKey(previousVersionHash)) {
                            obsoleteVersionHashes.add(previousVersionHash);
                            reviewInfo.totalCount--;
                        }
                        Boolean approve = review.getApprove();
                        if (approve != null) {
                            if (approve) {
                                reviewInfo.approvalsCount++;
                            }
                            else {
                                reviewInfo.declinationsCount++;
                            }
                        }
                    }
                    else {
                        toLoad.add(reviewDocument.documentObject);
                    }
                }
                if (obsoleteVersionHashes != null) {
                    for (String hash : obsoleteVersionHashes) {
                        reviews.remove(hash);
                    }
                }
            }
            reviewInfo.statusMessage = null;
            if (toLoad.size() > 0) {
                loadObjects(toLoad, (successCount) -> {
                    if (reviewsHandler != null) {
                        reviewsHandler.completed(successCount);
                    }
                    loadReviews(sealed, successCount == 0, successCount == 0 ? attemptCountAfterLastSuccess + 1 : 0);
                });
            }
            else {
                reviewInfo.loading = false;
                if (reviewsHandler != null) {
                    reviewsHandler.completed(0);
                }
            }
        }
    }

    private boolean requestRemoval(ModelController controller, IPLDObject<DocumentRemoval> removal, User user,
            Signer signer) {
        removeCalled = true;
        IPLDObject<ModelState> currentValidatedState = controller.getCurrentValidatedState();
        if (modelStateObject != currentValidatedState) {
            ModelState validState = currentValidatedState.getMapped();
            if (validState.expectUserState(user.getMultihash()).getMapped().getDocument(multihash) == null) {
                failedTask(ProgressTask.SAVE,
                        "You are not the owner of the document, anymore. If you want to delete it,"
                                + "you have to request ownership.",
                        null);
                removeCalled = false;
                return false;
            }
        }
        startOperation(() -> requestRemoval(controller, removal, user, signer));
        new Thread(() -> {
            try {
                controller.requestDocumentRemoval(removal, signer);
            }
            catch (IOException e) {

            }
            finally {

            }
        }).start();
        return true;
    }

}
