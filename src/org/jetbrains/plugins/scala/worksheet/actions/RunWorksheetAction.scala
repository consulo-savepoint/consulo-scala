package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.openapi.actionSystem.{AnActionEvent, AnAction}
import lang.psi.api.ScalaFile
import com.intellij.execution._
import com.intellij.execution.configurations.JavaParameters
import com.intellij.util.PathUtil
import org.consulo.java.module.extension.JavaModuleExtension
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetViewerInfo
import com.intellij.icons.AllIcons
import com.intellij.openapi.keymap.{KeymapUtil, KeymapManager}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.execution.ui.ConsoleViewContentType
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler
import com.intellij.openapi.application.{ModalityState, ApplicationManager}
import org.jetbrains.plugins.scala
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager}
import com.intellij.openapi.roots.{ModuleRootManager, ProjectFileIndex}
import org.jetbrains.plugins.scala.config.{Libraries, CompilerLibraryData, ScalaFacet}
import com.intellij.openapi.module.{ModuleUtilCore, ModuleManager, Module}
import com.intellij.openapi.projectRoots.{JdkUtil, JavaSdkType}
import org.jetbrains.plugins.scala.compiler.ScalacSettings
import org.jetbrains.plugins.scala.worksheet.MacroPrinter
import com.intellij.execution.process.{ProcessEvent, ProcessAdapter, OSProcessHandler}
import com.intellij.ide.util.EditorHelper
import org.jetbrains.plugins.scala.worksheet.ui.WorksheetEditorPrinter
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.worksheet.server.WorksheetProcessManager
import com.intellij.internal.statistic.UsageTrigger
import com.intellij.openapi.vfs.VirtualFileWithId

/**
 * @author Ksenia.Sautina
 * @author Dmitry Naydanov        
 * @since 10/17/12
 */

class RunWorksheetAction extends AnAction with TopComponentAction {
  def actionPerformed(e: AnActionEvent) {
    RunWorksheetAction.runCompiler(e.getProject, false)
  }

  override def update(e: AnActionEvent) {
    val presentation = e.getPresentation
    presentation.setIcon(AllIcons.Actions.Execute)
    val shortcuts = KeymapManager.getInstance.getActiveKeymap.getShortcuts("Scala.RunWorksheet")
    if (shortcuts.length > 0) {
      val shortcutText = " (" + KeymapUtil.getShortcutText(shortcuts(0)) + ")"
      presentation.setText(ScalaBundle.message("worksheet.execute.button") + shortcutText)
    }

    updateInner(presentation, e.getProject)
  }

  override def actionIcon = AllIcons.Actions.Execute

  override def bundleKey = "worksheet.execute.button"

  override def shortcutId: Option[String] = Some("Scala.RunWorksheet")
}

object RunWorksheetAction {
  private val runnerClassName = "org.jetbrains.plugins.scala.worksheet.MyWorksheetRunner"

  def runCompiler(project: Project, auto: Boolean) {
    UsageTrigger.trigger("scala.worksheet")
    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor

    if (editor == null) return

    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    WorksheetProcessManager.stop(psiFile.getVirtualFile)

    psiFile match {
      case file: ScalaFile if file.isWorksheetFile =>
        val viewer = WorksheetViewerInfo getViewer editor

        if (viewer != null) {
          ApplicationManager.getApplication.invokeAndWait(new Runnable {
            override def run() {
              scala.extensions.inWriteAction {
                CleanWorksheetAction.resetScrollModel(viewer)
                if (!auto) CleanWorksheetAction.cleanWorksheet(file.getNode, editor, viewer, project)
              }
            }
          }, ModalityState.any())
        }

        def runnable() = {
          new WorksheetCompiler().compileAndRun(editor, file, (className: String, addToCp: String) => {
            ApplicationManager.getApplication invokeLater new Runnable {
              override def run() {
                executeWorksheet(file.getName, project, file.getContainingFile, className, addToCp)
              }
            }
          }, Option(editor), auto)
        }

        if (WorksheetCompiler isMakeBeforeRun psiFile) {
          CompilerManager.getInstance(project).make(
            getModuleFor(file),
            new CompileStatusNotification {
              override def finished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
                if (!aborted && errors == 0) runnable()
              }
            })
        } else runnable()
      case _ =>
    }
  }

  def executeWorksheet(name: String, project: Project, file: PsiFile, mainClassName: String, addToCp: String) {
    val virtualFile = file.getVirtualFile
    val params = createParameters(getModuleFor(file), mainClassName, Option(project.getBaseDir) map (_.getPath) getOrElse "", addToCp, "",
      virtualFile.getCanonicalPath) //todo extract default java options??

    setUpUiAndRun(params.createOSProcessHandler(), file)
  }

  private def createParameters(module: Module, mainClassName: String,
                               workingDirectory: String, additionalCp: String, consoleArgs: String, worksheetField: String) =  {
    import _root_.scala.collection.JavaConverters._

    if (module == null) throw new ExecutionException("Module is not specified")

    val project = module.getProject

    val facet = ScalaFacet.findIn(module).getOrElse {
      throw new ExecutionException("No Scala facet configured for module " + module.getName)
    }

    val rootManager = ModuleRootManager.getInstance(module)
    val sdk = ModuleUtilCore.getSdk(module, classOf[JavaModuleExtension[_]])
    if (sdk == null) {
      throw CantRunException.noJdkForModule(module)
    }

    val params = new JavaParameters()
    val files =
      if (facet.fsc) {
        val settings = ScalacSettings.getInstance(project)
        val lib: Option[CompilerLibraryData] = Libraries.findBy(settings.COMPILER_LIBRARY_NAME,
          settings.COMPILER_LIBRARY_LEVEL, project)
        lib match {
          case Some(compilerLib) => compilerLib.files
          case _ => facet.files
        }
      } else facet.files
    params.getClassPath.addAllFiles(files.asJava)
    params.setUseDynamicClasspath(JdkUtil.useDynamicClasspath(project))
    params.setUseDynamicVMOptions(JdkUtil.useDynamicVMOptions())
    params.getClassPath.add(PathUtil.getJarPathForClass(classOf[_root_.org.jetbrains.plugins.scala.worksheet.MyWorksheetRunner]))
    params.setWorkingDirectory(workingDirectory)
    params.setMainClass(runnerClassName)
    params.configureByModule(module, JavaParameters.JDK_AND_CLASSES_AND_TESTS)

    params.getClassPath.add(PathUtil.getJarPathForClass(classOf[MacroPrinter]))
    params.getClassPath.add(additionalCp)
    params.getProgramParametersList addParametersString worksheetField
    if (!consoleArgs.isEmpty) params.getProgramParametersList addParametersString consoleArgs
    params.getProgramParametersList prepend mainClassName //IMPORTANT! this must be first program argument

    params
  }

  private def setUpUiAndRun(handler: OSProcessHandler, file: PsiFile) {
    val virtualFile = file.getVirtualFile

    val editor = EditorHelper openInEditor file

    val worksheetPrinter = WorksheetEditorPrinter.newWorksheetUiFor(editor, virtualFile)

    val myProcessListener: ProcessAdapter = new ProcessAdapter {
      override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
        val text = event.getText
        if (ConsoleViewContentType.NORMAL_OUTPUT == ConsoleViewContentType.getConsoleViewType(outputType)) {
          worksheetPrinter processLine text
        }
      }

      override def processTerminated(event: ProcessEvent): Unit = {
        worksheetPrinter.flushBuffer()
      }
    }

    worksheetPrinter.scheduleWorksheetUpdate()
    handler.addProcessListener(myProcessListener)

    handler.startNotify()
  }

  def getModuleFor(file: PsiFile) = file.getVirtualFile match {
    case _: VirtualFileWithId => ProjectFileIndex.SERVICE getInstance file.getProject getModuleForFile file.getVirtualFile
    case _ => ScalaFacet.findFirstIn(file.getProject).map(_.getModule).orNull
  }
}