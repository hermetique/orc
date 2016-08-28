//
// NamedInfixCombinators.scala -- Scala trait NamedInfixCombinators
// Project OrcScala
//
// $Id$
//
// Created by dkitchin on May 31, 2010.
//
// Copyright (c) 2011 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.ast.orctimizer.named

// Infix combinator constructors
trait NamedInfixCombinators {
  self: Expression =>

  def ||(g: Expression) = Parallel(this, g)

  def >>(g: Expression) = Branch(this, new BoundVar(), g)

  trait WithGreater {
    def >(g: Expression): Expression
  }
  def >(x: BoundVar): WithGreater =
    new WithGreater {
      def >(g: Expression) = Branch(NamedInfixCombinators.this, x, g)
    }
  
  def otw(g: Expression) = Otherwise(this, g)
}

// Infix combinator extractors
object || {
  def unapply(e: NamedAST) =
    e match {
      case Parallel(l, r) => Some((l, r))
      case _ => None
    }
  def unapply(e: WithContext[NamedAST]) =
    e match {
      case Parallel(l, r) in ctx => Some((l in ctx, r in ctx))
      case _ => None
    }
}

object > {
  def unapply(e: NamedAST) = {
    e match {
      case Branch(f, x, g) => Some(((f, x), g))
      case _ => None
    }
  }
  def unapply(e: WithContext[NamedAST]) = {
    e match {
      case (n@Branch(f, x, g)) in ctx => {
        val newctx = ctx + Bindings.SeqBound(ctx, n)
        Some(((f in ctx, x), g in newctx))
      }
      case _ => None
    }
  }
  def unapply[T](p: (T, BoundVar)) = Some(p)
}


object DeclareDefsAt {
  def unapply(e: WithContext[Expression]) = e match {
    case (n@DeclareDefs(defs, body)) in ctx => {
      val defsctx = ctx extendBindings (defs map { Bindings.RecursiveDefBound(ctx, n, _) })
      val bodyctx = ctx extendBindings (defs map { Bindings.DefBound(defsctx, n, _) })
      Some(defs, defsctx, body in bodyctx)
    }
    case _ => None
  }
}
object DefAt {
  def unapply(e: WithContext[NamedAST]) = e match {
    case (d@Def(name, formals, body, typeformals, argtypes, returntype)) in ctx => {
      val newctx = ctx extendBindings (formals map { Bindings.ArgumentBound(ctx, d, _) }) extendTypeBindings (typeformals map { TypeBinding(ctx, _) })
      Some(name in ctx, formals, body in newctx, typeformals, argtypes, returntype, newctx)
    }
    case _ => None
  }
}
object TrimAt {
  def unapply(e: WithContext[Expression]) = e match {
    case (n@Trim(f)) in ctx => {
      Some(f in ctx)
    }
    case _ => None
  }
}
object FutureAt {
  def unapply(e: WithContext[Expression]) = e match {
    case (n@Future(x, f, g)) in ctx => {
      val newctx = ctx + Bindings.FutureBound(ctx, n)
      Some(x, f in ctx, g in newctx)
    }
    case _ => None
  }
}
object ForceAt {
  def unapply(e: WithContext[Expression]) = e match {
    case (n@Force(xs, vs, b, e)) in ctx => {
      val newctx = ctx extendBindings (xs.map(x => Bindings.ForceBound(ctx, n, x)))
      Some(xs, vs map (_ in ctx), b, e in newctx)
    }
    case _ => None
  }
}
object CallDefAt {
  def unapply(e: WithContext[Expression]) = e match {
    case (n@CallDef(t, args, targs)) in ctx => {
      Some(t in ctx, args, targs, ctx)
    }
    case _ => None
  }
}
object CallSiteAt {
  def unapply(e: WithContext[Expression]) = e match {
    case (n@CallSite(t, args, targs)) in ctx => {
      Some(t in ctx, args, targs, ctx)
    }
    case _ => None
  }
}
object OtherwiseAt {
  def unapply(e: WithContext[Expression]) = e match {
    case (n@Otherwise(f, g)) in ctx => {
      Some(f in ctx, g in ctx)
    }
    case _ => None
  }
}
object IfDefAt {
  def unapply(e: WithContext[Expression]) = e match {
    case (n@IfDef(a, f, g)) in ctx => {
      Some(a in ctx, f in ctx, g in ctx)
    }
    case _ => None
  }
}
object DeclareTypeAt {
  def unapply(e: WithContext[Expression]) = e match {
    case (n@DeclareType(tv, t, b)) in ctx => {
      val newctx = ctx + TypeBinding(ctx, tv)
      Some(tv in newctx, t in newctx, b in newctx)
    }
    case _ => None
  }
}