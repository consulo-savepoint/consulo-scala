package org.jetbrains.plugins.scala
package debugger

import com.intellij.openapi.diagnostic.Logger
import java.util
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTrait, ScObject}
import com.intellij.psi._
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.debugger.engine.{DebugProcess, DebugProcessImpl, CompoundPositionManager}
import com.intellij.debugger.{NoDataException, PositionManager, SourcePosition}
import consulo.internal.com.sun.jdi.{ClassNotPreparedException, AbsentInformationException, Location, ReferenceType}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClauses
import org.jetbrains.plugins.scala.lang.resolve.ResolvableReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScMacroDefinition
import com.intellij.psi.util.PsiTreeUtil
import consulo.internal.com.sun.jdi.request.ClassPrepareRequest
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import com.intellij.openapi.util.{Computable, Ref}
import ScalaPositionManager._
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.util.macroDebug.ScalaMacroDebuggingUtil
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.project.Project
import com.intellij.util.{Processor, Query}
import org.jetbrains.annotations.{NotNull, Nullable}
import scala.annotation.tailrec
import com.intellij.openapi.editor.Document

/**
 * @author ilyas
 */
class ScalaPositionManager(debugProcess: DebugProcess) extends PositionManager {
  def getDebugProcess = debugProcess

  @NotNull def locationsOfLine(refType: ReferenceType, position: SourcePosition): util.List[Location] = {
    try {
      val line: Int = position.getLine + 1
      val locations: util.List[Location] =
        if (getDebugProcess.getVirtualMachineProxy.versionHigher("1.4"))
          refType.locationsOfLine(DebugProcess.JAVA_STRATUM, null, line)
        else refType.locationsOfLine(line)
      if (locations == null || locations.isEmpty) throw new NoDataException
      locations
    }
    catch {
      case e: AbsentInformationException => {
        throw new NoDataException
      }
    }
  }

  private def findReferenceTypeSourceImage(position: SourcePosition): ScalaPsiElement = {
    if (position == null) return null
    @tailrec
    def findSuitableParent(element: PsiElement): PsiElement = {
      element match {
        case null => null
        case elem @ (_: ScForStatement | _: ScTypeDefinition | _: ScFunctionExpr) => elem
        case extBlock: ScExtendsBlock if extBlock.isAnonymousClass => extBlock
        case caseCls: ScCaseClauses if caseCls.getParent.isInstanceOf[ScBlockExpr] => caseCls
        case expr: ScExpression if ScalaPsiUtil.isByNameArgument(expr) || isInsideMacro(position) => expr
        case elem => findSuitableParent(elem.getParent)
      }
    }

    val element = nonWhitespaceElement(position)
    findSuitableParent(element).asInstanceOf[ScalaPsiElement]
  }

  def nonWhitespaceElement(position: SourcePosition): PsiElement = {
    if (position == null) return null

    val file = position.getFile
    @tailrec
    def nonWhitespaceInner(element: PsiElement, document: Document): PsiElement = {
      element match {
        case null => null
        case ws: PsiWhiteSpace if document.getLineNumber(element.getTextRange.getEndOffset) == position.getLine =>
          val nextElement = file.findElementAt(element.getTextRange.getEndOffset)
          nonWhitespaceInner(nextElement, document)
        case _ => element
      }
    }
    if (!file.isInstanceOf[ScalaFile]) null
    else {
      val firstElement = file.findElementAt(position.getOffset)
      try {
        val document = PsiDocumentManager.getInstance(file.getProject).getDocument(file)
        nonWhitespaceInner(firstElement, document)
      }
      catch {
        case t: Throwable => firstElement
      }
    }
  }

  private def isInsideMacro(position: SourcePosition): Boolean = {
    val element: PsiElement = nonWhitespaceElement(position)
    var call = PsiTreeUtil.getParentOfType(element, classOf[ScMethodCall])
    while (call != null) {
      call.getEffectiveInvokedExpr match {
        case resRef: ResolvableReferenceElement =>
          if (resRef.resolve().isInstanceOf[ScMacroDefinition]) return true
        case _ =>
      }
      call = PsiTreeUtil.getParentOfType(call, classOf[ScMethodCall])
    }
    false
  }

  private def findEnclosingTypeDefinition(position: SourcePosition): Option[ScTypeDefinition] = {
    if (position == null) return None
    @tailrec
    def notLocalEnclosingTypeDefinition(element: PsiElement): Option[ScTypeDefinition] = {
      PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition]) match {
        case null => None
        case td if ScalaPsiUtil.isLocalClass(td) => notLocalEnclosingTypeDefinition(td.getParent)
        case td => Some(td)
      }
    }

    val element = nonWhitespaceElement(position)
    notLocalEnclosingTypeDefinition(element)

  }

  def createPrepareRequest(requestor: ClassPrepareRequestor, position: SourcePosition): ClassPrepareRequest = {
    val qName = new Ref[String](null)
    val waitRequestor = new Ref[ClassPrepareRequestor](null)
    ApplicationManager.getApplication.runReadAction(new Runnable {
      def run() {
        val sourceImage: ScalaPsiElement = ApplicationManager.getApplication.runReadAction(new Computable[ScalaPsiElement] {
          def compute: ScalaPsiElement = {
            findReferenceTypeSourceImage(position)
          }
        })
        val insideMacro: Boolean = ApplicationManager.getApplication.runReadAction(new Computable[Boolean] {
          def compute: Boolean = {
            isInsideMacro(position)
          }
        })

        def isLocalOrUnderDelayedInit(definition: PsiClass): Boolean = {
          val isDelayed = definition match {
            case obj: ScObject =>
              val manager: ScalaPsiManager = ScalaPsiManager.instance(obj.getProject)
              val clazz: PsiClass = manager.getCachedClass(obj.getResolveScope, "scala.DelayedInit")
              clazz != null && manager.cachedDeepIsInheritor(obj, clazz)
            case _ => false
          }
          ScalaPsiUtil.isLocalClass(definition) || isDelayed
        }

        sourceImage match {
          case typeDef: ScTypeDefinition if !isLocalOrUnderDelayedInit(typeDef) =>
            val specificName = getSpecificName(typeDef.getQualifiedNameForDebugger, typeDef.getClass)
            qName.set(if (insideMacro) specificName + "*" else specificName)
          case _ if sourceImage != null =>
          /*condition in previous version of this file just listed
          all possibilities we could get from findReferenceTypeSourceImage(position)*/
            findEnclosingTypeDefinition(position)
                    .foreach(typeDef => qName.set(typeDef.getQualifiedNameForDebugger + (if (insideMacro) "*" else "$*")))
          case null =>
            findEnclosingTypeDefinition(position)
                    .foreach(typeDef => qName.set(getSpecificName(typeDef.getQualifiedNameForDebugger, typeDef.getClass)))
        }
        // Enclosing type definition is not found
        if (qName.get == null) {
          if (position.getFile.isInstanceOf[ScalaFile]) {
            qName.set(SCRIPT_HOLDER_CLASS_NAME + "*")
          }
        }
        waitRequestor.set(new ScalaPositionManager.MyClassPrepareRequestor(position, requestor))
      }
    })

    if (qName.get == null || waitRequestor.get == null) throw new NoDataException
    getDebugProcess.getRequestsManager.createClassPrepareRequest(waitRequestor.get, qName.get)
  }

  def getSourcePosition(location: Location): SourcePosition = {
    if (location == null) throw new NoDataException
    val psiFile: PsiFile = getPsiFileByLocation(getDebugProcess.getProject, location)
    if (psiFile == null) throw new NoDataException
    val lineNumber: Int = calcLineIndex(location)
    if (lineNumber < 0) throw new NoDataException
    SourcePosition.createFromLine(psiFile, lineNumber)
  }

  private def calcLineIndex(location: Location): Int = {
    LOG.assertTrue(getDebugProcess != null)
    if (location == null) return -1
    try {
      location.lineNumber - 1
    }
    catch {
      case e: InternalError => return -1
    }
  }

  @Nullable private def getPsiFileByLocation(project: Project, location: Location): PsiFile = {
    if (location == null) return null
    val refType = location.declaringType
    if (refType == null) return null
    val originalQName = refType.name.replace('/', '.')
    val searchScope: GlobalSearchScope = getDebugProcess.getSearchScope
    if (originalQName.startsWith(SCRIPT_HOLDER_CLASS_NAME)) {
      try {
        val sourceName = location.sourceName
        val files: Array[PsiFile] = FilenameIndex.getFilesByName(project, sourceName, searchScope)
        if (files.length == 1) return files(0)
      }
      catch {
        case e: AbsentInformationException => return null
      }
    }
    val dollar: Int = originalQName.indexOf('$')
    val qName = if (dollar >= 0) originalQName.substring(0, dollar) else originalQName
    val classes = ScalaShortNamesCacheManager.getInstance(project).getClassesByFQName(qName, searchScope)
    val clazz: PsiClass = if (classes.length == 1) classes.apply(0) else null
    if (clazz != null && clazz.isValid && !ScalaMacroDebuggingUtil.isEnabled) {
      return clazz.getNavigationElement.getContainingFile
    }
    val directoryIndex: DirectoryIndex = DirectoryIndex.getInstance(project)
    val dotIndex = qName.lastIndexOf(".")
    val packageName = if (dotIndex > 0) qName.substring(0, dotIndex) else ""
    val query: Query[VirtualFile] = directoryIndex.getDirectoriesByPackageName(packageName, true)
    val fileNameWithoutExtension = if (dotIndex > 0) qName.substring(dotIndex + 1) else qName
    val fileNames: util.Set[String] = new util.HashSet[String]
    import scala.collection.JavaConversions._
    for (extention <- ScalaLoader.SCALA_EXTENSIONS) {
      fileNames.add(fileNameWithoutExtension + "." + extention)
    }
    val result = new Ref[PsiFile]
    query.forEach(new Processor[VirtualFile] {
      def process(vDir: VirtualFile): Boolean = {
        for (fileName <- fileNames) {
          val vFile: VirtualFile = vDir.findChild(fileName)
          if (vFile != null) {
            val psiFile: PsiFile = PsiManager.getInstance(project).findFile(vFile)
            val debugFile: PsiFile = ScalaMacroDebuggingUtil.loadCode(psiFile, force = false)
            if (debugFile != null) {
              result.set(debugFile)
              return false
            }
            if (psiFile.isInstanceOf[ScalaFile]) {
              result.set(psiFile)
              return false
            }
          }
        }
        true
      }
    })
    result.get
  }

  @NotNull def getAllClasses(position: SourcePosition): util.List[ReferenceType] = {
    val result = ApplicationManager.getApplication.runReadAction(new Computable[util.List[ReferenceType]] {
      def compute(): util.List[ReferenceType] = {
        val sourceImage = findReferenceTypeSourceImage(position)
        sourceImage match {
          case definition: ScTypeDefinition =>
            val qName = getSpecificName(definition.getQualifiedNameForDebugger, definition.getClass)
            if (qName != null) getDebugProcess.getVirtualMachineProxy.classesByName(qName)
            else util.Collections.emptyList[ReferenceType]
          case _ =>
            val qName = findEnclosingTypeDefinition(position).map(typeDef => typeDef.getQualifiedNameForDebugger)
            def hasLocations(refType: ReferenceType): Boolean = {
              var hasLocations = false
              try {
                hasLocations = refType.locationsOfLine(position.getLine + 1).size > 0
              } catch {
                case ignore @ (_: AbsentInformationException | _: ClassNotPreparedException) =>
              }
              hasLocations
            }
            import scala.collection.JavaConverters._
            qName.map { name =>
              val outers = getDebugProcess.getVirtualMachineProxy.allClasses.asScala
              val sameStart = outers.filter(_.name.startsWith(name))
              sameStart.filter(hasLocations).asJava
            }.getOrElse(util.Collections.emptyList[ReferenceType])
        }
      }
    })
    if (result == null || result.isEmpty) throw new NoDataException
    result
  }
}

object ScalaPositionManager {
  private val LOG: Logger = Logger.getInstance("#com.intellij.debugger.engine.PositionManagerImpl")
  private val SCRIPT_HOLDER_CLASS_NAME: String = "Main$$anon$1"

  private def getSpecificName(name: String, clazzClass: Class[_ <: PsiClass]): String = {
    if (classOf[ScObject].isAssignableFrom(clazzClass)) name + "$"
    else if (classOf[ScTrait].isAssignableFrom(clazzClass)) name + "$class"
    else name
  }

  private class MyClassPrepareRequestor(position: SourcePosition, requestor: ClassPrepareRequestor) extends ClassPrepareRequestor {
   def processClassPrepare(debuggerProcess: DebugProcess, referenceType: ReferenceType) {
      val positionManager: CompoundPositionManager = debuggerProcess.asInstanceOf[DebugProcessImpl].getPositionManager
      if (positionManager.locationsOfLine(referenceType, position).size > 0) {
        requestor.processClassPrepare(debuggerProcess, referenceType)
      }
      else {
        val positionClasses: util.List[ReferenceType] = positionManager.getAllClasses(position)
        if (positionClasses.contains(referenceType)) {
          requestor.processClassPrepare(debuggerProcess, referenceType)
        }
      }
    }
  }
}
