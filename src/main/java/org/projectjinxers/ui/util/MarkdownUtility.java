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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.dansoftware.mdeditor.MarkdownEditorControl;
import com.dansoftware.mdeditor.MarkdownEditorControl.ViewMode;
import com.dansoftware.mdeditor.MarkdownEditorSkin;
import com.vladsch.flexmark.ext.attributes.AttributeNode;
import com.vladsch.flexmark.ext.attributes.AttributesExtension;
import com.vladsch.flexmark.ext.attributes.AttributesNode;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.NodeVisitor;
import com.vladsch.flexmark.util.ast.VisitHandler;
import com.vladsch.flexmark.util.collection.iteration.ReversiblePeekingIterable;
import com.vladsch.flexmark.util.sequence.BasedSequence;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.image.ImageView;

/**
 * @author ProjectJinxers
 *
 */
public class MarkdownUtility {

    public static void fixSkippedProperties(MarkdownEditorControl contentsEditor) {
        contentsEditor.markdownProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                if (newValue != null && newValue.trim().length() > 0) {
                    Platform.runLater(() -> {
                        applySkippedAttributes(contentsEditor, newValue);
                    });
                }
            }
        });
    }

    private static void applySkippedAttributes(MarkdownEditorControl contentsEditor, String markdown) {
        ViewMode viewMode = contentsEditor.getViewMode();
        if (viewMode != ViewMode.EDITOR_ONLY) {
            MarkdownEditorSkin skin = (MarkdownEditorSkin) contentsEditor.getSkin();
            Parser parser = Parser.builder().extensions(Collections.singleton(AttributesExtension.create())).build();

            Document node = parser.parse(markdown);

            MDParser mdParser = new MDParser(node);
            mdParser.visitor.visitChildren(node);
            if (mdParser.imageAttributes != null) {
                Parent vbox = (Parent) skin.getChildren().get(0);
                // vbox > splitPane > previewArea
                ObservableList<Node> children = vbox.getChildrenUnmodifiable();
                for (Node child : children) {
                    if (child instanceof SplitPane) {
                        SplitPane splitPane = (SplitPane) child;
                        ObservableList<Node> items = splitPane.getItems();
                        ScrollPane scrollPane;
                        if (viewMode == ViewMode.PREVIEW_ONLY) {
                            scrollPane = (ScrollPane) items.get(0);
                        }
                        else {
                            scrollPane = (ScrollPane) items.get(1);
                        }
                        Parent preview = (Parent) scrollPane.getContent();
                        adjustViews(preview, mdParser.imageAttributes);
                    }
                }
            }
        }
    }

    private static void adjustViews(Parent parent, Map<String, AttributesNode> imageAttributes) {
        ObservableList<Node> childrenUnmodifiable = parent.getChildrenUnmodifiable();
        for (Node child : childrenUnmodifiable) {
            if (child instanceof ImageView) {
                String url = ((ImageView) child).getImage().getUrl();
                AttributesNode attributes = imageAttributes.get(url);
                if (attributes != null) {
                    adjustImageView((ImageView) child, attributes);
                }
            }
            else if (child instanceof Parent) {
                adjustViews((Parent) child, imageAttributes);
            }
        }
    }

    private static void adjustImageView(ImageView imageView, AttributesNode attributes) {
        double width = 0;
        double height = 0;
        ReversiblePeekingIterable<com.vladsch.flexmark.util.ast.Node> children = attributes.getChildren();
        for (com.vladsch.flexmark.util.ast.Node childNode : children) {
            AttributeNode attribute = (AttributeNode) childNode;
            BasedSequence name = attribute.getName();
            BasedSequence value = attribute.getValue();

            boolean isWidth;
            boolean isHeight;
            if (name.equals("width")) {
                isWidth = true;
                isHeight = false;
            }
            else {
                isWidth = false;
                if (name.equals("height")) {
                    isHeight = true;
                }
                else {
                    isHeight = false;
                }
            }
            if (isWidth || isHeight) {
                String val = value.toString();
                boolean isRelative;
                if (val.endsWith("px")) {
                    val = val.replace("px", "");
                    isRelative = false;
                }
                else if (val.endsWith("%")) {
                    val = val.replace("%", "");
                    isRelative = true;
                }
                else {
                    isRelative = false;
                }
                try {
                    double doubleVal = Double.parseDouble(val);
                    if (isWidth) {
                        width = doubleVal;
                        if (isRelative) {
                            width *= imageView.getParent().getParent().getBoundsInParent().getWidth();
                        }
                    }
                    else {
                        height = doubleVal;
                        if (isRelative) {
                            height *= imageView.getParent().getParent().getBoundsInParent().getHeight();
                        }
                    }
                }
                catch (NumberFormatException e) {

                }
            }

        }

        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPreserveRatio(true);
    }

    static class MDParser {

        private NodeVisitor visitor = new NodeVisitor(
                new VisitHandler<>(com.vladsch.flexmark.ast.Image.class, this::visit));

        private Map<String, AttributesNode> imageAttributes;

        MDParser(Document document) {

        }

        private void visit(com.vladsch.flexmark.ast.Image image) {
            com.vladsch.flexmark.util.ast.Node next = image.getNext();
            if (next instanceof AttributesNode) {
                if (imageAttributes == null) {
                    imageAttributes = new HashMap<>();
                }
                imageAttributes.put(image.getUrl().toString(), (AttributesNode) next);
            }
        }

    }

}
