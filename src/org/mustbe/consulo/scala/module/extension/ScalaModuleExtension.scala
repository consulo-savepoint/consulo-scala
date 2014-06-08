package org.mustbe.consulo.scala.module.extension

import org.consulo.module.extension.impl.ModuleExtensionImpl
import com.intellij.openapi.roots.ModifiableRootModel
import org.jetbrains.plugins.scala.config._
import java.io.File
import org.jetbrains.plugins.scala.lang.languageLevel.ScalaLanguageLevel

/**
 * Created by VISTALL on 08.06.14.
 */
class ScalaModuleExtension(id: String, model: ModifiableRootModel) extends ModuleExtensionImpl[ScalaModuleExtension](id, model) {
  private val data : ConfigurationData = new ConfigurationData()

  def compilerParameters: Array[String] = {
    val plugins = data.pluginPaths.map { path =>
      "-Xplugin:" + new CompilerPlugin(path, getModule).file.getPath
    }
    data.compilerParameters ++ plugins
  }

  def fsc: Boolean = data.fsc

  def compiler = Libraries.findBy(compilerLibraryId, getProject)
          .map(new CompilerLibraryData(_))

  def files: Seq[File] = compiler.toList.flatMap(_.files)

  def classpath: String = compiler.map(_.classpath).mkString

  def version: String = compiler.flatMap(_.version).mkString

  def javaParameters: Array[String] = data.javaParameters

  def compilerLibraryId: LibraryId = {
    new LibraryId(data.compilerLibraryName, data.compilerLibraryLevel)
  }

  def basePackage: Option[String] = {
    Option(data.basePackage).filter(!_.isEmpty)
  }

  def basePackage_=(aPackage: Option[String]) {
    data.basePackage = aPackage.getOrElse("")
  }

  def languageLevel: ScalaLanguageLevel.Value = {
    ScalaLanguageLevel.withName(data.languageLevel)
  }

  def languageLevel_=(level: ScalaLanguageLevel.Value) {
    data.languageLevel = level.toString
  }
}
