package org.jetbrains.plugins.scala.compiler;

import com.intellij.compiler.CompilerException;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.impl.javaCompiler.BackendCompiler;
import com.intellij.compiler.impl.javaCompiler.BackendCompilerWrapper;
import com.intellij.compiler.make.CacheCorruptedException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.ex.CompileContextEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Chunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.config.ScalaFacet;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;
import scala.Option;

import java.util.Arrays;
import java.util.List;

/**
 * @author ven, ilyas, Pavel Fatin
 */
public class ScalaCompiler implements TranslatingCompiler {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.scala.compiler.ScalaCompiler");
  private Project myProject;
  private boolean myFsc;
  private static final FileTypeManager FILE_TYPE_MANAGER = FileTypeManager.getInstance();

  public ScalaCompiler(Project project, boolean fsc) {
    myProject = project;
    myFsc = fsc;
  }

  @NotNull
  public String getDescription() {
    return ScalaBundle.message("scala.compiler.description");
  }

  public boolean isCompilableFile(final VirtualFile file, CompileContext context) {
    if (!ScalaFacet.isPresentIn(myProject)) return false;

    boolean compilableByFileType = isCompilableByExtension(file);

    Module module = context.getModuleByFile(file);
    if (module == null) return compilableByFileType;

    Option<ScalaFacet> facet = ScalaFacet.findIn(module);
    if (!facet.isDefined()) return false;

    if (myFsc != facet.get().fsc()) return false;

    return compilableByFileType && !isScalaScript(file) && !isWorksheet(file);
  }

  private boolean isCompilableByExtension(VirtualFile file) {
    FileType fileType = FILE_TYPE_MANAGER.getFileTypeByFile(file);

    ScalacSettings settings = ScalacSettings.getInstance(myProject);

    if (StdFileTypes.JAVA.equals(fileType)) return settings.SCALAC_BEFORE;

    return ScalaFileType.SCALA_FILE_TYPE.equals(fileType);
  }

  private boolean isScalaScript(final VirtualFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        PsiFile psi = PsiManager.getInstance(myProject).findFile(file);
        return psi instanceof ScalaFile && ((ScalaFile) psi).isScriptFile(true);
      }
    });
  }

  private boolean isWorksheet(final VirtualFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        PsiFile psi = PsiManager.getInstance(myProject).findFile(file);
        return psi instanceof ScalaFile && ((ScalaFile) psi).isWorksheetFile();
      }
    });
  }

  static boolean containsScalaFiles(Iterable<VirtualFile> files) {
    for (VirtualFile file : files) {
      final FileType fileType = FILE_TYPE_MANAGER.getFileTypeByFile(file);
      if (ScalaFileType.SCALA_FILE_TYPE.equals(fileType)) {
        return true;
      }
    }
    return false;
  }

  public void compile(CompileContext context, Chunk<Module> moduleChunk, VirtualFile[] files, OutputSink sink) {
    final List<VirtualFile> filesToCompile = Arrays.asList(files);

    if (!containsScalaFiles(filesToCompile)) {
      return;
    }

    Project project = context.getProject();

    ScalacSettings settings = ScalacSettings.getInstance(project);

    if (myFsc && settings.INTERNAL_SERVER) {
      FscServerLauncher server = myProject.getComponent(FscServerLauncher.class);
      server.init();
    }

    final BackendCompiler backEndCompiler = getBackEndCompiler();

    final BackendCompilerWrapper wrapper = new BackendCompilerWrapper(this, moduleChunk, myProject, filesToCompile,
        (CompileContextEx) context, backEndCompiler, sink);
    try {
      wrapper.compile();
    }
    catch (CompilerException e) {
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
    }
    catch (CacheCorruptedException e) {
      LOG.info(e);
      context.requestRebuildNextTime(e.getMessage());
    }
  }

  @NotNull
  @Override
  public FileType[] getInputFileTypes() {
    return new FileType[0];
  }

  @NotNull
  @Override
  public FileType[] getOutputFileTypes() {
    return new FileType[0];
  }

  public boolean validateConfiguration(CompileScope scope) {
    return getBackEndCompiler().checkCompiler(scope);
  }

  @Override
  public void init(@NotNull CompilerManager compilerManager) {
    compilerManager.addCompilableFileType(ScalaFileType.SCALA_FILE_TYPE);
  }

  private BackendCompiler getBackEndCompiler() {
    return new ScalacBackendCompiler(myProject, myFsc);
  }
}