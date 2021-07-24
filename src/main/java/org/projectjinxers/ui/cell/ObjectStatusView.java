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

import java.net.URL;
import java.util.ResourceBundle;

import org.projectjinxers.controller.IPLDObject.ProgressTask;
import org.projectjinxers.data.ProgressObserver;
import org.projectjinxers.data.ProgressObserver.ProgressChangeListener;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;

/**
 * @author ProjectJinxers
 *
 */
public class ObjectStatusView implements Initializable, ProgressChangeListener {

    public interface StatusChangeListener {

        void statusChanged(ProgressObserver progressObserver);

    }

    @FXML
    private VBox root;
    @FXML
    private Label currentTaskLabel;
    @FXML
    private ProgressBar totalProgressBar;
    @FXML
    private ProgressBar taskProgressBar;
    @FXML
    private Button cancelButton;
    @FXML
    private Button retryButton;

    private StringProperty currentTask = new SimpleStringProperty();
    private DoubleProperty totalProgress = new SimpleDoubleProperty();
    private DoubleProperty progress = new SimpleDoubleProperty();
    private StringProperty statusMessage = new SimpleStringProperty();

    private ProgressObserver progressObserver;
    private StatusChangeListener statusChangeListener;

    private boolean retrying;

    public StringProperty currentTaskProperty() {
        return currentTask;
    }

    public String getCurrentTask() {
        return currentTask.get();
    }

    public DoubleProperty totalProgressProperty() {
        return totalProgress;
    }

    public double getTotalProgress() {
        return totalProgress.get();
    }

    public DoubleProperty progressProperty() {
        return progress;
    }

    public double getProgress() {
        return progress.get();
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public String getStatusMessage() {
        return statusMessage.get();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentTaskLabel.managedProperty().bind(currentTaskLabel.visibleProperty());
        totalProgressBar.managedProperty().bind(totalProgressBar.visibleProperty());
        taskProgressBar.managedProperty().bind(taskProgressBar.visibleProperty());
        cancelButton.managedProperty().bind(cancelButton.visibleProperty());
        retryButton.managedProperty().bind(retryButton.visibleProperty());
        root.managedProperty().bind(root.visibleProperty());
    }

    @Override
    public void progressChanged(ProgressObserver observer) {
        if (observer == progressObserver) {
            updateProgress(true);
        }
    }

    public void setProgressObserver(ProgressObserver progressObserver, StatusChangeListener statusChangeListener) {
        this.progressObserver = progressObserver;
        this.statusChangeListener = statusChangeListener;
        updateProgress(false);
        progressObserver.setProgressChangeListener(this);
    }

    @FXML
    void cancel(Event e) {
        progressObserver.cancel();
        setStatusMessage(progressObserver.getStatusMessagePrefix(), "Canceling…");
    }

    @FXML
    void retry(Event e) {
        retrying = true;
        if (!progressObserver.retry()) {
            setStatusMessage(progressObserver.getStatusMessagePrefix(), "Retry failed");
        }
    }

    private void updateProgress(boolean changed) {
        boolean hide = false;
        ProgressTask currentTask = progressObserver.getCurrentTask();
        int totalProgressTasks = currentTask == null ? 0 : currentTask.getTotalProgressTasks();
        boolean finalTask;
        if (totalProgressTasks > 1) {
            totalProgressBar.setVisible(true);
            int totalProgressTask = currentTask.getTotalProgressTask();
            totalProgress.set(totalProgressTask * 1.0 / totalProgressTasks);
            finalTask = totalProgressTasks - totalProgressTask == 1;
        }
        else {
            totalProgressBar.setVisible(false);
            finalTask = false;
        }
        if (retrying && changed && statusChangeListener != null) {
            retrying = false;
            statusChangeListener.statusChanged(progressObserver);
        }
        String statusMessagePrefix = progressObserver.getStatusMessagePrefix();
        ProgressTask failedTask = progressObserver.getFailedTask();
        int totalTaskSteps = progressObserver.getTotalTaskSteps();
        int executedTaskSteps = progressObserver.getExecutedTaskSteps();
        if (totalTaskSteps > 0) {
            progress.set(executedTaskSteps * 1.0 / totalTaskSteps);
            if (finalTask && executedTaskSteps == totalTaskSteps) {
                totalProgress.set(1);
                if (changed && statusChangeListener != null) {
                    statusChangeListener.statusChanged(progressObserver);
                }
                this.currentTask.set(null);
                cancelButton.setVisible(false);
                if (statusMessagePrefix == null && failedTask == null) {
                    hide = true;
                }
            }
            else {
                this.currentTask.set(
                        currentTask == null ? null : currentTask.getProgressMessage(progressObserver.isDestroying()));
                cancelButton.setVisible(true);
            }
        }
        else if (executedTaskSteps == 0) {
            this.currentTask
                    .set(currentTask == null ? null : currentTask.getProgressMessage(progressObserver.isDestroying()));
            progress.set(-1);
            cancelButton.setVisible(true);
        }
        else {
            if (changed && statusChangeListener != null) {
                statusChangeListener.statusChanged(progressObserver);
            }
            this.currentTask.set(null);
            cancelButton.setVisible(false);
            if (statusMessagePrefix == null && failedTask == null) {
                hide = true;
            }
        }
        if (failedTask == null) {
            if (!totalProgressBar.isVisible()) {
                currentTaskLabel.setVisible(true);
            }
            taskProgressBar.setVisible(true);
            retryButton.setVisible(false);
            if (progressObserver.isPaused()) {
                setStatusMessage(statusMessagePrefix, "Paused");
                if (changed && statusChangeListener != null) {
                    statusChangeListener.statusChanged(progressObserver);
                }
            }
            else if (progressObserver.isObsolete()) {
                setStatusMessage(statusMessagePrefix, "Obsolete");
                if (changed && statusChangeListener != null) {
                    statusChangeListener.statusChanged(progressObserver);
                }
            }
            else {
                statusMessage.set(statusMessagePrefix);
            }
        }
        else {
            if (!totalProgressBar.isVisible()) {
                currentTaskLabel.setVisible(false);
            }
            taskProgressBar.setVisible(false);
            cancelButton.setVisible(false);
            retryButton.setVisible(true);
            String failureMessage = progressObserver.getFailureMessage();
            setStatusMessage(statusMessagePrefix, failureMessage == null ? "Unknown failure" : failureMessage);
            if (changed && statusChangeListener != null) {
                statusChangeListener.statusChanged(progressObserver);
            }
        }
        if (hide) {
            root.setVisible(false);
            statusChangeListener = null;
        }
        else {
            root.setVisible(true);
        }
    }

    private void setStatusMessage(String prefix, String statusMessage) {
        if (prefix == null) {
            this.statusMessage.set(statusMessage);
        }
        else {
            this.statusMessage.set(prefix + " - " + statusMessage);
        }
    }

}
