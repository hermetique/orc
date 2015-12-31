//
// OrcCmd.scala -- Scala trait OrcCmd
// Project OrcScala
//
// Created by jthywiss on Dec 21, 2015.
//
// Copyright (c) 2015 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.run.distrib

import orc.{ OrcEvent, OrcExecutionOptions }

/** Command sent to dOrc runtime engines.
  *
  * @author jthywiss
  */
trait OrcCmd extends Serializable

trait OrcLeaderToFollowerCmd extends OrcCmd
trait OrcFollowerToLeaderCmd extends OrcCmd
trait OrcPeerCmd extends OrcLeaderToFollowerCmd with OrcFollowerToLeaderCmd

case class LoadProgramCmd(executionId: DOrcExecution#ExecutionId, followerExecutionNum: Int, programOil: String, options: OrcExecutionOptions) extends OrcLeaderToFollowerCmd
case class UnloadProgramCmd(executionId: DOrcExecution#ExecutionId) extends OrcLeaderToFollowerCmd

case class NotifyLeaderCmd(executionId: DOrcExecution#ExecutionId, event: OrcEvent) extends OrcFollowerToLeaderCmd

case class HostTokenCmd(executionId: DOrcExecution#ExecutionId, movedToken: TokenReplacement) extends OrcPeerCmd
case class PublishGroupCmd(executionId: DOrcExecution#ExecutionId, groupMemberProxyId: DOrcExecution#GroupProxyId, publishingToken: TokenReplacement, value: Option[AnyRef]) extends OrcPeerCmd
case class KillGroupCmd(executionId: DOrcExecution#ExecutionId, groupProxyId: DOrcExecution#GroupProxyId) extends OrcPeerCmd
case class HaltGroupMemberProxyCmd(executionId: DOrcExecution#ExecutionId, groupMemberProxyId: DOrcExecution#GroupProxyId) extends OrcPeerCmd
case object EOF extends OrcPeerCmd
