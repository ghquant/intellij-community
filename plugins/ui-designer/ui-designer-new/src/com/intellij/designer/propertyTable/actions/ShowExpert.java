/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.propertyTable.actions;

import com.intellij.designer.DesignerBundle;
import com.intellij.designer.DesignerToolWindowManager;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.util.IconLoader;

/**
 * @author Alexander Lobas
 */
public class ShowExpert extends ToggleAction {
  public ShowExpert() {
    Presentation presentation = getTemplatePresentation();
    String text = DesignerBundle.message("designer.properties.show.expert");
    presentation.setText(text);
    presentation.setDescription(text);
    presentation.setIcon(IconLoader.getIcon("/com/intellij/designer/icons/filter.png"));
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    PropertyTable table = DesignerToolWindowManager.getInstance(e.getProject()).getPropertyTable();
    return table.isShowExpert();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    PropertyTable table = DesignerToolWindowManager.getInstance(e.getProject()).getPropertyTable();
    table.showExpert(state);
  }
}