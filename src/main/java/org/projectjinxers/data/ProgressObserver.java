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

import org.projectjinxers.controller.IPLDObject.ProgressListener;
import org.projectjinxers.controller.IPLDObject.ProgressTask;

import javafx.application.Platform;

/**
 * @author ProjectJinxers
 *
 */
public abstract class ProgressObserver implements ProgressListener {

    public interface Retry {

        boolean retry();

    }

    public interface ProgressChangeListener {

        void progressChanged(ProgressObserver observer);

    }

    private transient boolean determinate;

    private transient ProgressTask currentTask;
    private transient int totalTaskSteps;
    private transient int executedTaskSteps;
    private transient ProgressTask failedTask;
    private transient String failureMessage;
    private transient Throwable failure;
    private transient boolean paused;
    private transient boolean obsolete;
    private transient boolean canceled;

    private transient ProgressChangeListener progressChangeListener;
    private transient Retry retry;

    protected ProgressObserver(boolean determinate) {
        this.determinate = determinate;
    }

    @Override
    public boolean isDeterminate() {
        return determinate;
    }

    @Override
    public void startedTask(ProgressTask task, int steps) {
        this.currentTask = task;
        this.totalTaskSteps = steps;
        this.executedTaskSteps = 0;
        fireProgressChanged();
    }

    @Override
    public void nextStep() {
        this.executedTaskSteps++;
        fireProgressChanged();
    }

    @Override
    public void finishedTask(ProgressTask task) {
        if (executedTaskSteps != totalTaskSteps) {
            this.executedTaskSteps = totalTaskSteps <= 0 ? 1 : totalTaskSteps;
            fireProgressChanged();
        }
    }

    @Override
    public void failedTask(ProgressTask task, String message, Throwable failure) {
        this.failedTask = task;
        this.failureMessage = message;
        this.failure = failure;
        fireProgressChanged();
    }

    @Override
    public void enqueued() {
        this.paused = true;
        fireProgressChanged();
    }

    @Override
    public boolean dequeued() {
        if (canceled) {
            return false;
        }
        paused = false;
        fireProgressChanged();
        return true;
    }

    @Override
    public void obsoleted() {
        this.obsolete = true;
        fireProgressChanged();
    }

    @Override
    public boolean isCanceled() {
        return canceled;
    }

    public void cancel() {
        this.canceled = true;
    }

    public ProgressTask getCurrentTask() {
        return currentTask;
    }

    public int getTotalTaskSteps() {
        return totalTaskSteps;
    }

    public int getExecutedTaskSteps() {
        return executedTaskSteps;
    }

    public ProgressTask getFailedTask() {
        return failedTask;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public Throwable getFailure() {
        return failure;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isObsolete() {
        return obsolete;
    }

    public void setProgressChangeListener(ProgressChangeListener progressChangeListener) {
        this.progressChangeListener = progressChangeListener;
    }

    public void startOperation(Retry retry) {
        this.retry = retry;
    }

    public boolean retry() {
        if (paused || retry == null || !canceled && failedTask == null) {
            return false;
        }
        this.currentTask = null;
        this.totalTaskSteps = 0;
        this.executedTaskSteps = 0;
        this.failedTask = null;
        this.canceled = false;
        return retry.retry();
    }

    public abstract String getStatusMessagePrefix();

    private void fireProgressChanged() {
        Platform.runLater(() -> {
            ProgressChangeListener listener = this.progressChangeListener;
            if (listener != null) {
                listener.progressChanged(this);
            }
        });
    }

}
