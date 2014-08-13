package org.jetbrains.plugins.scala
package decompiler

import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.psi.compiled.ClsStubBuilder
import com.intellij.psi.stubs.{PsiFileStubImpl, PsiFileStub}
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.util.indexing.FileContent
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil.DecompilationResult
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.StubVersion

/**
 * @author VISTALL
 * @since 12.08.2014
 */
class ScalaClsStubBuilder extends ClsStubBuilder{
  override def getStubVersion: Int = StubVersion.STUB_VERSION

  override def buildFileStub(p1: FileContent): PsiFileStub[_] = {
    val DecompilationResult(_, source, text, _) = DecompilerUtil.decompile(p1.getFile, p1.getContent)
    val file = ScalaPsiElementFactory.createScalaFile(text.replace("\r", ""),
      PsiManager.getInstance(DefaultProjectFactory.getInstance().getDefaultProject))

    val adj = file.asInstanceOf[CompiledFileAdjuster]
    adj.setCompiled(c = true)
    adj.setSourceFileName(source)
    adj.setVirtualFile(p1.getFile)

    val fType = LanguageParserDefinitions.INSTANCE.forLanguage(ScalaFileType.SCALA_LANGUAGE).getFileNodeType
    val stub = fType.asInstanceOf[IStubFileElementType[PsiFileStub[PsiFile]]].getBuilder.buildStubTree(file)
    stub.asInstanceOf[PsiFileStubImpl[PsiFile]].clearPsi("Stub was built from decompiled file")
    stub.asInstanceOf[PsiFileStub[ScalaFile]]
  }
}
