package org.jetbrains.plugins.scala
package decompiler

import java.io.IOException

import com.intellij.openapi.vfs.VirtualFile

import scala.annotation.tailrec

/**
 * @author ilyas
 */

object ScClsStubBuilderFactory {
  def canBeProcessed(file: VirtualFile): Boolean = {
    try {
      canBeProcessed(file, file.contentsToByteArray())
    } catch {
      case ex: IOException => false
      case u: UnsupportedOperationException => false //why we need to handle this?
    }
  }

  def canBeProcessed(file: VirtualFile, bytes: => Array[Byte]): Boolean = {
    val name: String = file.getNameWithoutExtension
    if (name.contains("$")) {
      val parent: VirtualFile = file.getParent
      @tailrec
      def checkName(name: String): Boolean = {
        val child: VirtualFile = parent.findChild(name + ".class")
        if (child != null) {
          val res = DecompilerUtil.isScalaFile(child)
          if (res) return true //let's handle it separately to avoid giving it for Java.
        }
        val index = name.lastIndexOf("$")
        if (index == -1) return false
        var newName = name.substring(0, index)
        while (newName.endsWith("$")) newName = newName.dropRight(1)
        checkName(newName)
      }

      checkName(name)
    } else {
      DecompilerUtil.isScalaFile(file, bytes)
    }
  }
}