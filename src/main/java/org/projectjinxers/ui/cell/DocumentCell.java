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
package org.projectjinxers.ui.cell;

import static org.projectjinxers.ui.util.ModelLoadingUtility.loadObject;
import static org.projectjinxers.ui.util.ModelLoadingUtility.loadObjects;

import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.projectjinxers.controller.IPLDContext;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.ModelController;
import org.projectjinxers.data.Document;
import org.projectjinxers.data.Group;
import org.projectjinxers.data.User;
import org.projectjinxers.model.LoaderFactory;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.SealedDocument;
import org.projectjinxers.model.UserState;
import org.projectjinxers.ui.main.MainPresenter;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * @author ProjectJinxers
 *
 */
public class DocumentCell extends AbstractListCell<Document> implements Initializable {

    private static final long ONE_MINUTE = 1000L * 60;
    private static final long ONE_HOUR = ONE_MINUTE * 60;
    private static final long ONE_DAY = ONE_HOUR * 24;
    private static final long ONE_WEEK = ONE_DAY * 7;
    private static final long ONE_YEAR = ONE_DAY * 365;

    private static final Image APPROVE_IMAGE = new Image("images/approve.png");
    private static final Image DECLINE_IMAGE = new Image("images/decline.png");
    private static final Image NEUTRAL_IMAGE = new Image("images/totalReviews.png");
    private static final Image TRUTH_INVERSION_IMAGE = new Image("images/truthInversion.png");
    private static final Image SEALED_TRUTH_IMAGE = new Image("images/sealedTruth.png");
    private static final Image SEALED_LIE_IMAGE = new Image("images/sealedLie.png");
    private static final Image UNKNOWN_SEAL_STATE = new Image("images/questionmark.png");

    MainPresenter mainPresenter;

    @FXML
    private ImageView approvalImage;
    @FXML
    private ImageView totalReviewsImage;
    @FXML
    private ImageView approvalsImage;
    @FXML
    private ImageView declinationsImage;
    @FXML
    private ImageView truthImage;
    @FXML
    private ObjectStatusView objectStatusView;

    private StringProperty age = new SimpleStringProperty();
    private StringProperty time = new SimpleStringProperty();
    private StringProperty author = new SimpleStringProperty();
    private StringProperty authorDetails = new SimpleStringProperty();
    private StringProperty reviewed = new SimpleStringProperty();
    private StringProperty reviewedDetails = new SimpleStringProperty();
    private StringProperty title = new SimpleStringProperty();
    private StringProperty subtitle = new SimpleStringProperty();
    private StringProperty version = new SimpleStringProperty();
    private StringProperty tags = new SimpleStringProperty();
    private StringProperty source = new SimpleStringProperty();
    private StringProperty totalReviews = new SimpleStringProperty();
    private StringProperty approvals = new SimpleStringProperty();
    private StringProperty declinations = new SimpleStringProperty();

    private Tooltip approvalStateTooltip;
    private Tooltip totalReviewsTooltip;
    private Tooltip truthStateTooltip;

    public DocumentCell(MainPresenter mainPresenter) {
        super("DocumentCell.fxml");
        this.mainPresenter = mainPresenter;
    }

    public StringProperty ageProperty() {
        return age;
    }

    public String getAge() {
        return age.get();
    }

    public StringProperty timeProperty() {
        return time;
    }

    public String getTime() {
        return time.get();
    }

    public StringProperty authorProperty() {
        return author;
    }

    public String getAuthor() {
        return author.get();
    }

    public StringProperty authorDetailsProperty() {
        return authorDetails;
    }

    public String getAuthorDetails() {
        return authorDetails.get();
    }

    public StringProperty reviewedProperty() {
        return reviewed;
    }

    public String getReviewed() {
        return reviewed.get();
    }

    public StringProperty reviewedDetailsProperty() {
        return reviewedDetails;
    }

    public String getReviewedDetails() {
        return reviewedDetails.get();
    }

    public StringProperty titleProperty() {
        return title;
    }

    public String getTitle() {
        return title.get();
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public String getSubtitle() {
        return subtitle.get();
    }

    public StringProperty versionProperty() {
        return version;
    }

    public String getVersion() {
        return version.get();
    }

    public StringProperty tagsProperty() {
        return tags;
    }

    public String getTags() {
        return tags.get();
    }

    public StringProperty sourceProperty() {
        return source;
    }

    public String getSource() {
        return source.get();
    }

    public StringProperty totalReviewsProperty() {
        return totalReviews;
    }

    public String getTotalReviews() {
        return totalReviews.get();
    }

    public StringProperty approvalsProperty() {
        return approvals;
    }

    public String getApprovals() {
        return approvals.get();
    }

    public StringProperty declinationsProperty() {
        return declinations;
    }

    public String getDeclinations() {
        return declinations.get();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Tooltip.install(approvalsImage, new Tooltip("Approvals"));
        Tooltip.install(declinationsImage, new Tooltip("Declinations"));
    }

    @Override
    protected void update(Document item) {
        item.setProgressChangeListener(objectStatusView);
        IPLDObject<org.projectjinxers.model.Document> documentObject = item.getDocumentObject();
        org.projectjinxers.model.Document document = documentObject == null || !documentObject.isMapped() ? null
                : documentObject.getMapped();
        if (document == null) {
            age.set(null);
            time.set(null);
            updateUser(item.getUser());
            if (approvalStateTooltip != null) {
                Tooltip.uninstall(approvalImage, approvalStateTooltip);
            }
            reviewed.set(null);
            reviewedDetails.set(null);
            title.set(null);
            subtitle.set(null);
            version.set(null);
            tags.set(null);
            source.set(null);
            totalReviews.set(null);
            if (totalReviewsTooltip == null) {
                totalReviewsTooltip = new Tooltip("Total reviews (loading)");
            }
            else {
                totalReviewsTooltip.setText("Total reviews (loading)");
            }
            Tooltip.install(totalReviewsImage, totalReviewsTooltip);
            approvals.set(null);
            declinations.set(null);
            if (truthStateTooltip != null) {
                Tooltip.uninstall(truthImage, truthStateTooltip);
            }
            item.getOrLoadDocumentObject();
        }
        else {
            Date date = document.getDate();
            age.set(getAge(date));
            time.set(getDateTime(date));
            updateUser(document.getUserState(), false, 0);
            if (document instanceof Review) {
                updateReviewInfo((Review) document);
            }
            else {
                approvalImage.setImage(null);
                if (approvalStateTooltip != null) {
                    Tooltip.uninstall(approvalImage, approvalStateTooltip);
                }
                reviewed.set(null);
                reviewedDetails.set(null);
            }
            title.set(document.getTitle());
            subtitle.set(document.getSubtitle());
            version.set(document.getVersion());
            tags.set(document.getTags());
            source.set(document.getSource());
            updateReviewsInfo();
        }
    }

    @FXML
    void showAuthor(Event e) {

    }

    @FXML
    void showReviewed(Event e) {

    }

    private void updateUser(User user) {
        if (user == null) {
            author.set(null);
            authorDetails.set(null);
        }
        else {
            author.set(user.getName());
            authorDetails.set(user.getMultihash());
        }
    }

    private void updateUser(IPLDObject<UserState> userState, boolean loadFailed, int attemptCount) {
        if (loadFailed && attemptCount == 3) {
            author.set("<unknown user>");
            authorDetails.set("Gave up after 3 failed attempts.");
        }
        else if (userState.isMapped()) {
            IPLDObject<org.projectjinxers.model.User> userObject = userState.getMapped().getUser();
            if (userObject.isMapped()) {
                org.projectjinxers.model.User user = userObject.getMapped();
                author.set(user.getUsername());
                authorDetails.set(getDateTime(user.getCreatedAt()) + " - " + userObject.getMultihash());
            }
            else {
                author.set(null);
                authorDetails.set(null);
                loadObject(userObject, (successCount) -> updateUser(userState, successCount == 0, attemptCount + 1));
            }
        }
        else {
            author.set(null);
            authorDetails.set(null);
            loadObject(userState, (successCount) -> updateUser(userState, successCount == 0, attemptCount + 1));
        }
    }

    private void updateReviewInfo(Review review) {
        Boolean approve = review.getApprove();
        if (approvalStateTooltip == null) {
            approvalStateTooltip = new Tooltip();
        }
        if (approve == null) {
            approvalImage.setImage(NEUTRAL_IMAGE);
            approvalStateTooltip.setText("Neutral");
            Tooltip.install(approvalImage, approvalStateTooltip);
        }
        else if (approve) {
            approvalImage.setImage(APPROVE_IMAGE);
            approvalStateTooltip.setText("Approved");
        }
        else {
            if (review.isInvertTruth()) {
                approvalImage.setImage(TRUTH_INVERSION_IMAGE);
                Document item = getItem();
                Group group = item.getGroup();
                if (group == null) {
                    approvalStateTooltip.setText("Truth inversion");
                }
                else {
                    try {
                        ModelController controller = group.getController();
                        ModelState modelState = controller.getCurrentValidatedState().getMapped();
                        String documentHash = item.getMultihash();
                        if (modelState.isSealedDocument(documentHash)) {
                            SealedDocument sealed = modelState.expectSealedDocument(documentHash);
                            if (sealed.isTruthInverted()) {
                                approvalStateTooltip.setText("Successful truth inversion");
                            }
                            else {
                                approvalStateTooltip.setText("Failed truth inversion");
                            }
                        }
                        else {
                            approvalStateTooltip.setText("Ongoing truth inversion");
                        }
                    }
                    catch (Exception e) {
                        // Not standalone, object is loaded, so the controller must have been initialized successfully
                        throw new Error(e);
                    }
                }
            }
            else {
                approvalImage.setImage(DECLINE_IMAGE);
                approvalStateTooltip.setText("Declined");
            }
        }
        Tooltip.install(approvalImage, approvalStateTooltip);
        updateReviewed(review.getDocument(), false, 0);
    }

    private void updateReviewed(IPLDObject<org.projectjinxers.model.Document> reviewedObject, boolean failed,
            int attemptCount) {
        if (failed && attemptCount == 3) {
            reviewed.set(reviewedObject.getMultihash());
            reviewedDetails.set("Failed to load. Gave up after 3 failed attempts.");
        }
        else if (reviewedObject.isMapped()) {
            org.projectjinxers.model.Document reviewed = reviewedObject.getMapped();
            String title = reviewed.getTitle();
            this.reviewed.set(title == null ? reviewedObject.getMultihash() : title);
            reviewedDetails.set(title == null ? "Reviewed untitled document" : "Reviewed document");
        }
        else {
            reviewed.set(null);
            reviewedDetails.set(null);
            loadObject(reviewedObject,
                    (successCount) -> updateReviewed(reviewedObject, successCount == 0, attemptCount + 1));
        }
    }

    private void updateReviewsInfo() {
        if (totalReviewsTooltip == null) {
            totalReviewsTooltip = new Tooltip();
        }
        Document item = getItem();
        Group group = item.getGroup();
        IPLDObject<ModelState> modelStateObject;
        ModelController controller;
        if (group == null) {
            controller = null;
        }
        else {
            try {
                controller = group.getController();
            }
            catch (Exception e) {
                // Not standalone, object is loaded, so the controller must have been initialized successfully
                throw new Error(e);
            }
        }
        modelStateObject = controller == null ? null : controller.getCurrentValidatedState();
        if (modelStateObject == null) {
            totalReviews.set("n/a");
            totalReviewsTooltip.setText("Total reviews");
            approvals.set("n/a");
            declinations.set("n/a");
            truthImage.setImage(UNKNOWN_SEAL_STATE);
            if (truthStateTooltip == null) {
                truthStateTooltip = new Tooltip();
            }
            if (group == null) {
                truthStateTooltip
                        .setText("Please associate the document to a group, if you want to see the reviews summary.");
            }
            else {
                truthStateTooltip.setText("Please wait until a model state has been validated.");
            }
            Tooltip.install(truthImage, truthStateTooltip);
        }
        else {
            ModelState modelState = modelStateObject.getMapped();
            String documentHash = item.getMultihash();
            String[] reviewTableEntries = modelState.getReviewTableEntries(documentHash);
            if (reviewTableEntries == null) {
                totalReviews.set("0");
                totalReviewsTooltip.setText("Total reviews");
                approvals.set("0");
                declinations.set("0");
                truthImage.setImage(null);
                if (truthStateTooltip != null) {
                    Tooltip.uninstall(truthImage, truthStateTooltip);
                }
            }
            else {
                Map<String, IPLDObject<Review>> reviewObjects = new HashMap<>();
                IPLDContext context = controller.getContext();
                for (String reviewHash : reviewTableEntries) {
                    reviewObjects.put(reviewHash,
                            new IPLDObject<>(reviewHash, LoaderFactory.REVIEW.createLoader(), context, null));
                }
                boolean sealed = modelState.isSealedDocument(documentHash);
                if (sealed) {
                    truthImage.setImage(UNKNOWN_SEAL_STATE);
                    if (truthStateTooltip == null) {
                        truthStateTooltip = new Tooltip();
                    }
                    truthStateTooltip.setText("Loading reviews");
                    Tooltip.install(truthImage, truthStateTooltip);
                }
                loadReviews(sealed, reviewObjects, false, 0);
            }
        }
        Tooltip.install(totalReviewsImage, totalReviewsTooltip);
    }

    private void loadReviews(boolean sealed, Map<String, IPLDObject<Review>> reviewObjects, boolean completeFailure,
            int attemptCountAfterLastSuccess) {
        if (completeFailure && attemptCountAfterLastSuccess == 3) {
            totalReviewsTooltip.setText("Total reviews (incomplete - gave up after 3 complete loading failures)");
            if (sealed) {
                if (truthStateTooltip == null) {
                    truthStateTooltip = new Tooltip();
                }
                truthStateTooltip.setText("Incomplete evaluation - " + reviewObjects.size()
                        + " total review table entries (might include older versions)");
                Tooltip.install(truthImage, truthStateTooltip);
            }
        }
        else {
            int totalCount = reviewObjects.size();
            int approvalsCount = 0;
            int declinationsCount = 0;
            Collection<IPLDObject<Review>> toLoad = new ArrayList<>();
            for (IPLDObject<Review> reviewObject : reviewObjects.values()) {
                if (reviewObject.isMapped()) {
                    Review review = reviewObject.getMapped();
                    String previousVersionHash = review.getPreviousVersionHash();
                    if (reviewObjects.containsKey(previousVersionHash)) {
                        totalCount--;
                    }
                    Boolean approve = review.getApprove();
                    if (approve != null) {
                        if (approve) {
                            approvalsCount++;
                        }
                        else {
                            declinationsCount++;
                        }
                    }
                }
                else {
                    toLoad.add(reviewObject);
                }
            }
            totalReviews.set(String.valueOf(totalCount));
            approvals.set(String.valueOf(approvalsCount));
            declinations.set(String.valueOf(declinationsCount));
            if (toLoad.size() > 0) {
                totalReviewsTooltip.setText("Total reviews (loading)");
                loadObjects(toLoad, (successCount) -> loadReviews(sealed, reviewObjects, successCount == 0,
                        successCount == 0 ? attemptCountAfterLastSuccess + 1 : 0));
            }
            else {
                totalReviewsTooltip.setText("Total reviews");
                if (sealed) {
                    truthImage.setImage(approvalsCount > declinationsCount ? SEALED_TRUTH_IMAGE : SEALED_LIE_IMAGE);
                }
                else {
                    truthImage.setImage(null);
                    if (truthStateTooltip != null) {
                        Tooltip.uninstall(truthImage, truthStateTooltip);
                    }
                }
            }
        }
    }

    private String getAge(Date date) {
        long diff = System.currentTimeMillis() - date.getTime();
        if (diff >= ONE_YEAR) {
            long years = diff / ONE_YEAR;
            if (years == 1) {
                return "last year";
            }
            return years + " years ago";
        }
        if (diff >= ONE_WEEK) {
            long weeks = diff / ONE_WEEK;
            if (weeks == 1) {
                return "last week";
            }
            return weeks + " weeks ago";
        }
        if (diff >= ONE_DAY) {
            long days = diff / ONE_DAY;
            if (days == 1) {
                return "yesterday";
            }
            return days + " days ago";
        }
        if (diff >= ONE_HOUR) {
            long hours = diff / ONE_HOUR;
            if (hours == 1) {
                return "1 hour ago";
            }
            return hours + " hours ago";
        }
        if (diff >= ONE_MINUTE) {
            long minutes = diff / ONE_MINUTE;
            if (minutes == 1) {
                return "1 minute ago";
            }
            return minutes + " minutes ago";
        }
        return "just now";
    }

    private String getDateTime(Date date) {
        DateFormat dateInstance = DateFormat.getDateInstance(DateFormat.MEDIUM);
        DateFormat timeInstance = DateFormat.getTimeInstance(DateFormat.MEDIUM);
        return dateInstance.format(date) + " " + timeInstance.format(date);
    }

}
