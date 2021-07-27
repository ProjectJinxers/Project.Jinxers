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
package org.projectjinxers.ui.document;

import static org.projectjinxers.util.ObjectUtility.checkNotBlank;
import static org.projectjinxers.util.ObjectUtility.isEqual;
import static org.projectjinxers.util.ObjectUtility.isNullOrBlank;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.projectjinxers.account.Signer;
import org.projectjinxers.controller.IPLDObject;
import org.projectjinxers.controller.ModelController;
import org.projectjinxers.data.Data;
import org.projectjinxers.data.Document;
import org.projectjinxers.data.Group;
import org.projectjinxers.data.User;
import org.projectjinxers.model.DocumentContents;
import org.projectjinxers.model.ModelState;
import org.projectjinxers.model.Review;
import org.projectjinxers.model.UserState;
import org.projectjinxers.ui.ProjectJinxers;
import org.projectjinxers.ui.common.DataPresenter;
import org.projectjinxers.ui.common.PJPresenter.View;
import org.projectjinxers.ui.editor.EditorPresenter;
import org.projectjinxers.ui.editor.EditorPresenter.EditorListener;
import org.projectjinxers.ui.editor.EditorView;
import org.projectjinxers.ui.group.GroupPresenter;
import org.projectjinxers.ui.group.GroupView;
import org.projectjinxers.ui.signing.SigningPresenter;
import org.projectjinxers.ui.signing.SigningPresenter.SigningListener;
import org.projectjinxers.ui.signing.SigningView;
import org.projectjinxers.ui.user.UserPresenter;
import org.projectjinxers.ui.user.UserView;

import com.overzealous.remark.Remark;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;

/**
 * @author ProjectJinxers
 *
 */
public class DocumentPresenter extends DataPresenter<Document, DocumentPresenter.DocumentView>
        implements EditorListener, SigningListener {

    private static final int INITIAL_MD_FILE_STRING_BUILDER_SIZE = 1024 * 32;

    interface DocumentView extends View {

        void updateGroups(Group toSelect);

        void updateUsers(User toSelect);

        void updateContentsIndicator();

    }

    private Document reviewed;
    private Data data;
    private boolean truthInversion;
    private Boolean approval;

    private String abstr;
    private String contents;

    private DataListener<Group> groupListener;
    private DataListener<User> userListener;

    private ModelController controller;
    private Document toSave;

    protected DocumentPresenter(DocumentView view, Document document, Document reviewed, Data data,
            boolean truthInversion, Boolean approval, ProjectJinxers application) throws Exception {
        super(view, document, application);
        this.reviewed = reviewed;
        this.data = data;
        this.truthInversion = truthInversion;
        this.approval = approval;
        if (document != null) {
            IPLDObject<org.projectjinxers.model.Document> documentObject = document.getDocumentObject();
            if (documentObject != null) {
                org.projectjinxers.model.Document mapped = documentObject.getMapped();
                if (mapped != null) {
                    IPLDObject<DocumentContents> contentsObject = mapped.getContents();
                    if (contentsObject != null) {
                        DocumentContents contents = contentsObject.getMapped();
                        if (contents != null) {
                            this.abstr = checkNotBlank(contents.getAbstract());
                            this.contents = checkNotBlank(contents.getContents());
                        }
                    }
                }
            }
        }
    }

    public Document getReviewed() {
        return reviewed;
    }

    public boolean isTruthInversion() {
        return truthInversion;
    }

    public Boolean getApproval() {
        return approval;
    }

    public List<Group> getGroups() {
        return new ArrayList<>(data.getGroups().values());
    }

    public List<User> getUsers() {
        Collection<User> users = data.getUsers();
        return users == null ? null : new ArrayList<>(users);
    }

    public String getAbstract() {
        return abstr;
    }

    public String getContents() {
        return contents;
    }

    @Override
    protected Scene createScene() {
        Scene res = new DocumentScene(this);
        return res;
    }

    @Override
    public void didConfirm(String abstr, String contents) {
        this.abstr = checkNotBlank(abstr);
        this.contents = checkNotBlank(contents);
        getView().updateContentsIndicator();
    }

    @Override
    public void didCancel() {

    }

    @Override
    public void didCreateSigner(Signer signer) {
        toSave.save(controller, signer);
        confirmed(toSave);
    }

    void addGroup() {
        GroupPresenter groupPresenter = GroupView.createGroupPresenter(null, data.getSettings(), getApplication());
        if (groupListener == null) {
            groupListener = new DataListener<Group>() {
                @Override
                public boolean didConfirmData(Group group) {
                    group.getOrCreateController();
                    Group replaced = data.addGroup(group);
                    boolean saveGroup = group.isSave();
                    if (saveGroup || replaced != null && replaced.isSave() || data.getSettings().isSaveGroups()) {
                        data.getSettings().setSaveGroups(saveGroup);
                        saveData();
                    }
                    getView().updateGroups(group);
                    return true;
                }
            };
        }
        groupPresenter.setListener(groupListener);
        presentModally(groupPresenter, "Add group", false);
    }

    void addUser() {
        UserPresenter userPresenter = UserView.createUserPresenter(null, data.getSettings(), getApplication());
        if (userListener == null) {
            userListener = new DataListener<User>() {
                @Override
                public boolean didConfirmData(User user) {
                    data.addUser(user);
                    boolean saveUser = user.isSave();
                    if (saveUser || data.getSettings().isSaveUsers()) {
                        data.getSettings().setSaveUsers(saveUser);
                        saveData();
                    }
                    if (user.getMultihash() == null) {
                        user.getOrCreateNewUserObject();
                    }
                    else {
                        user.setProgressChangeListener((progressObserver) -> {
                            if (user.getUserObject() != null) {
                                getView().updateUsers(user);
                            }
                            else if (user.getFailedTask() != null) {
                                user.setProgressChangeListener(null);
                                getView().showError(user.getFailureMessage(), user.getFailure());
                            }
                        });
                        user.getOrLoadUserObject();
                    }
                    getView().updateUsers(user);
                    return true;
                }
            };
        }
        userPresenter.setListener(userListener);
        presentModally(userPresenter, "Add user", false);
    }

    void showEditor(String importValue) {
        if (isNullOrBlank(importValue)) {
            showEditor(abstr, contents);
        }
        else {
            URL url = null;
            try {
                url = new URL(importValue);
            }
            catch (Exception ignored) {
                // ignore
            }
            if (url == null) {
                getView().showMessage("Can't import the document. Please fix the import URL or clear it.");
            }
            else {
                final File file;
                if ("file".equals(url.getProtocol())) {
                    try {
                        file = new File(url.toURI());
                    }
                    catch (URISyntaxException e) {
                        getView().showMessage("Can't import the document from the given local file.");
                        return;
                    }
                }
                else {
                    file = null;
                }
                final URL finalURL = url;
                Task<Void> task = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        final String markdown;
                        if (file == null || file.getName().contains(".htm")) {
                            Remark remark = new Remark();
                            markdown = file == null ? remark.convert(finalURL, 15000) : remark.convert(file);
                        }
                        else {
                            BufferedReader br = new BufferedReader(new FileReader(file));
                            StringBuilder sb = new StringBuilder(INITIAL_MD_FILE_STRING_BUILDER_SIZE);
                            String line;
                            try {
                                while ((line = br.readLine()) != null) {
                                    sb.append(line);
                                }
                                markdown = sb.toString();
                            }
                            catch (Exception e) {
                                getView().showError("Failed to read the local file.", e);
                                return null;
                            }
                            finally {
                                br.close();
                            }
                        }
                        Platform.runLater(() -> {
                            showEditor(null, markdown == null ? "Failed to import from " + finalURL : markdown);
                        });
                        return null;
                    }
                };
                new Thread(task).start();
            }
        }
    }

    void showEditor(String abstr, String contents) {
        EditorPresenter editorPresenter = EditorView.createEditorPresenter(abstr, contents, getScene(),
                getApplication());
        editorPresenter.setListener(this);
        Scene scene = editorPresenter.getScene();
        getStage().setScene(scene);
    }

    void clearDocument() {
        this.abstr = null;
        this.contents = null;
        getView().updateContentsIndicator();
    }

    void confirmed(String importValue, Group group) {
        Document edited = getData();
        if (edited == null || edited.getMultihash() == null || edited.getDocumentObject() == null) {
            if (importValue == null) {
                getView().showMessage(
                        "Please enter the multihash of the document to load, or a valid URL and/or click the edit button.");
            }
            else {
                Document document = new Document(group, importValue);
                boolean changed = group != null;
                Group oldGroup = edited == null ? null : edited.getGroup();
                if (oldGroup != null && (oldGroup != group || !isEqual(edited.getMultihash(), importValue))) {
                    oldGroup.removeStandaloneDocument(edited.getMultihash());
                    changed = true;
                }
                if (changed) {
                    saveData();
                }
                confirmed(document);
            }
        }
        else {
            getView().showMessage("Please enter a valid URL and/or click the edit button.");
        }
    }

    void confirmed(org.projectjinxers.model.Document data, Group group, User user, String importURL,
            boolean didNotConfirmContents) {
        if (group == null || user == null) {
            getView().showMessage("Please select a group and a user.");
            return;
        }
        if (abstr == null && contents == null
                && (!(data instanceof Review) || !Boolean.TRUE.equals(((Review) data).getApprove()))) {
            getView().showMessage("Document without mandatory abstract or contents.");
            return;
        }
        if (didNotConfirmContents && importURL != null && !getView().askForConfirmation(
                "There is an editable import location, but you haven't confirmed the imported contents. Do you really want to save this review?")) {
            return;
        }
        Document edited = getData();
        IPLDObject<org.projectjinxers.model.Document> documentObject = edited == null ? null
                : edited.getDocumentObject();
        IPLDObject<ModelState> modelStateObject;
        IPLDObject<UserState> userState = null;
        ModelController controller = group.getOrCreateController();
        if (group.isInitializingController()) {
            getView().showMessage("Please wait until the model controller has been initialized.");
            return;
        }
        modelStateObject = controller.getCurrentValidatedState();
        String userHash = user.getMultihash();
        if (userHash == null) {
            userState = new IPLDObject<>(new UserState(user.getOrCreateNewUserObject()));
        }
        else {
            if (modelStateObject != null) {
                ModelState modelState = modelStateObject.getMapped();
                userState = modelState.expectUserState(userHash);
                if (userState != null && reviewed != null) {
                    Map<String, IPLDObject<org.projectjinxers.model.Document>> docs = userState.getMapped()
                            .getAllDocuments();
                    if (docs != null) {
                        String reviewedHash = reviewed.getMultihash();
                        for (IPLDObject<org.projectjinxers.model.Document> doc : docs.values()) {
                            org.projectjinxers.model.Document mapped = doc.getMapped();
                            if (mapped instanceof Review
                                    && reviewedHash.equals(((Review) mapped).getDocument().getMultihash())) {
                                getView().showMessage("The user has already reviewed the document.");
                                return;
                            }
                        }
                    }
                }
            }
            if (userState == null) {
                userState = new IPLDObject<>(new UserState(user.getUserObject()));
            }
        }
        IPLDObject<org.projectjinxers.model.Document> toSave;
        if (documentObject == null) {
            if (abstr == null && contents == null) {
                toSave = new IPLDObject<>(data.update(null, userState));
            }
            else {
                toSave = new IPLDObject<>(
                        data.update(new IPLDObject<>(new DocumentContents(abstr, contents)), userState));
            }
        }
        else {
            org.projectjinxers.model.Document document = documentObject.getMapped();
            if (document.checkUnchanged(data, abstr, contents)) {
                // TODO: when implementing links, save a flag and show this message after the user selected the links
                // and there are still no changes.
                getView().showMessage("No changes.");
                return;
            }
            org.projectjinxers.model.Document updated;
            if (abstr == null && contents == null) {
                updated = document.update(data, null, documentObject, userState);
            }
            else {
                updated = document.update(data, new IPLDObject<>(new DocumentContents(abstr, contents)), documentObject,
                        userState);
            }
            toSave = new IPLDObject<>(updated);
        }
        this.controller = controller;
        this.toSave = new Document(group, user, toSave, importURL, modelStateObject);
        SigningPresenter signingPresenter = SigningView.createSigningPresenter(user, getApplication());
        signingPresenter.setListener(this);
        presentModally(signingPresenter, "Sign document", false);
    }

    private void saveData() {
        try {
            data.save();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

}
