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
package org.projectjinxers.ui.util;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.SimpleProgressListener;
import org.projectjinxers.controller.IPLDObject.ProgressTask;
import org.projectjinxers.model.IPLDSerializable;

import javafx.application.Platform;

/**
 * @author ProjectJinxers
 *
 */
public class ModelLoadingUtility {

    public interface CompletionHandler {

        void completed(int successCount);

    }

    public static <T extends IPLDSerializable> void loadObject(IPLDObject<T> object,
            CompletionHandler completionHandler) {
        new Thread(() -> {
            if (object.isMapped()) {
                Platform.runLater(() -> completionHandler.completed(1));
            }
            else {
                object.setProgressListener(new SimpleProgressListener() {
                    @Override
                    protected void finishedTask(ProgressTask task, boolean success) {
                        object.removeProgressListener(this);
                        Platform.runLater(() -> completionHandler.completed(success ? 1 : 0));
                    }
                });
                object.getMapped();
            }
        }).start();
    }

    public static <T extends IPLDSerializable> void loadObjects(Collection<IPLDObject<T>> objects,
            CompletionHandler completionHandler) {
        new Thread(() -> {
            int totalAttempts = objects.size();
            AtomicInteger finishCounter = new AtomicInteger();
            AtomicInteger successCounter = new AtomicInteger();
            for (IPLDObject<T> object : objects) {
                if (object.isMapped()) {
                    int successCount = successCounter.incrementAndGet();
                    if (finishCounter.incrementAndGet() == totalAttempts) {
                        Platform.runLater(() -> completionHandler.completed(successCount));
                    }
                }
                else {
                    object.setProgressListener(new SimpleProgressListener() {
                        @Override
                        protected void finishedTask(ProgressTask task, boolean success) {
                            object.removeProgressListener(this);
                            if (success) {
                                successCounter.incrementAndGet();
                            }
                            if (finishCounter.incrementAndGet() == totalAttempts) {
                                Platform.runLater(() -> completionHandler.completed(successCounter.get()));
                            }
                        }
                    });
                    object.getMapped();
                }
            }
        }).start();
    }

}
