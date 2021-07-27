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

import static org.projectjinxers.ui.cell.DocumentCellUtil.getDateTime;
import static org.projectjinxers.ui.cell.DocumentCellUtil.updateReviewInfo;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.ResourceBundle;

import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.ModelController;
import org.projectjinxers.controller.OwnershipTransferController;
import org.projectjinxers.data.Document;
import org.projectjinxers.data.Document.Kind;
import org.projectjinxers.data.Document.ReviewInfo;
import org.projectjinxers.data.Group;
import org.projectjinxers.data.ProgressObserver;
import org.projectjinxers.data.User;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.SealedDocument;
import org.projectjinxers.model.UserState;
import org.projectjinxers.ui.cell.ObjectStatusView.StatusChangeListener;
import org.projectjinxers.ui.main.MainPresenter;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;

/**
 * @author ProjectJinxers
 *
 */
public class DocumentCell<D extends Document> extends AbstractListCell<D>
        implements Initializable, StatusChangeListener {

    private MainPresenter mainPresenter;
    private boolean history;

    @FXML
    private Hyperlink reviewedLink;
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
    private ObjectStatusView objectStatusViewController;

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

    private MenuItem approveItem;
    private MenuItem declineItem;
    private MenuItem reviewItem;
    private MenuItem truthInversionItem;
    private MenuItem editItem;
    private MenuItem updateItem;
    private MenuItem removeItem;
    private MenuItem deleteItem;
    private MenuItem settlementRequestItem;
    private MenuItem unbanRequestItem;
    private MenuItem ownershipRequestItem;
    private MenuItem showInDocumentsListItem;

    private Document loading;

    protected DocumentCell(String fxmlPath, MainPresenter mainPresenter, boolean hasContextMenu) {
        super(fxmlPath, hasContextMenu);
        this.mainPresenter = mainPresenter;
    }

    public DocumentCell(MainPresenter mainPresenter, boolean history) {
        super("DocumentCell.fxml", true);
        this.mainPresenter = mainPresenter;
        this.history = history;
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
        reviewedLink.managedProperty().bind(reviewedLink.visibleProperty());
        Tooltip.install(approvalsImage, new Tooltip("Approvals"));
        Tooltip.install(declinationsImage, new Tooltip("Declinations"));
    }

    @Override
    protected void update(Document item) {
        objectStatusViewController.setProgressObserver(item, this);
        IPLDObject<org.projectjinxers.model.Document> documentObject = item.getDocumentObject();
        org.projectjinxers.model.Document document = documentObject == null || !documentObject.isMapped() ? null
                : documentObject.getMapped();
        if (document == null) {
            if (item == loading) {
                return;
            }
            age.set(null);
            time.set(null);
            updateUser(item.getUser());
            if (approvalStateTooltip != null) {
                Tooltip.uninstall(approvalImage, approvalStateTooltip);
            }
            reviewed.set(null);
            reviewedLink.setVisible(false);
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
            this.loading = item;
            item.getOrLoadDocumentObject();
        }
        else {
            this.loading = null;
            Date date = document.getDate();
            age.set(DocumentCellUtil.getAge(date));
            time.set(getDateTime(date));
            updateUser(document.getUserState(), false, 0);
            if (document instanceof Review) {
                reviewedLink.setVisible(true);
                Review review = (Review) document;
                approvalStateTooltip = updateReviewInfo(review, item, approvalStateTooltip, approvalImage);
                updateReviewed(review.getDocument(), false, 0);
            }
            else {
                approvalImage.setImage(null);
                if (approvalStateTooltip != null) {
                    Tooltip.uninstall(approvalImage, approvalStateTooltip);
                }
                reviewed.set(null);
                reviewedLink.setVisible(false);
                reviewedDetails.set(null);
            }
            title.set(document.getTitle());
            subtitle.set(document.getSubtitle());
            version.set(document.getVersion());
            tags.set(document.getTags());
            source.set(document.getSource());
            updateReviewsInfo(item);
        }
    }

    @Override
    protected void updateContextMenu(ContextMenu contextMenu) {
        Document item = getItem();
        Kind kind = item.getKind();
        Group group = item.getGroup();
        Collection<MenuItem> menuItems = new ArrayList<>();
        if (group == null && kind == Kind.STANDALONE) {
            menuItems.add(getUpdateItem());
            menuItems.add(getRemoveItem());
        }
        else if (history || item.isReplaced()) {
            if (history) {
                menuItems.add(getShowInDocumentsListItem());
            }
            else {
                menuItems.add(getRemoveItem());
            }
        }
        else if (group != null && item.getMultihash() != null && !item.isDestroying() && !item.isRemoved()) {
            IPLDObject<org.projectjinxers.model.Document> documentObject = item.getDocumentObject();
            if (documentObject != null && documentObject.isMapped()) {
                ModelController controller = group.getOrCreateController();
                IPLDObject<ModelState> currentValidatedState = controller.getCurrentValidatedState();
                if (currentValidatedState != null) {
                    ModelState validState = currentValidatedState.getMapped();
                    String documentHash = item.getMultihash();
                    if (validState.isSealedDocument(documentHash)) {
                        SealedDocument sealed = validState.expectSealedDocument(documentHash);
                        if (sealed.isOriginal()) {
                            menuItems.add(getTruthInversionItem());
                        }
                    }
                    else {
                        menuItems.add(getApproveItem());
                        menuItems.add(getDeclineItem());
                        menuItems.add(getReviewItem());
                    }
                    menuItems.add(getEditItem());
                    org.projectjinxers.model.Document document = documentObject.getMapped();
                    if (document.getUserState().getMapped().checkRequiredRating()) {
                        menuItems.add(getDeleteItem());
                    }
                    else {
                        menuItems.add(getUnbanRequestItem());
                    }
                    // Date date = document.getDate();
                    // TODO: check prerequisites for settlement requests and add item if met
                    if (validState.getVotingForOwnershipTransfer(documentHash) == null) {
                        Date lastActivityDate = validState.getLastActivityDate(documentObject);
                        if (lastActivityDate != null && System.currentTimeMillis() + controller.getTimestampTolerance()
                                - lastActivityDate.getTime() >= OwnershipTransferController.REQUIRED_INACTIVITY) {
                            menuItems.add(getOwnershipRequestItem());
                        }
                    }
                }
            }
            else if (kind == Kind.STANDALONE) {
                menuItems.add(getUpdateItem());
                menuItems.add(getRemoveItem());
            }
        }
        contextMenu.getItems().setAll(menuItems);
    }

    @Override
    public void statusChanged(ProgressObserver progressObserver) {
        D item = getItem();
        if (progressObserver == item) {
            updateItem(item, false);
            mainPresenter.getView().statusChanged(progressObserver);
        }
    }

    @FXML
    void showAuthor(Event e) {

    }

    @FXML
    void showReviewed(Event e) {
        Document item = getItem();
        mainPresenter.showLinked(item.getGroup(),
                ((Review) item.getDocumentObject().getMapped()).getDocument().getMultihash());
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
            reviewedDetails.set((title == null ? "Reviewed untitled document - " : "Reviewed document - ")
                    + reviewedObject.getMultihash());
        }
        else {
            reviewed.set(null);
            reviewedDetails.set(null);
            loadObject(reviewedObject,
                    (successCount) -> updateReviewed(reviewedObject, successCount == 0, attemptCount + 1));
        }
    }

    private void updateReviewsInfo(Document document) {
        if (document != getItem()) {
            return;
        }
        mainPresenter.getView().updatedReviews(document);
        if (totalReviewsTooltip == null) {
            totalReviewsTooltip = new Tooltip();
        }
        ReviewInfo reviewInfo = document
                .getOrLoadReviewInfo((successCount) -> Platform.runLater(() -> updateReviewsInfo(document)));
        if (reviewInfo.isAvailable()) {
            totalReviews.set(String.valueOf(reviewInfo.getTotalCount()));
            boolean loading = reviewInfo.isLoading();
            String statusMessage = reviewInfo.getStatusMessage();
            if (loading) {
                totalReviewsTooltip.setText("Total reviews (loading)");
            }
            else {
                totalReviewsTooltip.setText("Total reviews");
            }
            int approvalsCount = reviewInfo.getApprovalsCount();
            int declinationsCount = reviewInfo.getDeclinationsCount();
            approvals.set(String.valueOf(approvalsCount));
            declinations.set(String.valueOf(declinationsCount));
            if (reviewInfo.isSealed()) {
                if (truthStateTooltip == null) {
                    truthStateTooltip = new Tooltip();
                }
                if (loading || statusMessage != null) {
                    truthImage.setImage(DocumentCellUtil.UNKNOWN_SEAL_STATE);
                    if (loading) {
                        truthStateTooltip.setText("Loading reviews");
                    }
                    else {
                        truthStateTooltip.setText(statusMessage);
                    }
                }
                else if (approvalsCount > declinationsCount) {
                    truthImage.setImage(DocumentCellUtil.SEALED_TRUTH_IMAGE);
                    truthStateTooltip.setText("Truth");
                }
                else {
                    truthImage.setImage(DocumentCellUtil.SEALED_LIE_IMAGE);
                    truthStateTooltip.setText("Lie");
                }
                Tooltip.install(truthImage, truthStateTooltip);
            }
            else {
                truthImage.setImage(null);
                if (truthStateTooltip != null) {
                    Tooltip.uninstall(truthImage, truthStateTooltip);
                }
            }
        }
        else {
            totalReviews.set("n/a");
            totalReviewsTooltip.setText("Total reviews");
            approvals.set("n/a");
            declinations.set("n/a");
            truthImage.setImage(DocumentCellUtil.UNKNOWN_SEAL_STATE);
            if (truthStateTooltip == null) {
                truthStateTooltip = new Tooltip();
            }
            truthStateTooltip.setText(reviewInfo.getStatusMessage());
            Tooltip.install(truthImage, truthStateTooltip);
        }
        Tooltip.install(totalReviewsImage, totalReviewsTooltip);
    }

    private MenuItem getApproveItem() {
        if (approveItem == null) {
            approveItem = new MenuItem("Approve");
            approveItem.setOnAction(event -> mainPresenter.createReview(getItem(), Boolean.TRUE));
        }
        return approveItem;
    }

    private MenuItem getDeclineItem() {
        if (declineItem == null) {
            declineItem = new MenuItem("Decline");
            declineItem.setOnAction(event -> mainPresenter.createReview(getItem(), Boolean.FALSE));
        }
        return declineItem;
    }

    private MenuItem getReviewItem() {
        if (reviewItem == null) {
            reviewItem = new MenuItem("Review");
            reviewItem.setOnAction(event -> mainPresenter.createReview(getItem(), null));
        }
        return reviewItem;
    }

    private MenuItem getTruthInversionItem() {
        if (truthInversionItem == null) {
            truthInversionItem = new MenuItem("Invert truth");
            truthInversionItem.setOnAction(event -> mainPresenter.createTruthInversion(getItem()));
        }
        return truthInversionItem;
    }

    private MenuItem getEditItem() {
        if (editItem == null) {
            editItem = new MenuItem("Edit document");
            // the main presenter distinguishes between edit and update by checking the item
            editItem.setOnAction(event -> mainPresenter.editDocument(getItem()));
        }
        return editItem;
    }

    private MenuItem getUpdateItem() {
        if (updateItem == null) {
            updateItem = new MenuItem("Update document");
            // the main presenter distinguishes between edit and update by checking the item
            updateItem.setOnAction(event -> mainPresenter.editDocument(getItem()));
        }
        return updateItem;
    }

    private MenuItem getRemoveItem() {
        if (removeItem == null) {
            removeItem = new MenuItem("Remove document");
            removeItem.setOnAction(event -> mainPresenter.removeStandaloneDocument(getItem()));
        }
        return removeItem;
    }

    private MenuItem getDeleteItem() {
        if (deleteItem == null) {
            deleteItem = new MenuItem("Delete document");
            deleteItem.setOnAction(event -> mainPresenter.deleteDocument(getItem()));
        }
        return deleteItem;
    }

    private MenuItem getSettlementRequestItem() {
        if (settlementRequestItem == null) {
            settlementRequestItem = new MenuItem("Request settlement");
            settlementRequestItem.setOnAction(event -> {
            });
        }
        return settlementRequestItem;
    }

    private MenuItem getUnbanRequestItem() {
        if (unbanRequestItem == null) {
            unbanRequestItem = new MenuItem("Request unban");
            unbanRequestItem.setOnAction(event -> {
            });
        }
        return unbanRequestItem;
    }

    private MenuItem getOwnershipRequestItem() {
        if (ownershipRequestItem == null) {
            ownershipRequestItem = new MenuItem("Request ownership");
            ownershipRequestItem.setOnAction(event -> {
            });
        }
        return ownershipRequestItem;
    }

    private MenuItem getShowInDocumentsListItem() {
        if (showInDocumentsListItem == null) {
            showInDocumentsListItem = new MenuItem("Show in documents list");
            showInDocumentsListItem.setOnAction(event -> mainPresenter.showInDocumentsList(getItem()));
        }
        return showInDocumentsListItem;
    }

}
