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

import java.text.DateFormat;
import java.util.Date;

import org.projectjinxers.controller.ModelController;
import org.projectjinxers.data.Document;
import org.projectjinxers.data.Group;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.SealedDocument;

import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * @author ProjectJinxers
 *
 */
public class DocumentCellUtil {

    private static final long ONE_MINUTE = 1000L * 60;
    private static final long ONE_HOUR = ONE_MINUTE * 60;
    private static final long ONE_DAY = ONE_HOUR * 24;
    private static final long ONE_WEEK = ONE_DAY * 7;
    private static final long ONE_YEAR = ONE_DAY * 365;

    static final Image APPROVE_IMAGE = new Image("images/approve.png");
    static final Image DECLINE_IMAGE = new Image("images/decline.png");
    static final Image NEUTRAL_IMAGE = new Image("images/totalReviews.png");
    static final Image TRUTH_INVERSION_IMAGE = new Image("images/truthInversion.png");
    static final Image SEALED_TRUTH_IMAGE = new Image("images/sealedTruth.png");
    static final Image SEALED_LIE_IMAGE = new Image("images/sealedLie.png");
    static final Image UNKNOWN_SEAL_STATE = new Image("images/questionmark.png");

    public static Tooltip updateReviewInfo(Review review, Document item, Tooltip approvalStateTooltip,
            ImageView approvalImage) {
        Boolean approve = review.getApprove();
        if (approvalStateTooltip == null) {
            approvalStateTooltip = new Tooltip();
        }
        if (approve == null) {
            approvalImage.setImage(DocumentCellUtil.NEUTRAL_IMAGE);
            approvalStateTooltip.setText("Neutral");
            Tooltip.install(approvalImage, approvalStateTooltip);
        }
        else if (approve) {
            approvalImage.setImage(DocumentCellUtil.APPROVE_IMAGE);
            approvalStateTooltip.setText("Approved");
        }
        else {
            if (review.isInvertTruth()) {
                approvalImage.setImage(DocumentCellUtil.TRUTH_INVERSION_IMAGE);
                Group group = item.getGroup();
                if (group == null) {
                    approvalStateTooltip.setText("Truth inversion");
                }
                else {
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
            }
            else {
                approvalImage.setImage(DocumentCellUtil.DECLINE_IMAGE);
                approvalStateTooltip.setText("Declined");
            }
        }
        Tooltip.install(approvalImage, approvalStateTooltip);
        return approvalStateTooltip;
    }

    public static String getAge(Date date) {
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

    public static String getDateTime(Date date) {
        DateFormat dateInstance = DateFormat.getDateInstance(DateFormat.MEDIUM);
        DateFormat timeInstance = DateFormat.getTimeInstance(DateFormat.MEDIUM);
        return dateInstance.format(date) + " " + timeInstance.format(date);
    }

}
