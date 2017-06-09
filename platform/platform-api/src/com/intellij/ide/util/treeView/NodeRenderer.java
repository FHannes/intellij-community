/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.treeView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.navigation.ColoredItemPresentation;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class NodeRenderer extends ColoredTreeCellRenderer {
  @Override
  public void customizeCellRenderer(@NotNull JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    Object node = TreeUtil.getUserObject(value);

    if (node instanceof NodeDescriptor) {
      NodeDescriptor descriptor = (NodeDescriptor)node;
      // TODO: use this color somewhere
      Color color = descriptor.getColor();
      setIcon(descriptor.getIcon());
    }

    PresentationData presentation =
      node instanceof PresentableNodeDescriptor ? ((PresentableNodeDescriptor)node).getPresentation() :
      node instanceof NavigationItem ? ObjectUtils.tryCast(((NavigationItem)node).getPresentation(), PresentationData.class) :
      null;

    if (presentation != null) {
      Color color = node instanceof NodeDescriptor ? ((NodeDescriptor)node).getColor() : null;
      setIcon(presentation.getIcon(false));

      final List<PresentableNodeDescriptor.ColoredFragment> coloredText = presentation.getColoredText();
      Color forcedForeground = presentation.getForcedTextForeground();
      if (coloredText.isEmpty()) {
        String text = tree.convertValueToText(value.toString(), selected, expanded, leaf, row, hasFocus);
        SimpleTextAttributes simpleTextAttributes = getSimpleTextAttributes(
          presentation, forcedForeground != null ? forcedForeground : color, node);
        append(text, simpleTextAttributes);
        String location = presentation.getLocationString();
        if (!StringUtil.isEmpty(location)) {
          SimpleTextAttributes attributes = SimpleTextAttributes.merge(simpleTextAttributes, SimpleTextAttributes.GRAYED_ATTRIBUTES);
          append(presentation.getLocationPrefix() + location + presentation.getLocationSuffix(), attributes, false);
        }
      }
      else {
        boolean first = true;
        for (PresentableNodeDescriptor.ColoredFragment each : coloredText) {
          SimpleTextAttributes simpleTextAttributes = each.getAttributes();
          if (each.getAttributes().getFgColor() == null && forcedForeground != null) {
            simpleTextAttributes = addColorToSimpleTextAttributes(each.getAttributes(), forcedForeground);
          }
          if (first) {
            final TextAttributesKey textAttributesKey = presentation.getTextAttributesKey();
            if (textAttributesKey != null) {
              final TextAttributes forcedAttributes = getColorsScheme().getAttributes(textAttributesKey);
              if (forcedAttributes != null) {
                simpleTextAttributes = SimpleTextAttributes.merge(simpleTextAttributes, SimpleTextAttributes.fromTextAttributes(forcedAttributes));
              }
            }
            first = false;
          }
          // treat grayed text as non-main
          boolean isMain = simpleTextAttributes != SimpleTextAttributes.GRAYED_ATTRIBUTES;
          append(each.getText(), simpleTextAttributes, isMain);
        }
        String location = presentation.getLocationString();
        if (!StringUtil.isEmpty(location)) {
          append(presentation.getLocationPrefix() + location + presentation.getLocationSuffix(), SimpleTextAttributes.GRAY_ATTRIBUTES, false);
        }
      }

      setToolTipText(presentation.getTooltip());
    }
    else if (value != null) {
      String text = value.toString();
      if (node instanceof NodeDescriptor) {
        text = node.toString();
      }
      text = tree.convertValueToText(text, selected, expanded, leaf, row, hasFocus);
      if (text == null) {
        text = "";
      }
      append(text);
      setToolTipText(null);
    }
    if (!AbstractTreeUi.isLoadingNode(value)) {
      SpeedSearchUtil.applySpeedSearchHighlighting(tree, this, true, selected);
    }
  }

  @NotNull
  protected EditorColorsScheme getColorsScheme() {
    return EditorColorsManager.getInstance().getGlobalScheme();
  }

  @NotNull
  protected SimpleTextAttributes getSimpleTextAttributes(@NotNull PresentationData presentation, Color color, @NotNull Object node) {
    SimpleTextAttributes simpleTextAttributes = getSimpleTextAttributes(presentation, getColorsScheme());

    return addColorToSimpleTextAttributes(simpleTextAttributes, color);
  }

  private static SimpleTextAttributes addColorToSimpleTextAttributes(SimpleTextAttributes simpleTextAttributes, Color color) {
    if (color != null) {
      final TextAttributes textAttributes = simpleTextAttributes.toTextAttributes();
      textAttributes.setForegroundColor(color);
      simpleTextAttributes = SimpleTextAttributes.fromTextAttributes(textAttributes);
    }
    return simpleTextAttributes;
  }

  public static SimpleTextAttributes getSimpleTextAttributes(@Nullable final ItemPresentation presentation) {
    return getSimpleTextAttributes(presentation, EditorColorsManager.getInstance().getGlobalScheme());
  }
  
  public static SimpleTextAttributes getSimpleTextAttributes(@Nullable final ItemPresentation presentation,
                                                             @NotNull EditorColorsScheme colorsScheme)
  {
    if (presentation instanceof ColoredItemPresentation) {
      final TextAttributesKey textAttributesKey = ((ColoredItemPresentation) presentation).getTextAttributesKey();
      if (textAttributesKey == null) return SimpleTextAttributes.REGULAR_ATTRIBUTES;
      final TextAttributes textAttributes = colorsScheme.getAttributes(textAttributesKey);
      return textAttributes == null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.fromTextAttributes(textAttributes);
    }
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

}