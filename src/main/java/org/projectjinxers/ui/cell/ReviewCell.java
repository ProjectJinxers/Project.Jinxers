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
import java.util.Date;
import java.util.ResourceBundle;

import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.data.Document;
import org.projectjinxers.data.Document.ReviewInfo;
import org.projectjinxers.data.User;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.UserState;
import org.projectjinxers.ui.main.MainPresenter;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;

/**
 * @author ProjectJinxers
 *
 */
public class ReviewCell extends AbstractTreeCell<Document> implements Initializable {

    private MainPresenter mainPresenter;

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

    private StringProperty age = new SimpleStringProperty();
    private StringProperty time = new SimpleStringProperty();
    private StringProperty author = new SimpleStringProperty();
    private StringProperty authorDetails = new SimpleStringProperty();
    private StringProperty title = new SimpleStringProperty();
    private StringProperty totalReviews = new SimpleStringProperty();
    private StringProperty approvals = new SimpleStringProperty();
    private StringProperty declinations = new SimpleStringProperty();

    private Tooltip approvalStateTooltip;
    private Tooltip totalReviewsTooltip;
    private Tooltip truthStateTooltip;

    private MenuItem showInDocumentsListItem;

    private Document loading;

    public ReviewCell(MainPresenter mainPresenter) {
        super("ReviewCell.fxml", true);
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

    public StringProperty titleProperty() {
        return title;
    }

    public String getTitle() {
        return title.get();
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
            title.set(null);
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
            approvalStateTooltip = updateReviewInfo((Review) document, item, approvalStateTooltip, approvalImage);
            title.set(document.getTitle());
            updateReviewsInfo(item);
        }
    }

    @Override
    protected void updateContextMenu(ContextMenu contextMenu) {
        contextMenu.getItems().setAll(getShowInDocumentsListItem());
    }

    @FXML
    void showAuthor(Event e) {

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

    private void updateReviewsInfo(Document document) {
        if (document != getItem()) {
            return;
        }
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

    private MenuItem getShowInDocumentsListItem() {
        if (showInDocumentsListItem == null) {
            showInDocumentsListItem = new MenuItem("Show in documents list");
            showInDocumentsListItem.setOnAction(event -> mainPresenter.showInDocumentsList(getItem()));
        }
        return showInDocumentsListItem;
    }

}
