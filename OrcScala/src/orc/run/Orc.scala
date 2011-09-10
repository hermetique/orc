//
// Orc.scala -- Scala class Orc
// Project OrcScala
//
// $Id$
//
// Created by dkitchin on May 10, 2010.
//
// Copyright (c) 2011 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.run

import orc.ast.oil.nameless.Expression
import orc.run.core.Token
import orc.run.core.Execution
import orc.OrcRuntime
import orc.OrcEvent
import orc.OrcExecutionOptions
import orc.run.orca.RootContext

trait Orc extends OrcRuntime {

  def run(node: Expression, k: OrcEvent => Unit, options: OrcExecutionOptions) {
    startScheduler(options: OrcExecutionOptions)
    
    val root = new Execution(node, options, k, this)
    installHandlers(root)
    
    val context = new RootContext()
    
    val t = new Token(node, root, context)
    schedule(t)
  }

  def stop() = {
    stopScheduler()
  }

  /**
   * Add all needed event handlers to an execution.
   * Traits which add support for more events will override this
   * method and introduce more handlers.
   */
  def installHandlers(host: Execution) {}
  
}
