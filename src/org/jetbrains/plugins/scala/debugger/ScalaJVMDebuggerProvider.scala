package org.jetbrains.plugins.scala
package debugger

import com.intellij.debugger.engine.JVMDebugProvider
import com.intellij.psi.PsiFile

/**
 * Created by VISTALL on 08.06.14.
 */
class ScalaJVMDebuggerProvider extends JVMDebugProvider{
  override def supportsJVMDebugging(p1: PsiFile): Boolean = p1.getFileType == ScalaFileType.SCALA_FILE_TYPE
}
