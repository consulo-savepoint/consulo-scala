package org.jetbrains.plugins.scala.config

import java.lang.String
import com.intellij.openapi.module.{ModuleUtilCore, ModuleManager, Module}
import com.intellij.openapi.project.Project
import java.io.File
import org.jetbrains.plugins.scala.lang.languageLevel.ScalaLanguageLevel
import org.mustbe.consulo.scala.module.extension.ScalaModuleExtension

/**
 * Pavel.Fatin, 26.07.2010
 */

object ScalaFacet {
  def isPresentIn(module: Module) =  ModuleUtilCore.getExtension(module, classOf[ScalaModuleExtension]) != null
  
  def findIn(module: Module): Option[ScalaModuleExtension] =
    Option(ModuleUtilCore.getExtension(module, classOf[ScalaModuleExtension]))
  
  def findIn(modules: Array[Module]): Array[ScalaModuleExtension] = modules.flatMap(findIn(_).toList)
  
  def findModulesIn(project: Project) = ModuleManager.getInstance(project).getModules.filter(isPresentIn _)

  def isPresentIn(project: Project): Boolean = !findModulesIn(project).isEmpty

  def findIn(project: Project): Seq[ScalaModuleExtension] = ScalaFacet.findIn(ScalaFacet.findModulesIn(project))

  def findFirstIn(project: Project): Option[ScalaModuleExtension] = findIn(project).headOption
  
  def createIn(module: Module)(action: ScalaModuleExtension => Unit) {
    //TODO
  }
} 
            /*
@deprecated
class ScalaFacet(module: Module, name: String, 
                 configuration: ScalaFacetConfiguration, underlyingFacet: Facet[_ <: FacetConfiguration]) 
        extends ScalaFacetAdapter(module, name, configuration, underlyingFacet) {
  
  def compiler = Libraries.findBy(compilerLibraryId, module.getProject)
          .map(new CompilerLibraryData(_))

  def files: Seq[File] = compiler.toList.flatMap(_.files)

  def classpath: String = compiler.map(_.classpath).mkString

  def version: String = compiler.flatMap(_.version).mkString
  
  def javaParameters: Array[String] = getConfiguration.getState.javaParameters
  
  def javaParameters_=(parameters: Array[String]) {
    getConfiguration.getState.updateJavaParameters(parameters)
  }

  def compilerParameters: Array[String] = {
    val plugins = getConfiguration.getState.pluginPaths.map { path =>
      "-Xplugin:" + new CompilerPlugin(path, module).file.getPath
    }
    getConfiguration.getState.compilerParameters ++ plugins
  }
  
  def compilerParameters_=(parameters: Array[String]) {
    getConfiguration.getState.updateCompilerParameters(parameters)
  }
  
  def pluginPaths: Array[String] = getConfiguration.getState.pluginPaths
  
  def pluginPaths_=(paths: Array[String]) {
    getConfiguration.getState.pluginPaths = paths
  } 

  def fsc: Boolean = getConfiguration.getState.fsc

  def compilerLibraryId_=(id: LibraryId) {
    val data = getConfiguration.getState
    data.compilerLibraryName = id.name
    data.compilerLibraryLevel = id.level
  }

  def compilerLibraryId: LibraryId = {
    val data = getConfiguration.getState
    new LibraryId(data.compilerLibraryName, data.compilerLibraryLevel)
  }

  def basePackage: Option[String] = {
    val data = getConfiguration.getState
    Option(data.basePackage).filter(!_.isEmpty)
  }

  def basePackage_=(aPackage: Option[String]) {
    getConfiguration.getState.basePackage = aPackage.getOrElse("")
  }

  def languageLevel: ScalaLanguageLevel.Value = {
    ScalaLanguageLevel.withName(getConfiguration.getState.languageLevel)
  }

  def languageLevel_=(level: ScalaLanguageLevel.Value) {
    getConfiguration.getState.languageLevel = level.toString
  }
}      */