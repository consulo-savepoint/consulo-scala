package org.jetbrains.plugins.scala
package lang.refactoring

import mock.EditorMock
import org.jetbrains.plugins.scala.base.SimpleTestCase
import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import junit.framework.Assert._

/**
 * Pavel Fatin
 */

class StatementMoverTestBase extends SimpleTestCase {
  protected implicit def toMovable(code: String) = new Movable(code)

  private def move(code: String, direction: Direction): Option[String] = {
    val cursors = code.count(_ == '|')
    if(cursors == 0) fail("No cursor offset specified in the code: " + code)
    if(cursors > 1) fail("Multiple cursor offset specified in the code: " + code)

    val offset = code.indexOf("|")

    val cleanCode = code.replaceAll("\\|", "").replaceFirst("(?-m)\n?$", "\n")
    val file = cleanCode.parse
    val editor = new EditorMock(cleanCode, offset)

    val mover = new ScalaStatementMover()
    val info = new StatementUpDownMover.MoveInfo()

    val available = mover.checkAvailable(editor, file, info, direction == Down)

    available.ifTrue {
      val y1 = info.toMove.startLine
      val y2 = info.toMove2.startLine
      val length = info.toMove.endLine - y1

      val lines = cleanCode.split('\n')

      val source = lines.drop(y1).take(length)
      val remainder = lines.patch(y1, Seq.empty, length)

      val (prefix, suffix) = remainder.splitAt(y2)

      (prefix ++ source ++ suffix).mkString("\n")
    }
  }

  private class Direction
  private case object Up extends Direction
  private case object Down extends Direction

  protected class Movable(code: String) {
    def moveUpIsDisabled() {
      assertEquals(None, move(code, Up))
    }

    def moveDownIsDisabled() {
      assertEquals(None, move(code, Down))
    }

    def movedUpIs(s: String) {
      assertEquals(Some(s), move(code, Up))
    }

    def movedDownIs(s: String) {
      assertEquals(Some(s), move(code, Down))
    }
  }
}