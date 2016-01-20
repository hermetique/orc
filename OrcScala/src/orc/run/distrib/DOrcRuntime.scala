//
// DOrcRuntime.scala -- Scala class DOrcRuntime
// Project OrcScala
//
// Created by jthywiss on Dec 29, 2015.
//
// Copyright (c) 2016 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.run.distrib

import scala.collection.JavaConversions.collectionAsScalaIterable

import orc.run.StandardOrcRuntime
import orc.run.extensions.SupportForDOrc

/** Distributed Orc (dOrc) Runtime Engine.
  *
  * Rule of thumb: Orc Runtimes manage external interaction, with the
  * environment. Program state and engine-internal behavior is the bailiwick
  * of Orc Executions.
  *
  * @author jthywiss
  */
abstract class DOrcRuntime(val runtimeId: DOrcRuntime#RuntimeId, engineInstanceName: String) extends StandardOrcRuntime(engineInstanceName) with SupportForDOrc {

  /* For now, runtime IDs and Execution follower numbers are the same.  When
   * we host more than one execution in an engine, they will be different. */

  type RuntimeId = Int

  def locationForRuntimeId(runtimeId: DOrcRuntime#RuntimeId): PeerLocation

  def allLocations: Set[PeerLocation]

  val here: PeerLocation

}
