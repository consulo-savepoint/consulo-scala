package org.jetbrains.plugins.scala
package compiler

import com.intellij.openapi.project.{DumbAware, Project}
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.wm.{StatusBar, StatusBarWidget, WindowManager}
import com.intellij.openapi.wm.StatusBarWidget.PlatformType
import config.ScalaFacet
import java.awt.event.{ActionEvent, ActionListener, MouseEvent}
import javax.swing.Timer
import com.intellij.openapi.util.IconLoader
import com.intellij.util.Consumer
import com.intellij.notification.{NotificationType, NotificationDisplayType, Notifications, Notification}
import icons.Icons
import java.awt.Point
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.{AnSeparator, AnActionEvent, AnAction, DefaultActionGroup}
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.icons.AllIcons

/**
 * Pavel Fatin
 */

class FscServerManager(project: Project) extends ProjectComponent {
  private val IconRunning = Icons.FSC

  private val IconStopped = IconLoader.getDisabledIcon(IconRunning)

  private val timer = new Timer(1000, TimerListener)

  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {
   // registry.registerListener(ScalaFacet.Id, FacetListener)
    configureWidget()
    timer.setRepeats(true)
    timer.start()
  }

  def projectClosed() {
   // registry.unregisterListener(ScalaFacet.Id, FacetListener)
    configureWidget()
    timer.stop()
  }

  def getComponentName = getClass.getSimpleName

  def configureWidget() {
    (applicable, installed) match {
      case (true, true) => // do nothing
      case (true, false) => {
        bar.addWidget(Widget, "before Position", project)
        installed = true
      }
      case (false, true) => {
        removeWidget()
      }
      case (false, false) => // do nothing
    }
  }

  def removeWidget() {
    if (installed) {
      bar.removeWidget(Widget.ID)
      installed = false
    }
  }

  private def updateWidget() {
    bar.updateWidget(Widget.ID)
  }

  private def applicable = running ||
                  ScalacSettings.getInstance(project).INTERNAL_SERVER &&
                  ScalaFacet.findIn(project).exists(_.fsc)

  private def running = launcher.running

  private var installed = false

  private def launcher = project.getComponent(classOf[FscServerLauncher])

  private def bar = WindowManager.getInstance.getStatusBar(project)


  private object Widget extends StatusBarWidget {
    def ID = "FSC"

    def getPresentation(platformType : PlatformType) = Presentation

    def install(statusBar: StatusBar) {}

    def dispose() {}

    object Presentation extends StatusBarWidget.IconPresentation {
      def getIcon = if(running) IconRunning else IconStopped

      def getClickConsumer = ClickConsumer

      def getTooltipText = title

      object ClickConsumer extends Consumer[MouseEvent] {
        def consume(t: MouseEvent) {
          toggleList(t)
        }
      }
    }
  }

  private def title = "Scala project FSC%s".format(launcher.compilerVersion.map(_.formatted(" (%s)")).mkString)

  private def toggleList(e: MouseEvent) {
    val mnemonics = JBPopupFactory.ActionSelectionAid.MNEMONICS
    val group = new DefaultActionGroup(Start, Reset, Stop, AnSeparator.getInstance, Configure)
    val context = DataManager.getInstance.getDataContext(e.getComponent)
    val popup = JBPopupFactory.getInstance.createActionGroupPopup(title, group, context, mnemonics, true)
    val dimension = popup.getContent.getPreferredSize
    val at = new Point(0, -dimension.height)
    popup.show(new RelativePoint(e.getComponent, at))
  }

  private object Start extends AnAction("&Run", "Start project FSC", AllIcons.Actions.Execute) with DumbAware {
    override def update(e: AnActionEvent) {
      e.getPresentation.setEnabled(!launcher.running)
    }

    def actionPerformed(e: AnActionEvent) {
      launcher.init()
    }
  }

  private object Reset extends AnAction("R&eset", "Reset project FSC", AllIcons.Actions.SyncPanels) with DumbAware {
    override def update(e: AnActionEvent) {
      e.getPresentation.setEnabled(launcher.running)
    }

    def actionPerformed(e: AnActionEvent) {
      launcher.reset()

      val notification = new Notification("scala", title, "Reset", NotificationType.INFORMATION)
      Notifications.Bus.register("scala", NotificationDisplayType.BALLOON)
      Notifications.Bus.notify(notification, project)
    }
  }

  private object Stop extends AnAction("&Stop", "Shutdown project FSC", AllIcons.Actions.Suspend) with DumbAware {
    override def update(e: AnActionEvent) {
      e.getPresentation.setEnabled(launcher.running)
    }

    def actionPerformed(e: AnActionEvent) {
      launcher.stop()
    }
  }

  private object Configure extends AnAction("&Configure...", "Configure project FSC", AllIcons.General.Settings) with DumbAware {
    def actionPerformed(e: AnActionEvent) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, "Scala Compiler")
    }
  }        /*

  private object FacetListener extends ProjectWideFacetAdapter[ScalaFacet]() {
    override def facetAdded(facet: ScalaFacet) {
      configureWidget()
    }

    override def facetRemoved(facet: ScalaFacet) {
      configureWidget()
    }

    override def facetConfigurationChanged(facet: ScalaFacet) {
      configureWidget()
    }
  }   */

  private object TimerListener extends ActionListener {
    private var wasRunning = false

    def actionPerformed(e: ActionEvent) {
      val nowRunning = running

      if (installed || nowRunning) updateWidget()

      wasRunning -> nowRunning match {
        case (false, true) =>
//          val notification = new Notification("scala", title, "Startup", NotificationType.INFORMATION)
//          Notifications.Bus.register("scala", NotificationDisplayType.BALLOON)
//          Notifications.Bus.notify(notification, project)
        case (true, false) =>
//          val notification = new Notification("scala", title, "Shutdown", NotificationType.INFORMATION)
//          Notifications.Bus.register("scala", NotificationDisplayType.BALLOON)
//          Notifications.Bus.notify(notification, project)
        case _ =>
      }

      wasRunning = nowRunning

      val errors = launcher.errors()

      if (errors.nonEmpty) {
        val notification = new Notification("scala", title, errors.mkString, NotificationType.ERROR)
        Notifications.Bus.register("scala", NotificationDisplayType.BALLOON)
        Notifications.Bus.notify(notification, project)
      }
    }
  }
}