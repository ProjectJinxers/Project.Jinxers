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

import java.io.IOException;

import org.projectjinxers.account.Signer;
import org.projectjinxers.config.Config;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.IPLDObject.ProgressTask;
import org.projectjinxers.controller.ModelController;
import org.projectjinxers.model.DocumentRemoval;
import org.projectjinxers.model.LoaderFactory;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.UserState;

/**
 * @author ProjectJinxers
 *
 */
public class Document extends ProgressObserver {

    public enum Kind {

        NEW, STANDALONE, LOADED

    }

    private transient Group group;
    private transient User user;
    private String multihash;
    private transient String importURL;
    private transient IPLDObject<org.projectjinxers.model.Document> documentObject;
    private transient IPLDObject<ModelState> modelStateObject;
    private Kind kind;

    private boolean loading;
    private boolean saveCalled;
    private boolean removeCalled;
    private boolean removed;

    public Document(Group group, IPLDObject<org.projectjinxers.model.Document> documentObject) {
        super(true);
        this.group = group;
        this.multihash = documentObject.getMultihash();
        this.documentObject = documentObject;
        this.kind = Kind.LOADED;
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
                        ModelController controller = group == null
                                ? ModelController.getModelController(Config.getSharedInstance())
                                : group.getOrCreateController();
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
                            try {
                                this.modelStateObject = group.getOrCreateController().getCurrentValidatedState();
                                IPLDObject<UserState> userState = modelStateObject.getMapped()
                                        .expectUserState(userHash);
                                if (userState == null) {
                                    failedTask(ProgressTask.LOAD,
                                            "The document is not contained in the selected group.", null);
                                    this.group = null;
                                    return;
                                }
                                String firstVersionHash = mapped.getFirstVersionHash();
                                if (firstVersionHash == null) {
                                    firstVersionHash = multihash;
                                }
                                if (userState.getMapped().isRemoved(firstVersionHash)) {
                                    removed = true;
                                }
                            }
                            catch (Exception e) {
                                failedTask(ProgressTask.LOAD,
                                        "Failed to check if the document is contained in the selected group.", null);
                                this.group = null;
                                return;
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

    public void checkRemoved(Group group, ModelState valid) {
        if (this.group == group) {
            if (documentObject != null && documentObject.isMapped()) {
                org.projectjinxers.model.Document mapped = documentObject.getMapped();
                IPLDObject<UserState> userStateObject = mapped.getUserState();
                if (userStateObject != null) {
                    String userHash = userStateObject.getMapped().getUser().getMultihash();
                    IPLDObject<UserState> userState = valid.expectUserState(userHash);
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
