package org.jetbrains.plugins.scala.debugger.evaluation.evaluator

import com.intellij.debugger.engine.evaluation.expression.{Modifier, Evaluator}
import consulo.internal.com.sun.jdi.BooleanValue
import com.intellij.debugger.engine.evaluation.{EvaluateExceptionUtil, EvaluationContextImpl}

/**
 * User: Alefas
 * Date: 20.10.11
 */

class ScalaDoStmtEvaluator(cond: Evaluator, expr: Evaluator) extends Evaluator {
  def evaluate(context: EvaluationContextImpl): AnyRef = {
    expr.evaluate(context)
    var condition: Boolean = cond.evaluate(context) match {
      case b: BooleanValue => b.value()
      case _ => throw EvaluateExceptionUtil.createEvaluateException("condition has wrong type")
    }
    while (condition) {
      expr.evaluate(context)
      condition = cond.evaluate(context) match {
        case b: BooleanValue => b.value()
        case _ => throw EvaluateExceptionUtil.createEvaluateException("condition has wrong type")
      }
    }
    context.getDebugProcess.getVirtualMachineProxy.mirrorOf()
  }

  def getModifier: Modifier = null
}