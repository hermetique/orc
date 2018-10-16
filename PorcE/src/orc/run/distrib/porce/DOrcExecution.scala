//
// DOrcExecution.scala -- Scala classes DOrcExecution, DOrcLeaderExecution, and DOrcFollowerExecution
// Project PorcE
//
// Created by jthywiss on Dec 29, 2015.
//
// Copyright (c) 2018 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.run.distrib.porce

import java.util.ServiceLoader

import scala.collection.JavaConverters.iterableAsScalaIterableConverter

import orc.{ OrcEvent, OrcExecutionOptions, PublishedEvent }
import orc.compile.parse.OrcSourceRange
import orc.compiler.porce.PorcToPorcE
import orc.run.distrib.DOrcPlacementPolicy
import orc.run.porce.{ HasId, PorcEUnit }
import orc.run.porce.runtime.{ MaterializedCPSCallContext, PorcEClosure, PorcEExecution, PorcEExecutionWithLaunch }

import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode

/** Top level Group, associated with a program running in a dOrc runtime
  * engine.  dOrc executions have an ID, the program AST and OrcOptions,
  * etc.
  *
  * Rule of thumb: Orc Executions keep program state and handle engine-internal
  * behavior (Tokens, Groups, Frames, etc.).  External interaction (with the
  * environment) is the bailiwick of Orc Runtimes.
  *
  * An Execution (Distributed Orc program run) is identified by a execution ID
  * UUID. Each runtime participating in an execution is assigned an
  * unique-to-that-execution follower number.  (The leader runtime for an
  * execution uses "follower" number zero.)
  *
  * @author jthywiss
  */
abstract class DOrcExecution(
    val executionId: DOrcExecution#ExecutionId,
    val followerExecutionNum: Int,
    programAst: DOrcRuntime#ProgramAST,
    options: OrcExecutionOptions,
    eventHandler: OrcEvent => Unit,
    override val runtime: DOrcRuntime)
  extends PorcEExecution( /* node, options,*/ runtime, eventHandler)
  with DistributedInvocationInterceptor
  with ValueMarshaler
  with ExecutionMashaler
  with CounterProxyManager
  with TerminatorProxyManager
  with RemoteFutureManager
  with RemoteObjectManager
  with RemoteRefIdManager {

  val (programPorceAst, programCallTargetMap) = PorcToPorcE.method(programAst, this)

  type ExecutionId = String

  def locationForFollowerNum(followerNum: DOrcRuntime#RuntimeId): PeerLocation = runtime.locationForRuntimeId(followerNum)

  private val hereSet: Set[PeerLocation] = Set(runtime.here)

  protected val valueLocatorFactoryServiceLoader: ServiceLoader[ValueLocatorFactory] = ServiceLoader.load(classOf[ValueLocatorFactory])

  protected val valueLocators: Set[ValueLocator] = valueLocatorFactoryServiceLoader.asScala.map(_(this)).toSet

  protected val callLocationOverriders: Set[CallLocationOverrider] = valueLocators.filter(_.isInstanceOf[CallLocationOverrider]).asInstanceOf[Set[CallLocationOverrider]]

  def currentLocations(v: Any): Set[PeerLocation] = {
    def pfc(v: Any): PartialFunction[ValueLocator, Set[PeerLocation]] =
      { case vl if vl.currentLocations.isDefinedAt(v) => vl.currentLocations(v) }
    val cl = v match {
      case rmt: RemoteRef => Set(homeLocationForRemoteRef(rmt.remoteRefId))
      case _ if valueLocators.exists(_.currentLocations.isDefinedAt(v)) => valueLocators.collect(pfc(v)).reduce(_.union(_))
      case _ => hereSet
    }
    //Logger.ValueLocation.finer(s"currentLocations($v: ${orc.util.GetScalaTypeName(v)})=$cl")
    cl
  }

  def isLocal(v: Any): Boolean = {
    val result = v match {
      case rmt: RemoteRef => false
      case _ if valueLocators.exists(_.currentLocations.isDefinedAt(v)) => valueLocators.exists({ vl => vl.currentLocations.isDefinedAt(v) && vl.valueIsLocal(v) })
      case _ => true
    }
    //Logger.ValueLocation.finer(s"isLocal($v: ${orc.util.GetScalaTypeName(v)})=$result")
    result
  }

  def permittedLocations(v: Any): Set[PeerLocation] = {
    def pfp(v: Any): PartialFunction[ValueLocator, Set[PeerLocation]] =
      { case vl if vl.permittedLocations.isDefinedAt(v) => vl.permittedLocations(v) }
    val pl = v match {
      case plp: DOrcPlacementPolicy => plp.permittedLocations(runtime)
      case rmt: RemoteRef => Set(homeLocationForRemoteRef(rmt.remoteRefId))
      case _ if valueLocators.exists(_.permittedLocations.isDefinedAt(v)) => valueLocators.collect(pfp(v)).reduce(_.intersect(_))
      case _ => runtime.allLocations
    }
    //Logger.ValueLocation.finest(s"permittedLocations($v: ${orc.util.GetScalaTypeName(v)})=$pl")
    pl
  }

  def callLocationMayNeedOverride(target: AnyRef, arguments: Array[AnyRef]): Option[Boolean] = {
    //val cloResults = callLocationOverriders.map(_.callLocationMayNeedOverride(target, arguments)).filter(_.isDefined).map(_.get)
    //assert(cloResults.size <= 1, "Multiple CallLocationOverriders responded for " + orc.util.GetScalaTypeName(target) + arguments.map(orc.util.GetScalaTypeName(_)).mkString("(", ",", ")"))
    //cloResults.headOption
    for (clo <- callLocationOverriders) {
      val needsOverride = clo.callLocationMayNeedOverride(target, arguments)
      /* Short-circuit: First clo to answer wins.  Masks multiple clo bugs, though. */
      if (needsOverride.isDefined) return needsOverride
    }
    None
  }

  def callLocationOverride(target: AnyRef, arguments: Array[AnyRef]): Set[PeerLocation] = {
    for (clo <- callLocationOverriders) {
      val needsOverride = clo.callLocationMayNeedOverride(target, arguments)
      /* Short-circuit: First clo to answer wins.  Masks multiple clo bugs, though. */
      if (needsOverride.isDefined) return clo.callLocationOverride(target, arguments)
    }
    Set.empty
  }

  def selectLocationForCall(candidateLocations: Set[PeerLocation]): PeerLocation = candidateLocations.head

}

object DOrcExecution {
  def freshExecutionId(): String = java.util.UUID.randomUUID().toString
  val noGroupProxyId: RemoteRef#RemoteRefId = 0L
}

/** DOrcExecution in the dOrc LeaderRuntime.  This is the "true" root group.
  *
  * @author jthywiss
  */
class DOrcLeaderExecution(
    executionId: DOrcExecution#ExecutionId,
    programAst: DOrcRuntime#ProgramAST,
    options: OrcExecutionOptions,
    eventHandler: OrcEvent => Unit,
    runtime: LeaderRuntime)
  extends DOrcExecution(executionId, 0, programAst, options, eventHandler, runtime) with PorcEExecutionWithLaunch {

  protected val startingFollowers = java.util.concurrent.ConcurrentHashMap.newKeySet[DOrcRuntime#RuntimeId]()
  protected val readyFollowers = java.util.concurrent.ConcurrentHashMap.newKeySet[DOrcRuntime#RuntimeId]()
  protected val followerStartWait = new Object()

  def followerStarting(followerNum: DOrcRuntime#RuntimeId): Unit = {
    startingFollowers.add(followerNum)
  }

  def followerReady(followerNum: DOrcRuntime#RuntimeId): Unit = {
    readyFollowers.add(followerNum)
    startingFollowers.remove(followerNum)
    followerStartWait synchronized followerStartWait.notifyAll()
  }

  def awaitAllFollowersReady(): Unit = {
    while (!startingFollowers.isEmpty()) followerStartWait synchronized followerStartWait.wait()
  }

  //TODO: Move to superclass
  def runProgram(): Unit = {
    scheduleProgram(programPorceAst, programCallTargetMap)
  }

  override def notifyOrc(event: OrcEvent): Unit = {
    super.notifyOrc(event)
    Logger.fine(event.toString)
  }

}

/** Execution group to contain migrated tokens.  This is a "fake" root group to
  * anchor the partial group tree in FollowerRuntimes.
  *
  * @author jthywiss
  */
class DOrcFollowerExecution(
    executionId: DOrcExecution#ExecutionId,
    followerExecutionNum: Int,
    programAst: DOrcRuntime#ProgramAST,
    options: OrcExecutionOptions,
    eventHandler: OrcEvent => Unit,
    rootCounterId: CounterProxyManager#DistributedCounterId,
    rootCounterOrigin: PeerLocation,
    runtime: FollowerRuntime)
  extends DOrcExecution(executionId, followerExecutionNum, programAst, options, eventHandler, runtime) {

  // Token: This does not get a token initially.
  val counter = getDistributedCounterForId(rootCounterId).counter

  val pRootNode = new RootNode(null) with HasId {
    def execute(frame: VirtualFrame): Object = {
      // Skip the first argument since it is our captured value array.
      val v = frame.getArguments()(1)
      notifyOrcWithBoundary(PublishedEvent(v))
      // Token: from caller of P.
      counter.haltToken()
      PorcEUnit.SINGLETON
    }

    def getId() = -1
  }

  val pCallTarget = truffleRuntime.createCallTarget(pRootNode)

  protected override def setCallTargetMap(callTargetMap: collection.Map[Int, RootCallTarget]) = {
    super.setCallTargetMap(callTargetMap)
    this.callTargetMap += (-1 -> pCallTarget)
  }

  {
    val (_, map) = PorcToPorcE.method(programAst, this)

    setCallTargetMap(map)

    //Logger.ProgLoad.finest(s"Loaded program in follower. CallTagetMap:\n${callTargetMap.mkString("\n")}")
  }
}

case class CallMemento(callSiteId: Int, callSitePosition: Option[OrcSourceRange], publicationContinuation: PorcEClosure, counterId: CounterProxyManager#DistributedCounterId, credit: Int, terminatorProxyId: TerminatorProxyManager#TerminatorProxyId, target: AnyRef, arguments: Array[AnyRef]) extends Serializable {
  def this(callContext: MaterializedCPSCallContext, counterId: CounterProxyManager#DistributedCounterId, credit: Int, terminatorProxyId: TerminatorProxyManager#TerminatorProxyId, target: AnyRef, arguments: Array[AnyRef]) {
    this(callSiteId = callContext.callSiteId, callSitePosition = callContext.callSitePosition, publicationContinuation = callContext.p, counterId = counterId, credit = credit, terminatorProxyId = terminatorProxyId, target = target, arguments = arguments)
  }
}

case class PublishMemento(publicationContinuation: PorcEClosure, publishedValue: AnyRef) extends Serializable

case class KillingMemento(counterId: CounterProxyManager#DistributedCounterId, credit: Int, continuation: PorcEClosure) extends Serializable
