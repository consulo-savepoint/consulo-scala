package org.jetbrains.jps.incremental.scala

import java.io.File
import com.intellij.openapi.compiler.CompilerMessageCategory

/**
 * @author Pavel Fatin
 */
trait Client {
  def message(kind: CompilerMessageCategory, text: String, source: Option[File] = None, line: Option[Long] = None, column: Option[Long] = None)

  def error(text: String, source: Option[File] = None, line: Option[Long] = None, column: Option[Long] = None) {
    message(CompilerMessageCategory.ERROR, text, source, line, column)
  }

  def warning(text: String, source: Option[File] = None, line: Option[Long] = None, column: Option[Long] = None) {
    message(CompilerMessageCategory.WARNING, text, source, line, column)
  }

  def info(text: String, source: Option[File] = None, line: Option[Long] = None, column: Option[Long] = None) {
    message(CompilerMessageCategory.INFORMATION, text, source, line, column)
  }

  def trace(exception: Throwable)

  def progress(text: String, done: Option[Float] = None)

  def debug(text: String)

  def generated(source: File, module: File, name: String)

  def processed(source: File)

  def deleted(module: File)

  def isCanceled: Boolean
  
  def worksheetOutput(text: String) {}
  
  def compilationEnd() {}
}

