//
// StandardBackend.scala -- Scala class/trait/object StandardBackend
// Project OrcScala
//
// Created by amp on Aug 28, 2013.
//
// Copyright (c) 2019 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//
package orc

import java.io.{ InputStream, InputStreamReader, OutputStream, OutputStreamWriter }

import orc.ast.oil.nameless.Expression
import orc.ast.oil.xml.OrcXML
import orc.compile.StandardOrcCompiler
import orc.compile.parse.OrcInputContext
import orc.error.compiletime.CompileLogger
import orc.error.loadtime.{ LoadingException, OilParsingException }
import orc.progress.ProgressMonitor
import orc.run.StandardOrcRuntime

/** A backend implementation using the Token interpreter.
  *
  * @author amp
  */
class StandardBackend extends Backend[Expression] {
  lazy val compiler: Compiler[Expression] = new StandardOrcCompiler() with Compiler[Expression] {
    def compile(source: OrcInputContext, options: OrcCompilationOptions,
        compileLogger: CompileLogger, progress: ProgressMonitor): Expression = this(source, options, compileLogger, progress)
  }

  val serializer: Option[CodeSerializer[Expression]] = Some(new CodeSerializer[Expression] {
    @throws(classOf[LoadingException])
    def deserialize(in: InputStream): orc.ast.oil.nameless.Expression = {
      OrcXML.readOil(new InputStreamReader(in, "UTF-8")) match {
        case e: Expression => e
        case _ => throw new OilParsingException("Top-level element of input was not an Expression.")
      }
    }

    def serialize(code: orc.ast.oil.nameless.Expression, out: OutputStream): Unit = {
      new OutputStreamWriter(out, "UTF-8").write(OrcXML.toXML(code).toString)
    }
  })

  def createRuntime(options: OrcExecutionOptions): Runtime[Expression] = new StandardOrcRuntime("Orc") with Runtime[Expression] {
    def run(code: orc.ast.oil.nameless.Expression, eventHandler: orc.OrcEvent => Unit): Unit = run(code, eventHandler, options)
    def runSynchronous(code: orc.ast.oil.nameless.Expression, eventHandler: orc.OrcEvent => Unit): Unit = runSynchronous(code, eventHandler, options)
  }
}
