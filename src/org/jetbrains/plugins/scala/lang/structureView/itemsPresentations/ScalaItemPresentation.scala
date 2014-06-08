package org.jetbrains.plugins.scala
package lang
package structureView
package itemsPresentations


import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import javax.swing._
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.ide.IconDescriptorUpdaters
;

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/
abstract class ScalaItemPresentation(protected val myElement: PsiElement) extends ColoredItemPresentation {
  def getLocationString: String = null

  def getIcon(open: Boolean): Icon = {
    IconDescriptorUpdaters.getIcon(myElement, Iconable.ICON_FLAG_OPEN)
  }

  def getTextAttributesKey: TextAttributesKey = null
}