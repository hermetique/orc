//
// Optimizer.scala -- Scala class Optimizer
// Project OrcScala
//
// Created by amp on Jun 3, 2013.
//
// Copyright (c) 2017 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//
package orc.ast.porc

import orc.values.sites.{ Site => OrcSite }
//TODO: import orc.values.sites.DirectSite
import orc.compile.CompilerOptions
import orc.compile.OptimizerStatistics
import orc.compile.NamedOptimization
import scala.collection.mutable
import swivel.Zipper

trait Optimization extends ((Expression.Z, AnalysisProvider[PorcAST]) => Option[Expression]) with NamedOptimization {
  //def apply(e : Expression, analysis : ExpressionAnalysisProvider[Expression], ctx: OptimizationContext) : Expression = apply((e, analysis, ctx))
  val name: String
}

case class Opt(name: String)(f: PartialFunction[(Expression.Z, AnalysisProvider[PorcAST]), Expression]) extends Optimization {
  def apply(e: Expression.Z, analysis: AnalysisProvider[PorcAST]): Option[Expression] = f.lift((e, analysis))
}
case class OptFull(name: String)(f: (Expression.Z, AnalysisProvider[PorcAST]) => Option[Expression]) extends Optimization {
  def apply(e: Expression.Z, analysis: AnalysisProvider[PorcAST]): Option[Expression] = f(e, analysis)
}

/** @author amp
  */
case class Optimizer(co: CompilerOptions) extends OptimizerStatistics {
  def apply(e: PorcAST, analysis: AnalysisProvider[PorcAST]): PorcAST = {
    val trans = new Transform {
      override def onExpression = {
        case (e: Expression.Z) => {
          val e1 = opts.foldLeft(e)((e, opt) => {
            opt(e, analysis) match {
              case None => e
              case Some(e2) =>
                if (e.value != e2) {
                  import orc.util.StringExtension._
                  Logger.fine(s"${opt.name}: ${e.value.toString.truncateTo(60)}\n====>\n${e2.toString.truncateTo(60)}")
                  countOptimization(opt)
                  e.replace(e.value ->> e2)
                } else
                  e
            }
          })
          e1.value
        }
      }
    }

    trans(e.toZipper())
  }

  import Optimizer._

  def newName(x: Variable) = {
    new Variable(x.optionalName.map(_ + "i"))
  }
  
  def renameVariables(e: Method.Z)(implicit mapping: Map[Variable, Variable]): Method = {
    e.value ->> (e match {
      case MethodCPS.Z(name, p, c, t, isDef, args, b) =>
        val newArgs = args.map(newName)
        val (newP, newC, newT) = (newName(p), newName(c), newName(t))
        MethodCPS(mapping(name), newP, newC, newT, isDef, newArgs, renameVariables(b)(mapping + ((p, newP)) + ((c, newC)) + ((t, newT)) ++ (args zip newArgs)))
      case MethodDirect.Z(name, isDef, args, b) =>
        val newArgs = args.map(newName)
        MethodDirect(mapping(name), isDef, newArgs, renameVariables(b)(mapping ++ (args zip newArgs)))
    })
  }

  def renameVariables(e: Argument.Z)(implicit mapping: Map[Variable, Variable]): Argument = {
    e.value ->> (e.value match {
      case v: Variable =>
        mapping.getOrElse(v, v)
      case a =>
        a
    })
  }

  def renameVariables(e: Expression.Z)(implicit mapping: Map[Variable, Variable]): Expression = {
    e.value ->> (e match {
      case Let.Z(x, v, b) =>
        val newX = newName(x)
        Let(newX, renameVariables(v), renameVariables(b)(mapping + ((x, newX))))
      case Continuation.Z(args, b) =>
        val newArgs = args.map(newName)
        Continuation(newArgs, renameVariables(b)(mapping ++ (args zip newArgs)))
      case MethodDeclaration.Z(methods, b) =>
        val newMethodNames = methods.map(m => newName(m.name))
        val newMapping = mapping ++ (methods.map(_.name) zip newMethodNames)
        val newMethods = methods.map(m => renameVariables(m)(newMapping))
        MethodDeclaration(newMethods, renameVariables(b)(newMapping))
      case a: Argument.Z =>
        renameVariables(a)
      case CallContinuation.Z(t, args) =>
        CallContinuation(renameVariables(t), args.map(renameVariables))
      case Force.Z(p, c, t, b, futs) =>
        Force(renameVariables(p), renameVariables(c), renameVariables(t), b, futs.map(renameVariables))
      case Sequence.Z(exprs) =>
        Sequence(exprs.map(renameVariables))
      case MethodCPSCall.Z(external, target, p, c, t, args) =>
        MethodCPSCall(external, renameVariables(target), renameVariables(p), renameVariables(c), renameVariables(t), args.map(renameVariables))
      case MethodDirectCall.Z(external, target, args) =>
        MethodDirectCall(external, renameVariables(target), args.map(renameVariables))
      case IfDef.Z(a, left, right) =>
        IfDef(renameVariables(a), renameVariables(left), renameVariables(right))
      case GetField.Z(o, f) =>
        GetField(renameVariables(o), f)
      case New.Z(bindings) =>
        New(bindings.mapValues(renameVariables).view.force)
      case Spawn.Z(c, t, comp) =>
        Spawn(renameVariables(c), renameVariables(t), renameVariables(comp))
      case NewTerminator.Z(t) =>
        NewTerminator(renameVariables(t))
      case Kill.Z(t) =>
        Kill(renameVariables(t))
      case TryOnKilled.Z(b, h) =>
        TryOnKilled(renameVariables(b), renameVariables(h))

      case NewCounter.Z(b, h) =>
        NewCounter(renameVariables(b), renameVariables(h))
      case Halt.Z(c) =>
        Halt(renameVariables(c))
      case SetDiscorporate.Z(c) =>
        SetDiscorporate(renameVariables(c))
      case TryOnHalted.Z(b, h) =>
        TryOnHalted(renameVariables(b), renameVariables(h))
      case TryFinally.Z(b, h) =>
        TryFinally(renameVariables(b), renameVariables(h))

      case SpawnBindFuture.Z(fut, c, t, comp) =>
        SpawnBindFuture(renameVariables(fut), renameVariables(c), renameVariables(t), renameVariables(comp))
      case f @ NewFuture.Z() =>
        f.value
    })
  }

  val letInlineThreshold = co.options.optimizationFlags("porc:let-inline-threshold").asInt(30)
  val letInlineCodeExpansionThreshold = co.options.optimizationFlags("porc:let-inline-expansion-threshold").asInt(30)
  val referenceThreshold = co.options.optimizationFlags("porc:let-inline-ref-threshold").asInt(5)
  
  val InlineLet = OptFull("inline-let") { (expr, a) =>
    import a.ImplicitResults._
    expr match {
      case Let.Z(x, lam @ Continuation.Z(formals, impl), scope) =>
        def size = Analysis.cost(impl.value)
        lazy val (noncompatReferences, compatReferences, compatCallsCost) = {
          var refs = 0
          var refsCompat = 0
          var callsCost = 0
          (new Transform {
            override def onArgument = {
              case Zipper(`x`, _) =>
                refs += 1
                x
            }
            override def onExpression = {
              case e @ CallContinuation.Z(Zipper(`x`, _), _) =>
                refsCompat += 1
                callsCost += Analysis.cost(e.value)
                e.value
              case a: Argument.Z if onArgument.isDefinedAt(a) =>
                onArgument(a)
            }
          })(scope)
          (refs - refsCompat, refsCompat, callsCost)
        }

        val codeExpansion = compatReferences * size - compatCallsCost -
          (if (noncompatReferences == 0) size else 0)

        def doInline(rename: Boolean) = new Transform {
          override def onExpression = {
            case CallContinuation.Z(Zipper(`x`, _), args) =>
              val res = impl.replace(impl.value.substAll((formals zip args.map(_.value)).toMap))
              if (rename)
                renameVariables(res)(Map[Variable, Variable]())
              else
                res.value
          }
        }

        //Logger.finer(s"Attempting inline: $x: $compatReferences $noncompatReferences $compatCallsCost $size; $codeExpansion")
        if (compatReferences > 0 && codeExpansion <= letInlineCodeExpansionThreshold) {
          if (noncompatReferences > 0)
            Some(Let(x, lam.value, doInline(true)(scope)))
          else
            Some(doInline(compatReferences != 1)(scope))
        } else {
          None
        }
      case Let.Z(x, Zipper(a: Argument, _), scope)=>
        Some(scope.value.substAll(Map((x, a))))
      case e =>
        None
    }
  }
  /*

  val spawnCostInlineThreshold = co.options.optimizationFlags("porc:spawn-inline-threshold").asInt(30)

  val InlineSpawn = OptFull("inline-spawn") { (e, a) =>
    import a.ImplicitResults._
    e match {
      case SpawnIn(c, t, e) => {
        val c = Analysis.cost(e)
        if (c <= spawnCostInlineThreshold && e.fastTerminating)
          Some(e)
        else
          None
      }
      case _ => None
    }
  }

  /*
  This may not be needed because site inlining is already done in Orc5C

  val siteInlineThreshold = 50
  val siteInlineCodeExpansionThreshold = 50
  val siteInlineTinySize = 12

  val InlineSite = OptFull("inline-site") { (e, a) =>
    import a.ImplicitResults._
    e match {
      case SiteCallIn((t:Var) in ctx, args, _) => ctx(t) match {
        case SiteBound(dctx, Site(_,k), d) => {
          def recursive = d.body.freevars.contains(d.name)
          lazy val compat = ctx.compatibleForSite(d.body)(dctx)
          lazy val size = Analysis.cost(d.body)
          def smallEnough = size <= siteInlineThreshold
          lazy val referencedN = Analysis.count(k, {
            case SiteCall(`t`, _) => true
            case _ => false
          })
          def tooMuchExpansion = (referencedN-1)*size > siteInlineCodeExpansionThreshold && size > siteInlineTinySize
          //println(s"Inline attempt: ${e.e} ($referencedN, $size, $compat)")
          if ( recursive || !smallEnough || !compat || tooMuchExpansion )
            None // No inlining of recursive functions or large functions.
          else
            Some(d.body.substAll(((d.arguments: List[Var]) zip args).toMap))
        }
        case _ => None
      }
      case _ => None
    }
  }
   */

*/
  val allOpts = List[Optimization](InlineLet)

  val opts = allOpts.filter { o =>
    co.options.optimizationFlags(s"porc:${o.name}").asBool()
  }
}

object Optimizer {
  /*
  object <::: {
    def unapply(e: PorcAST.Z) = e match {
      case Sequence(l) in ctx if !l.isEmpty =>
        Some(Sequence(l.init) in ctx, l.last in ctx)
      case _ => None
    }
  }
  object :::> {
    def unapply(e: PorcAST.Z) = e match {
      case Sequence(e :: l) in ctx =>
        Some(e in ctx, Sequence(l) in ctx)
      case _ => None
    }
  }

  object LetStackIn {
    def unapply(e: PorcAST.Z): Some[(Seq[(Option[Var.Z], Expr.Z)], PorcAST.Z)] = e match {
      case LetIn(x, v, b) =>
        val LetStackIn(bindings, b1) = b
        Some(((Some(x), v) +: bindings, b1))
      case s :::> ss if !ss.es.isEmpty =>
        val LetStackIn(bindings, b1) = ss.simplify in ss.ctx
        //Logger.fine(s"unpacked sequence: $s $ss $bindings $b1")
        Some(((None, s) +: bindings, b1))
      case _ => Some((Seq(), e))
    }
  }

  object LetStack {
    def apply(bindings: Seq[(Option[Var.Z], Expr.Z)], b: Expr) = {
      bindings.foldRight(b)((bind, b) => {
        bind match {
          case (Some(x), v) =>
            Let(x, v, b)
          case (None, v) =>
            v ::: b
        }
      })
    }
    /*def apply(bindings: Map[Var, Expr], b: Expr) = {
      bindings.foldRight(b)((bind, b) => {
        val (x, v) = bind
        Let(x, v, b)
      })
    }*/
  }

  val EtaReduce = Opt("eta-reduce") {
    case (ContinuationIn(formal, _, CallIn(t, arg, _)), a) if arg == formal =>
      t
  }
  val EtaSpawnReduce = Opt("eta-spawn-reduce") {
    case (ContinuationIn(formal, _, SpawnIn(_, _, CallIn((t: Var) in ctx, arg, _))), a) if arg == formal && ctx(t).isInstanceOf[DefArgumentBound] =>
      t
  }

  val LetElim = Opt("let-elim") {
    //case (LetIn(x, v, b), a) if !b.freevars.contains(x) && a(v).doesNotThrowHalt && a(v).sideEffectFree => b
    case (LetIn(x, ContinuationIn(_, _, _), b), a) if !b.freevars.contains(x) => b
    case (LetIn(x, TupleElem(_, _) in _, b), a) if !b.freevars.contains(x) => b
    case (LetIn(x, v, b), a) if !b.freevars.contains(x) => v ::: b
  }
  val VarLetElim = Opt("var-let-elim") {
    case (LetIn(x, (y: Var) in _, b), a) => b.substAll(Map((x, y)))
  }
  val DefElim = Opt("def-elim") {
    case (DefDeclarationIn(ds, _, b), a) if (b.freevars & ds.map(_.name).toSet).isEmpty => b
  }

  val SpecializeSiteCall = OptFull("specialize-sitecall") { (e, a) =>
    import a.ImplicitResults._
    import PorcInfixNotation._
    e match {
      case SiteCallIn(target, p, c, t, args, ctx) if target.siteMetadata.map(_.isDirectCallable).getOrElse(false) =>
        val v = new Var()
        Some(
          TryOnHalted({
            let((v, SiteCallDirect(target, args))) {
              p(v)
            }
          }, Unit()))
      case _ => None
    }
  }

  val OnHaltedElim = OptFull("onhalted-elim") { (e, a) =>
    // TODO: Figure out why this is taking multiple passes to finish. This should eliminate all excess onHalted expressions in one pass.
    e match {
      case TryOnHaltedIn(LetStackIn(bindings, TryOnHaltedIn(b, h1)), h2) if h1.e == h2.e =>
        Some(TryOnHalted(LetStack(bindings, b), h2))
      case _ => None
    }
  }
  */
}
