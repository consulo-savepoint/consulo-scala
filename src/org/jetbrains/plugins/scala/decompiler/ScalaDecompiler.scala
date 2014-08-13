package org.jetbrains.plugins.scala
package decompiler

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.compiled.{ClassFileDecompilers, ClsStubBuilder}
import com.intellij.psi.{FileViewProvider, PsiManager}

/**
 * @author VISTALL
 * @since 12.08.2014
 */
class ScalaDecompiler extends ClassFileDecompilers.Full {
  override def getStubBuilder: ClsStubBuilder = new ScalaClsStubBuilder()

  override def createFileViewProvider(p1: VirtualFile, p2: PsiManager, p3: Boolean): FileViewProvider = new ScClassFileViewProvider(p2, p1, p3, true)

  override def accepts(p1: VirtualFile): Boolean = ScClsStubBuilderFactory.canBeProcessed(p1)
}
