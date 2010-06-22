//
// Orc.scala -- Scala class Orc
// Project OrcScala
//
// $Id$
//
// Created by dkitchin on May 10, 2010.
//
// Copyright (c) 2010 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.run

import orc.OrcExecutionAPI
import orc.TokenAPI
import orc.oil._
import orc.oil.nameless._
import orc.PartialMapExtension._
import orc.values.sites.Site
import orc.values.Value
import orc.values.Closure
import orc.error.OrcException
import orc.error.runtime.TokenException
import orc.error.runtime.JavaException
import orc.error.runtime.UncallableValueException

import scala.collection.mutable.Set   

abstract class Orc extends OrcExecutionAPI {

  var exec: Option[Execution] = Some(new Execution())

  def run(node: Expression) {
    val exec = new Execution()
    val t = new Token(node, exec)
    t.run
  }
  
  
  // Groups
  
  // A Group is a structure associated with dynamic instances of an expression,
  // tracking all of the executions occurring within that expression.
  // Different combinators make use of different Group subclasses.
  
  trait GroupMember {
    def kill: Unit
  }


  abstract class Group extends GroupMember {
  
    def publish(t: Token, v: Value): Unit
    def onHalt: Unit
  
    var members: Set[GroupMember] = Set()
  
    def halt(t: Token) { remove(t) }
    def kill { for (m <- members) m.kill } 
    /* Note: this is _not_ lazy termination */
  
    def add(m: GroupMember) { members.add(m) }
  
    def remove(m: GroupMember) { 
      members.remove(m)
      if (members.isEmpty) { onHalt }
    }
  
  }
  
  // A Groupcell is the group associated with expression g in (f <x< g)
  
  // Possible states of a Groupcell
  class GroupcellState
  case class Unbound(waitlist: List[Token]) extends GroupcellState
  case class Bound(v: Value) extends GroupcellState
  case object Dead extends GroupcellState
  
  class Groupcell(parent: Group) extends Group {
  
    var state: GroupcellState = Unbound(Nil) 
  
    def publish(t: Token, v: Value) {
      state match {
        case Unbound(waitlist) => {
          state = Bound(v)
          schedule(waitlist)
          t.halt
          this.kill
        }
        case _ => t.halt	
      }
    }
    
    def onHalt {
      state match {
        case Unbound(waitlist) => {
          for (t <- waitlist) t.halt
          state = Dead
          parent.remove(this)
        }
        case _ => {  }
      }
    }
    
    // Specific to Groupcells
    def read(reader: Token): Option[Value] = 
      state match {
        case Bound(v) => Some(v)
        case Unbound(waitlist) => {
          state = Unbound(reader :: waitlist)
          None
        }
        case Dead => {
          reader.halt
          None
        }
    }
  }
  
  object Groupcell {
    def apply(parent: Group): Groupcell = {
        val g = new Groupcell(parent)
        parent.add(g)
        g
    }
  }
  
  
  
  
  // A Region is the group associated with expression f in (f ; g)
  class Region(parent: Group, r: Token) extends Group {
  
    // Some(r): No publications have left this region;
    //			if the group halts silently, pending
    //			will be scheduled.
    // None:	A publication has left this region.
  
    var pending: Option[Token] = Some(r)
  
    def publish(t: Token, v: Value) {
      pending.foreach(_.halt)
      t.publish(v)
    }
    
    def onHalt {
      pending.foreach(schedule(_))
      parent.remove(this)
    }
  
  }	
  
  object Region {
  
    def apply(parent: Group, r: Token): Region = {
      val g = new Region(parent, r)
      parent.add(g)
      g
    }
  }
  
  // An execution is a special toplevel group, 
  // associated with the entire program.
  class Execution extends Group {
  
    def publish(t: Token, v: Value) {
      emit(v)
      t.halt
    }
  
    def onHalt {
      halted
    }
  }
  
  
  
  // Tokens and their auxilliary structures //
  
  
  // Context entries //
  trait Binding
  case class BoundValue(v: Value) extends Binding
  case class BoundCell(g: Groupcell) extends Binding
  implicit def ValuesAreBindings(v: Value): Binding = BoundValue(v)
  implicit def GroupcellsAreBindings(g: Groupcell): Binding = BoundCell(g) 
  
  // Control Frames //
  abstract class Frame {
    def apply(t: Token, v: Value): Unit
  }
  
  case class BindingFrame(n: Int) extends Frame {
    def apply(t: Token, v: Value) {
      t.env = t.env.drop(n)
      t.publish(v)
    }
  }
  
  case class SequenceFrame(node: Expression) extends Frame {
    def apply(t: Token, v: Value) {
      schedule(t.bind(v).move(node))
    }
  }
  
  case class FunctionFrame(callpoint: Expression, env: List[Binding]) extends Frame {
    def apply(t: Token, v: Value) {
      t.env = env
      t.move(callpoint).publish(v)
    }
  }
  
  case object GroupFrame extends Frame {
    def apply(t: Token, v: Value) {
      t.group.publish(t,v)
    }
  }
  
  
  
  
  // Token //
  
  class TokenState
  case object Live extends TokenState
  case object Halted extends TokenState
  case object Killed extends TokenState
  
  class Token private (
      var node: Expression,
      var stack: List[Frame] = Nil,
      var env: List[Binding] = Nil,
      var group: Group, 
      var state: TokenState = Live
  ) extends TokenAPI with GroupMember {	
  
    def this(start: Expression, exec: Execution) = {
      this(node = start, group = exec)
    }
  
    // Copy constructor with defaults
    private def copy(
        node: Expression = node,
        stack: List[Frame] = stack,
        env: List[Binding] = env,
        group: Group = group,
        state: TokenState = state): Token = 
        {
      new Token(node, stack, env, group, state)
        }
  
  
  
    def fork = (this, copy())
  
    def move(e: Expression) = { node = e ; this }
  
    def push(f: Frame) = { stack = f::stack ; this }
  
  
    // reslice to improve contracts
    def join(child: Group) = { 
      val parent = group
      child.add(this); parent.remove(this)
      group = child
      push(GroupFrame)
      this 
    }			
  
    // Manipulating context frames
  
    def bind(b: Binding): Token = {
      env = b::env
      stack match {
        case BindingFrame(n)::fs => { stack = (new BindingFrame(n+1))::fs }
    
        /* Tail call optimization (part 1 of 2) */
        case FunctionFrame(_,_)::fs => { /* Do not push a binding frame over a tail call.*/ }
    
        case fs => { stack = BindingFrame(1)::fs }
      }
      this
    }
  
  
    def lookup(a: Argument): Binding = 
      a match {
        case Constant(v) => v
        case Variable(n) => env(n)
    }
  
    // Caution: has a side effect! :-P
    def resolve(a: Argument): Option[Value] =
      lookup(a) match {
        case BoundValue(v) => Some(v)
        case BoundCell(g) => g.read(this)
    }
  
  
  
    // Publicly accessible methods
  
    def publish(v: Value) {
      stack match {
        case f::fs => { 
          stack = fs
          f(this, v)
        }
        case Nil => { emit(v) } // !!!
      }
    }
  
    def halt {
      state match {
        case Live => { state = Halted }
        case _ => {  }
      }
    }
  
    def kill {
      state match {
        case Live => { state = Killed }
        case _ => {  }
      }
    }
  
    def run {
      if (state == Live) {
        node match {
          case Stop() => halt
          case (a: Argument) => resolve(a).foreach(publish(_))
          case (Call(target, args, typeArgs)) => try {            
            resolve(target).foreach({
              case closure@ Closure(arity, body, newcontext) => {
                if (arity != args.size) halt /* Arity mismatch. */
                val actuals = args map lookup
                
                /* 1) Push a function frame (if this is not a tail call),
                 *    referring to the current environment
                 * 2) Change the current environment to the closure's
                 *    saved environment.
                 * 3) Add bindings for the arguments to the new current
                 *    environment
                 * 
                 * Caution: The ordering of these statements is very important;
                 *          do not permute them.    
                 */
      
                /* Tail call optimization (part 2 of 2) */
                stack match {
                  /*
                   * Push a new FunctionFrame 
                   * only if the call is not a tail call.
                   */
                  case FunctionFrame(_,_)::fs => {  }
                  case _ => push(new FunctionFrame(node, env))
                }
      
                this.env = newcontext map BoundValue      
                for (a <- actuals) { bind(a) }
                
                schedule(this.move(closure.altbody))				  					
              }
              case (s: Site) => {
                val vs = args.partialMap(resolve)
                vs.foreach( try {
                  invoke(this,s,_)
                } catch {
                  case e: OrcException => throw e
                  case e => throw new JavaException(e)
                })
              }
              case uncallable => {
                halt
                throw new UncallableValueException("You can't call the "+uncallable.getClass().getName()+" \""+uncallable.toString()+"\"")
              }
            })
          } catch {
            case e: TokenException => {
              halt
              e.setPosition(node.pos)
              //TODO: e.backtrace = all of the FunctionFrame.callpoint.pos in this token's stack
              caught(e)
            }
            case e: OrcException => {
              halt
              e.setPosition(node.pos)
              caught(e)
            }
            case e => {
              halt
              caught(e)
            }
          }
    
          case Parallel(left, right) => {
            val (l,r) = fork
            schedule(l.move(left), r.move(right))		
          }
    
          case Sequence(left, right) => {
            val frame = new SequenceFrame(right)		  	  
            schedule(this.push(frame).move(left))
          }
    
          case Prune(left, right) => {
            val (l,r) = fork
            val groupcell = Groupcell(group)
            schedule( l.bind(groupcell).move(left),
                r.join(groupcell).move(right) )
          }
    
          case Otherwise(left, right) => {
            val (l,r) = fork
            val region = Region(group, r)
            schedule(l.join(region).move(left))
          }
    
          case decldefs@ DeclareDefs(defs, body) => {
            
            
            /* Closure compaction: Bind only free variables
             * of the defs in the closure's context */
            
            /* Closures are strict, so we use a partialMap of resolve.
             * If any variable fails to resolve, then resolveContext
             * is None, and the blocked token will resume once one
             * or more unbound variables becomes bound.
             */
            // Dirt simple O(n^2) solution.
            // TODO: Optimize this to O(n) instead of O(n^2) in the case of many unbound vars;
            //       the token itself must keep track of the bound values discovered so far.
            val resolveContext = decldefs.freeVarList partialMap { n:Int => resolve(Variable(n)) }
            
            resolveContext match {
              case Some(vs) => {
                var context: List[Value] = vs
                
                val cs = defs map ( (d: Def) => new Closure(d) )
                for (c <- cs) { bind(c); context = c :: context }
                for (c <- cs) { c.context = context }
                
                this.move(body).run
              }
              case None => { /* Blocked on some unbound variable. Do nothing */ }
            }
          }
          case HasType(expr, _) => this.move(expr).run
          case DeclareType(_, expr) => this.move(expr).run
        }
      }
    }
    
    def printToStdout(s: String) = expressionPrinted(s)
  
  }

}


/**
 * A typical setup for an Orc execution; emit and halted are still abstract.
 */
trait StandardOrcExecution extends Orc {
  def invoke(t: this.Token, s: Site, vs: List[Value]) { s.call(vs,t) }
  def expressionPrinted(s: String) { print(s) }
  def caught(e: Throwable) { e.printStackTrace() }
  val worker = new Worker()
  
  import scala.actors.Actor
  import scala.actors.Actor._

  worker.start
  
  override def schedule(ts: List[Token]) { for (t <- ts) worker ! Some(t) }
  class Worker extends Actor {
    def act() {
      loop {
        react {
          case Some(x:Token) => x.run
          case _ =>
            Console.println("Invalid Message to Worker Actor!")
        }
      }
    }
  }
}
