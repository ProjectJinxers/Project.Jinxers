/*
	Copyright (C) 2021 ProjectJinxers

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.projectjinxers.controller;

import org.projectjinxers.controller.IPLDObject.ProgressListener;
import org.projectjinxers.controller.IPLDObject.ProgressTask;

/**
 * @author ProjectJinxers
 *
 */
public abstract class SimpleProgressListener implements ProgressListener {

    @Override
    public boolean isDeterminate() {
        return false;
    }

    @Override
    public void startedTask(ProgressTask task, int steps) {

    }

    @Override
    public void nextStep() {

    }

    @Override
    public void finishedTask(ProgressTask task) {
        finishedTask(task, true);
    }

    @Override
    public void failedTask(ProgressTask task, String message, Throwable failure) {
        finishedTask(task, false);
    }

    @Override
    public void enqueued() {

    }

    @Override
    public boolean dequeued() {
        return true;
    }

    @Override
    public void obsoleted() {

    }

    @Override
    public boolean isCanceled() {
        return false;
    }

    protected abstract void finishedTask(ProgressTask task, boolean success);

}
