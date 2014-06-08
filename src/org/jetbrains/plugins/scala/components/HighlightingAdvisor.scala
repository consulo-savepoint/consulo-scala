package org.jetbrains.plugins.scala.components

import org.intellij.lang.annotations.Language
import com.intellij.openapi.wm.{StatusBarWidget, WindowManager, StatusBar}
import com.intellij.openapi.wm.StatusBarWidget.PlatformType
import org.jetbrains.plugins.scala.icons.Icons
import java.awt.event.MouseEvent
import com.intellij.ide.DataManager
import com.intellij.util.{FileContentUtil, Consumer}
import collection.JavaConversions._
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.config.ScalaFacet
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext}
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util._
import com.intellij.openapi.components._
import com.intellij.notification._
import org.jetbrains.plugins.scala.util.NotificationUtil

@State(name = "HighlightingAdvisor", storages = Array(
  new Storage(id = "default", file = "$PROJECT_FILE$"),
    new Storage(id = "dir", file = "$PROJECT_CONFIG_DIR$/highlighting.xml", scheme = StorageScheme.DIRECTORY_BASED)))
class HighlightingAdvisor(project: Project) extends ProjectComponent with PersistentStateComponent[HighlightingSettings] {
  @Language("HTML")
  private val AdviceMessage = """
  <html>
   <body>
   <p><a href="http://confluence.jetbrains.net/display/SCA/Type-aware+highlighting">Type-aware highlighting</a>
    helps to discover type mismatches, unresolved symbols and many other type-related errors.</p>
   <br>
   <p>However, the feature is in beta and sometimes may report "false errors" in regular code.</p>
   <br>
   <a href="ftp://enable">Enable type-aware highlighting</a> (recommended) or <a href="ftp://disable">leave it disabled</a>
   </body>
  </html>"""

  @Language("HTML")
  private val EnabledMessage = """
  <html>
   <body>
   <p><a href="http://confluence.jetbrains.net/display/SCA/Type-aware+highlighting">Type-aware highlighting</a> has been enabled.</p>
   <!--<br>
   <a href="ftp://disable">Disable it again</a>-->
   </body>
  </html>"""

  @Language("HTML")
  private val DisabledMessage = """
  <html>
   <body>
   <p><a href="http://confluence.jetbrains.net/display/SCA/Type-aware+highlighting">Type-aware highlighting</a> has been disabled.</p>
   <!--<br>
   <a href="ftp://enable">Enable it again</a>-->
   </body>
  </html>"""

  private var installed = false

  private var settings = new HighlightingSettings()

  def getComponentName = "HighlightingAdvisor"

  def initComponent() {}

  def disposeComponent() {}

  def projectOpened() {
   /// registry.registerListener(ScalaFacet.Id, FacetListener)
    configureWidget()
    notifyIfNeeded()
  }

  def projectClosed() {
    ///registry.unregisterListener(ScalaFacet.Id, FacetListener)
    configureWidget()
  }

  def getState = settings

  def loadState(state: HighlightingSettings) {
    settings = state
  }

  private def configureWidget() {
    (applicable, installed) match {
      case (true, true) => // do nothing
      case (true, false) =>
        bar.addWidget(Widget, project)
        installed = true
      case (false, true) =>
        bar.removeWidget(Widget.ID)
        installed = false
      case (false, false) => // do nothing
    }
  }

  private def notifyIfNeeded() {
    if(settings.SUGGEST_TYPE_AWARE_HIGHLIGHTING && !enabled && applicable) {
      notify("Configure type-aware highlighting for the project", AdviceMessage, NotificationType.WARNING)
    }
  }

  private def notify(title: String, message: String, notificationType: NotificationType) {
    NotificationUtil.builder(project, message) setNotificationType notificationType setTitle title setHandler {
      case "enable" => enabled = true
      case "disable" => enabled = false
      case _ =>
    }
  }

  def toggle() {
    if(applicable) {
      enabled = !enabled
      TypeAwareHighlightingApplicationState.getInstance setSuggest enabled
    }
  }

  private def applicable = ScalaFacet.isPresentIn(project)

  def enabled = settings.TYPE_AWARE_HIGHLIGHTING_ENABLED

  private def enabled_=(enabled: Boolean) {
    settings.SUGGEST_TYPE_AWARE_HIGHLIGHTING = false

    if(this.enabled == enabled) return

    settings.TYPE_AWARE_HIGHLIGHTING_ENABLED = enabled

    updateWidget()
    reparseActiveFile()

    if (enabled)
      notify(status, EnabledMessage, NotificationType.INFORMATION)
    else
      notify(status, DisabledMessage, NotificationType.INFORMATION)
  }

  private def status = "Scala type-aware highlighting: %s"
          .format(if(enabled) "enabled" else "disabled")

  private def updateWidget() {
    bar.updateWidget(Widget.ID)
  }

  private def reparseActiveFile() {
    val context = DataManager.getInstance.getDataContextFromFocus
    context.doWhenDone(new AsyncResult.Handler[DataContext]() {
      def run(v: DataContext) {
        CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getData(v) match {
          case editor: EditorEx =>
            FileContentUtil.reparseFiles(project, Seq(editor.getVirtualFile), true)
          case _ => // do nothing
        }
      }
    })
  }

  private def bar = WindowManager.getInstance.getStatusBar(project)

  private object Widget extends StatusBarWidget {
    def ID = "TypeAwareHighlighting"

    def getPresentation(platformType: PlatformType) = Presentation

    def install(statusBar: StatusBar) {}

    def dispose() {}

    object Presentation extends StatusBarWidget.IconPresentation {
      def getIcon = if(enabled) Icons.TYPED else Icons.UNTYPED

      def getClickConsumer = ClickConsumer

      def getTooltipText = "%s (click to %s, or press Ctrl+Shift+Alt+E)"
              .format(status, if(enabled) "disable" else "enable")

      object ClickConsumer extends Consumer[MouseEvent] {
        def consume(t: MouseEvent) {
          toggle()
        }
      }
    }
  }
   /*
  private object FacetListener extends ProjectWideFacetAdapter[ScalaFacet]() {
    override def facetAdded(facet: ScalaFacet) {
      configureWidget()
      notifyIfNeeded()
    }

    override def facetRemoved(facet: ScalaFacet) {
      configureWidget()
    }
  }  */
}

object HighlightingAdvisor {
  def getInstance(project: Project) = project.getComponent(classOf[HighlightingAdvisor])
}
