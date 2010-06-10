//
// Named.scala -- Named representation of OIL syntax
// Project OrcScala
//
// $Id$
//
// Created by dkitchin on May 28, 2010.
//
// Copyright (c) 2010 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.oil.named

import orc.oil._
import orc.values.Value
import orc.AST

// The supertype of all variable binding nodes
trait Scope
// The supertype of all type variable binding nodes
trait TypeScope


trait Var extends Argument
/*
class TempVar(val optionalName : Option[String]) extends Var {
  def this(name: String) = this(Some(name))
  def this() = this(None)
}
*/
class TempVar(val optionalName : Option[String] = None) extends Var {
  def this(name: String) = this(Some(name))
}
case class NamedVar(name : String) extends Var

trait Typevar extends Type
class TempTypevar(val optionalName : Option[String] = None) extends Typevar {
  def this(name: String) = this(Some(name))
}
case class NamedTypevar(name : String) extends Typevar


trait hasFreeVars {
  val freevars: Set[Var]
}

sealed abstract class NamedAST extends AST with NamedToNameless {
  override def toString() = (new PrettyPrint()).reduce(this)
}

sealed abstract class Expression
extends NamedAST 
with NamedInfixCombinators 
with hasFreeVars 
with hasArgumentMap[Expression]
with ArgumentSubstitution[Expression]
{ 
  lazy val withoutNames: nameless.Expression = namedToNameless(this, Nil, Nil)
  
  lazy val freevars:Set[Var] = {
    this match {
      case x:Var => Set(x)
      case Call(target,args,_) => target.freevars ++ args.flatMap(_.freevars)
      case left || right => left.freevars ++ right.freevars
      case left > x > right => left.freevars ++ (right.freevars - x)
      case left < x < right => (left.freevars - x) ++ right.freevars
      case left ow right => left.freevars ++ right.freevars
      case DeclareDefs(defs,body) => (body.freevars ++ defs.flatMap(_.freevars)) -- defs.map(_.name)
      case HasType(body,typ) => body.freevars
      case _ => Set.empty
    }
  }
  
  def map(f: Argument => Argument): Expression = 
    this -> {
      case Stop() => Stop()
      case a : Argument => f(a)	
      case Call(target, args, typeargs) => Call(f(target), args map f, typeargs)
      case left || right => (left map f) || (right map f)
      case left > x > right => (left map f) > x > (right map f)
      case left < x < right => (left map f) < x < (right map f)
      case left ow right => (left map f) ow (right map f)
      case DeclareDefs(defs, body) => DeclareDefs(defs map { _ map f }, body map f)
      case HasType(body, expectedType) => HasType(body map f, expectedType)
    } setPos pos
  
    /*
     * Removes unused definitions from the OIL AST.
     */
	def removeUnusedDefs(): Expression = {
		this -> {
			case left || right => left.removeUnusedDefs() || right.removeUnusedDefs()
			case left > x > right => left.removeUnusedDefs() > x > right.removeUnusedDefs() 
			case left < x < right => left.removeUnusedDefs() < x < right.removeUnusedDefs()
			case left ow right => left.removeUnusedDefs() ow right.removeUnusedDefs()
			case DeclareDefs(defs, body) => {
				val newbody = body.removeUnusedDefs()
				// If none of the defs are bound in the body,
	        	// just return the body.
	        	if(body.freevars -- defs.map(_.name) isEmpty) {
	        		newbody
	        	} else {
	        		def f(d: Def): Def = {
	        			d match { 
	        				case Def(name,args,body,t,a,r) => Def(name,args,body.removeUnusedDefs(),t,a,r)
	        			}
	        		}
	        		val newdefs = defs.map(f)
	        		DeclareDefs(newdefs, newbody)
	        	}
			}
			case HasType(body, typ) => HasType(body.removeUnusedDefs(), typ)
			case _ => this
		}
	}
	
	
    
}

case class Stop() extends Expression
case class Call(target: Argument, args: List[Argument], typeargs: Option[List[Type]]) extends Expression
case class Parallel(left: Expression, right: Expression) extends Expression
case class Sequence(left: Expression, x: TempVar, right: Expression) extends Expression with Scope
case class Prune(left: Expression, x: TempVar, right: Expression) extends Expression with Scope
case class Otherwise(left: Expression, right: Expression) extends Expression
case class DeclareDefs(defs : List[Def], body: Expression) extends Expression with Scope
case class HasType(body: Expression, expectedType: Type) extends Expression

sealed abstract class Argument extends Expression
case class Constant(value: Value) extends Argument


sealed case class Def(name: TempVar, formals: List[TempVar], body: Expression, typeformals: List[TempTypevar], argtypes: List[Type], returntype: Option[Type]) 
extends NamedAST 
with Scope 
with TypeScope 
with hasFreeVars 
with hasArgumentMap[Def]
with ArgumentSubstitution[Def]
{ 
  lazy val withoutNames: nameless.Def = namedToNameless(this, Nil, Nil)
  lazy val freevars: Set[Var] = body.freevars -- formals
  
  def map(f: Argument => Argument): Def = {
    this ->> Def(name, formals, (body map f), typeformals, argtypes, returntype)
  }


}


sealed abstract class Type extends NamedAST
{ 
  lazy val withoutNames: nameless.Type = namedToNameless(this, Nil) 
  
  def map(f: Typevar => Typevar): Type = 
	  this -> {
	 	  case x : Typevar => f(x)
	 	  case TupleType(elements) => TupleType(elements map {_ map f})
	 	  case TypeApplication(tycon, typeactuals) => TypeApplication(f(tycon), typeactuals map {_ map f})
	 	  case AssertedType(assertedType) => AssertedType(assertedType map f)
	 	  case FunctionType(typeformals, argtypes, returntype) =>
	 	  	FunctionType(typeformals, argtypes map {_ map f}, returntype map f)
	 	  case u => u
	  }
  
  def subst(t: Typevar, u: Typevar): Type = this map (y => if (y equals t) { u } else { y })   
  def subst(t: Typevar, s: String): Type = subst(t, NamedTypevar(s))
}	
case class Top() extends Type
case class Bot() extends Type
case class TupleType(elements: List[Type]) extends Type
case class TypeApplication(tycon: Typevar, typeactuals: List[Type]) extends Type
case class AssertedType(assertedType: Type) extends Type	
case class FunctionType(typeformals: List[TempTypevar], argtypes: List[Type], returntype: Type) 
extends Type with TypeScope


// Conversions from named to nameless representations
trait NamedToNameless {

  def namedToNameless(e: Expression, context: List[TempVar], typecontext: List[TempTypevar]): nameless.Expression = {
    def toExp(e: Expression): nameless.Expression = namedToNameless(e, context, typecontext)
    def toArg(a: Argument): nameless.Argument = namedToNameless(a, context)
    def toType(t: Type): nameless.Type = namedToNameless(t, typecontext)
    e -> {
      case Stop() => nameless.Stop()
      case a : Argument => namedToNameless(a, context)		
      case Call(target, args, typeargs) => nameless.Call(toArg(target), args map toArg, typeargs map { _ map toType })
      case left || right => nameless.Parallel(toExp(left), toExp(right))
      case left > x > right => nameless.Sequence(toExp(left), namedToNameless(right, x::context, typecontext))
      case left < x < right => nameless.Prune(namedToNameless(left, x::context, typecontext), toExp(right))
      case left ow right => nameless.Otherwise(toExp(left), toExp(right))
      case DeclareDefs(defs, body) => {
        val defnames = defs map { _.name }
        val newdefs = defs map { namedToNameless(_, defnames ::: context, typecontext) }
        val newbody = namedToNameless(body, defnames ::: context, typecontext)
        nameless.DeclareDefs(newdefs, newbody)
      }
      case HasType(body, expectedType) => nameless.HasType(toExp(body), toType(expectedType))
    } setPos e.pos
  }	

  def namedToNameless(a: Argument, context: List[TempVar]): nameless.Argument = {
    a -> {
      case Constant(v) => nameless.Constant(v)
      case (x: TempVar) => nameless.Variable(context indexOf x) 
      case x@ NamedVar(s) => x !! ("Unbound variable " + s) 
    } setPos a.pos
  }


  def namedToNameless(t: Type, typecontext: List[TempTypevar]): nameless.Type = {
    def toType(t: Type): nameless.Type = namedToNameless(t, typecontext)
    t -> {
      case u: TempTypevar => nameless.TypeVar(typecontext indexOf u)
      case Top() => nameless.Top()
      case Bot() => nameless.Bot()
      case FunctionType(typeformals, argtypes, returntype) => {
        val newTypeContext = typeformals ::: typecontext
        val newArgTypes = argtypes map { namedToNameless(_, newTypeContext) }
        val newReturnType = namedToNameless(returntype, newTypeContext)
        nameless.FunctionType(typeformals.size, newArgTypes, newReturnType)
      }
      case TupleType(elements) => nameless.TupleType(elements map toType)
      case TypeApplication(tycon, typeactuals) => {
        val i = typecontext indexOf tycon
        nameless.TypeApplication(i, typeactuals map toType)
      }	
      case AssertedType(assertedType) => nameless.AssertedType(namedToNameless(assertedType, typecontext))
      case u@ NamedTypevar(s) => u !! ("Unbound type variable " + s)
    } setPos t.pos
  }	

  def namedToNameless(defn: Def, context: List[TempVar], typecontext: List[TempTypevar]): nameless.Def = {
    defn -> {
      case Def(_, formals, body, typeformals, argtypes, returntype) => {
        val newContext = formals ::: context
        val newTypeContext = typeformals ::: typecontext 
        val newbody = namedToNameless(body, newContext, newTypeContext)
        val newArgTypes = argtypes map { namedToNameless(_, newTypeContext) }
        val newReturnType = returntype map { namedToNameless(_, newTypeContext) }
        nameless.Def(typeformals.size, formals.size, newbody, newArgTypes, newReturnType)
      }
    } setPos defn.pos
  }

}	


trait hasArgumentMap[X] {
  self : X =>
  
  def map(f : Argument => Argument): X
}

trait ArgumentSubstitution[X] extends NamedAST with hasArgumentMap[X] {
  self : X =>
	
    // FIXME: Is equals correct here?
  def subst(a: Argument, x: Argument): X = 
	  this map (y => if (y equals x) { a } else { y })
  
  def substAllArgs(subs: List[(Argument, Argument)]): X = {
	  this map (y => {
	     val options = for ((a,x) <- subs if y equals x) yield a
	     options match {
	    	 case Nil => y
	    	 case List(a) => a
	    	 case _ => this !! ("Conflicting substitutions on " + y + ": " + options) 
	     }
	  })
  }
  
  
  def subst(a: Argument, s: String): X = subst(a, new NamedVar(s))
  
  def substAll(subs: List[(Argument, String)]): X = {
	  val newsubs = for ((a,s) <- subs) yield (a, new NamedVar(s))
	  substAllArgs(newsubs)
  }
  
  
	
}

