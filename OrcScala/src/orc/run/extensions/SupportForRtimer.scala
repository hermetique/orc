//
// SupportForRtimer.scala -- Scala trait SupportForRtimer
// Project OrcScala
//
// $Id$
//
// Created by dkitchin on Jul 10, 2010.
//
// Copyright (c) 2010 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//
package orc.run.extensions

import orc.run.Orc
import orc.Handle
import orc.OrcEvent
import orc.OrcExecutionOptions
import java.util.Timer
import java.util.TimerTask
import orc.ast.oil.nameless.Expression

/**
 * 
 *
 * @author dkitchin
 */

case class RtimerEvent(delay: BigInt, caller: Handle) extends OrcEvent

trait SupportForRtimer extends Orc {
  
  val timer: Timer = new Timer()
  
  override def stop = { timer.cancel() ; super.stop }
  
  /* Note that all executions in a runtime with Rtimer support
   * will share the same timer queue, so that we can stop the
   * timer when the runtime is shut down.
   */
  
  override def generateOrcHandlers(host: Execution): List[OrcHandler] = {
    val thisHandler = { 
      case RtimerEvent(delay, caller) => {
        val callback =  
          new TimerTask() {
            @Override
            override def run() { caller.publish() }
          }
        timer.schedule(callback, delay.toLong)
      }
    } : PartialFunction[OrcEvent, Unit]
    
    thisHandler :: super.generateOrcHandlers(host)
  }
    
}