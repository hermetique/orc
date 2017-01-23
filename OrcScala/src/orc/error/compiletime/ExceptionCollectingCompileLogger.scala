//
// ExceptionCompileLogger.scala -- Scala class ExceptionCompileLogger
// Project OrcScala
//
// Created by jthywiss on Jun 8, 2010.
//
// Copyright (c) 2013 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//
package orc.error.compiletime

import orc.error.compiletime.CompileLogger.Severity
import orc.ast.AST
import scala.util.parsing.input.Position
import orc.compile.parse.OrcInputContext
import java.io.PrintWriter
import scala.collection.mutable
import orc.compile.parse.OrcSourceRange

/** A CompileMessageRecorder that writes messages to a PrintWriter (such as one
  * for stderr) and collects exceptions as would be generated by
  * ExceptionCompileLogger. Unlike ExceptionCompileLogger this only collects
  * errors, not warnings.
  *
  * @author amp
  */
class ExceptionCollectingCompileLogger(logToWriter: PrintWriter) extends PrintWriterCompileLogger(logToWriter) {
  private val exceptionsBuffer = mutable.Buffer[Throwable]()

  /* (non-Javadoc)
     * @see orc.error.compiletime.CompileLogger#recordMessage(Severity, int, String, Position, AST, Throwable)
     */
  override def recordMessage(severity: Severity, code: Int, message: String, location: Option[OrcSourceRange], astNode: AST, exception: Throwable) {
    super.recordMessage(severity, code, message, location, astNode, exception)
    try {
      ExceptionCompileLogger.throwExceptionIfNeeded(Severity.ERROR, severity, message, location, exception)
    } catch {
      case e: Throwable =>
        exceptionsBuffer.append(e)
    }
  }

  def exceptions = exceptionsBuffer.toSeq
}
