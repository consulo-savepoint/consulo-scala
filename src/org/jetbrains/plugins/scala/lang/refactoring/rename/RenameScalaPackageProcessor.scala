package org.jetbrains.plugins.scala
package lang.refactoring.rename

import com.intellij.refactoring.rename.RenamePsiPackageProcessor
import com.intellij.psi.{PsiJavaPackage, PsiElement}
import java.util
import caches.ScalaShortNamesCacheManager

/**
 * @author Alefas
 * @since 06.11.12
 */
class RenameScalaPackageProcessor extends RenamePsiPackageProcessor with ScalaRenameProcessor {
  override def prepareRenaming(element: PsiElement, newName: String, allRenames: util.Map[PsiElement, String]) {
    element match {
      case p: PsiJavaPackage =>
        val po = ScalaShortNamesCacheManager.getInstance(element.getProject).getPackageObjectByName(p.getQualifiedName, element.getResolveScope)
        if (po != null && po.name != "`package`") {
          allRenames.put(po, newName)
        }
      case _ =>
    }
  }
}
