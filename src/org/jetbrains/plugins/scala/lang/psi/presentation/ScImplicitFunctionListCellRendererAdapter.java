package org.jetbrains.plugins.scala.lang.psi.presentation;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.psi.PsiNamedElement;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alefas
 * @since 02.10.13
 */
public abstract class ScImplicitFunctionListCellRendererAdapter extends PsiElementListCellRenderer<PsiNamedElement> {
  @Override
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    return getListCellRendererComponentAdapter(list, value, index, isSelected, cellHasFocus);
  }

  public Component getSuperListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
  }

  public abstract Component getListCellRendererComponentAdapter(JList list,
                                                                Object value,
                                                                int index,
                                                                boolean isSelected,
                                                                boolean cellHasFocus);
}
