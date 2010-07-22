//
// Main.scala -- Scala object Main
// Project OrcScala
//
// $Id$
//
// Created by jthywiss on Jul 20, 2010.
//
// Copyright (c) 2010 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//
package orc

import orc.script.OrcScriptEngine
import orc.script.OrcBindings
import orc.compile.parse.OrcReader
import orc.values.Format
import javax.script.ScriptEngineManager
import javax.script.ScriptEngine
import javax.script.Compilable
import javax.script.ScriptContext.ENGINE_SCOPE
import javax.script.ScriptException


/**
 * A command-line tool invocation of the Orc compiler and runtime engine
 *
 * @author jthywiss
 */
object Main {

  def main(args: Array[String]) {
    val engine = (new ScriptEngineManager).getEngineByName("orc").asInstanceOf[ScriptEngine with Compilable]
    if (engine == null) throw new ClassNotFoundException("Unable to load Orc ScriptEngine")
    try {
      val options = new OrcBindings() with CmdLineOptions
      options.parseCmdLine(args)
      engine.setBindings(options, ENGINE_SCOPE)
      val reader = new java.io.FileReader(options.filename) //OrcReader(new java.io.FileReader(options.filename), options.filename, compiler.openInclude(_, _, options))
      val compiledOrc = engine.compile(reader).asInstanceOf[OrcScriptEngine#OrcCompiledScript]
      val printPubs = new OrcEventAction() {
        override def published(value: AnyRef) { println(Format.formatValue(value)) }
      }
      compiledOrc.run(printPubs)
    } catch {
      case e: CmdLineUsageException => Console.err.println("Orc: " + e.getMessage)
      case e: PrintVersionAndMessageException => println("Orc "+orcVersion+"\n"+orcURL+"\n"+orcCopyright+"\n\n"+e.getMessage)
      case e: java.io.FileNotFoundException => Console.err.println("Orc: File not found: " + e.getMessage)
      case e: ScriptException => throw e.getCause // un-wrap and propagate
    }
  }

  lazy val orcImplName: String = valOrElse(Package.getPackage("orc").getImplementationTitle, "Orc")
  lazy val orcVersion: String = valOrElse(Package.getPackage("orc").getImplementationVersion, "(dev build)")
  lazy val orcURL: String = "http://orc.csres.utexas.edu/"
  lazy val orcCopyright: String = "© "+coyrightYear+" "+valOrElse(Package.getPackage("orc").getImplementationVendor, "The University of Texas at Austin")
  lazy val coyrightYear: String = "2010" //TODO: Automate this somehow from build timestamp
  def valOrElse[T](testVal: T, defaultVal: T): T = if (testVal != null) testVal else defaultVal
}
