//
// TranslateToPorc.scala -- Scala class/trait/object TranslateToPorc
// Project OrcScala
//
// $Id$
//
// Created by amp on May 27, 2013.
//
// Copyright (c) 2013 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//
package orc.ast.oil.named.orc5c

import orc.ast.porc
import orc.ast.porc._
import orc.values.sites.{Site => OrcSite}
import orc.values.sites.TotalSite1
import orc.values.Signal
import orc.values.Format
import orc.Handle
import orc.PublishedEvent
import orc.lib.builtin.MakeSite

/**
  *
  * @author amp
  */
object TranslateToPorc { 
  import PorcInfixNotation._
  
  // TODO: Use a varient of the TransformContext allowing us to know how variables where bound and translate to better code.
  
  case class TranslationContext(
      p: porc.Value,
      h: porc.Value,
      sites: Map[OrcSite, porc.SiteVariable] = Map(), 
      variables: Map[BoundVar, porc.Var] = Map(),
      siteInfo: Map[BoundVar, Set[porc.Var]] = Map(),
      recursives: Set[BoundVar] = Set()) {
    def +(v: (BoundVar, porc.Var)) = this.copy(variables = variables + v)
    def ++(s: Iterable[(BoundVar, porc.Var)]) = this.copy(variables = variables ++ s)
    def apply(v: BoundVar) = variables(v)
    def site(v: BoundVar) = siteInfo.get(v)

    //def +(s: OrcSite, v: porc.SiteVariable) = this.copy(sites = sites + (s -> v))
    //def ++(s: Iterable[(OrcSite, porc.SiteVariable)]) = this.copy(sites = sites ++ s)
    def apply(v: OrcSite) = sites(v)
    
    def isRecursive(v: BoundVar) = recursives(v)
    
    def addRecursives(vs: Iterable[BoundVar]) = this.copy(recursives = recursives ++ vs)
    def addSiteInfo(vs: Iterable[(BoundVar, Set[porc.Var])]) = this.copy(siteInfo = siteInfo ++ vs)
    
    def setP(v: Value) = copy(p=v)
    def setH(v: Value) = copy(h=v)
  }
  
  def orc5cToPorc(e: Expression): porc.Command = {
    val topP = new ClosureVariable(Some("Publish"))
    val topH = new ClosureVariable(Some("Halt"))
    
    //val (sites, names, defs) = e.referencedSites.toList.sortBy(_.name).map(wrapSite).unzip3
    val (sites, names, defs) = porcImplementedSiteDefs.unzip3
    
    val p = translate(e)(TranslationContext(topP, topH, sites = (sites zip names).toMap))
    val sp = defs.foldLeft(p)((p, s) => Site(List(s), p))
    
    val x = new Variable("x")
    val h = new Variable("h")

    val printSite = new orc.values.sites.Site {
      override val name = "PublishToUser"
        
      def call(args: List[AnyRef], h: Handle) {
        h.notifyOrc(PublishedEvent(args(0)))
        h.publish(Signal)
      }
      override val immediateHalt = true
      override val immediatePublish = true
      override val publications = (1, Some(1))
    }
    
    val noopP = new ClosureVariable("noop")

    let(topH() === Die()) {
      setCounterHalt(topH) {
        let(noopP(x, h) === h(),
          topP(x, h) === ExternalCall(printSite, Tuple(x), noopP, h)) {
            sp
          }
      }
    }
  }
  
  val porcImplementedSites = Map[OrcSite, (Variable, Variable, Variable) => Command](
    MakeSite -> { (args, p, h) =>
      val f = new Variable("f")
      val f1 = new SiteVariable("f'")
      val args1 = new Variable("args")
      val p1 = new Variable("P")
      val h1 = new Variable("H")
      val topH = new ClosureVariable("Halt")
      Unpack(List(f), args,
        Site(List(SiteDef(f1, List(args1, p1, h1),
          NewTerminator {
            NewCounterDisconnected {
              let(topH() === restoreCounter {
                    h1()
                  } {
                    Die()
                  }) {
                setCounterHalt(topH) {
                  f sitecall (args1, p1, topH)
                }
              }
            }
          })),
          p(f1, h)))

    })
    
  val porcImplementedSiteDefs = {
    for ((s, b) <- porcImplementedSites) yield {
      val name = new SiteVariable(Some("_" + s.name))
      val args = new Variable("args")
      val p = new Variable("P")
      val h = new Variable("H")
      val body = porcImplementedSites.get(s) match {
        case Some(b) => b(args, p, h)
      }
      (s, name, SiteDef(name, List(args, p, h), body))
    }
  }.toSeq
  
  def wrapSite(s: OrcSite) = {
    val name = new SiteVariable(Some("_" + s.name))
    val args = new Variable("args")
    val p = new Variable("P")
    val h = new Variable("H")
    val body = porcImplementedSites.get(s) match {
      case Some(b) => b(args, p, h)
      case None => {
        val pp = new ClosureVariable("pp")
        val x = new Variable("x")
        val impl = let(pp(x) === ExternalCall(s, x, p, h)) {
          Force(args, pp, h)
        }
        if (s.effectFree)
          impl
        else
          IsKilled(h(), impl)
      }
    }
    (s, name, SiteDef(name, List(args, p, h), body))
  }
  
  def translate(e: Expression)(implicit ctx: TranslationContext): porc.Command = {
    import ctx.{h, p}
    e -> {
      case Stop() => ClosureCall(h, Nil)
      case f || g => {
        val gCl = new ClosureVariable("g")
        val hInG = new Variable("h")
        let(gCl(hInG) === translate(g)(ctx setH hInG)) {
          spawn(gCl) {
            translate(f)
          }
        }
      }
      case f > x > g => {
        val p1Cl = new ClosureVariable("p'")
        val hInP1 = new Variable("h")
        val x1 = new Variable(x.optionalVariableName)
        let(p1Cl(x1, hInP1) === {
          val gCl = new ClosureVariable("g")
          val hInG = new Variable("h'")
          let(gCl(hInG) === (translate(g)(ctx + ((x, x1)) setH hInG))) {
            spawn(gCl) {
              ClosureCall(hInP1, Nil)
            }
          }
        }) {
          translate(f)(ctx setP p1Cl)
        }
      }
      case f ow g => {
        val hasPublished = new Variable("hasPublished")
        flag(hasPublished) {
          NewCounter {
            val h1 = new ClosureVariable("h'")
            let(h1() === {
              restoreCounter {
                getCounterHalt { ch =>
                  readFlag(hasPublished) {
                    ch()
                  } {
                    translate(g)(ctx setH ch)
                  }
                }
              }{
                Die()
              }
            }) {
              setCounterHalt(h1) {
                val p1 = new ClosureVariable("p'")
                val hlocal = new ClosureVariable("hlocal")
                val hInP1 = new Variable("h''")
                val x = new Variable("x")
               
                let(p1(x,hInP1) === {
                  setFlag(hasPublished) { p(x, hInP1) }
                },
                    hlocal() === { 
                  restoreCounter {
                    DecrCounter {
                    getCounterHalt { ch =>
                      readFlag(hasPublished) {
                        h()
                      } {
                        translate(g)
                      }
                    }
                    }
                  }{
                    h()
                  }
                }) {
                  translate(f)(ctx setP p1 setH hlocal)
                }
              }
            }
          }
        }
      }
      case f < x <| g => {
        val x1 = x ->> new Variable()
        future(x1) {
          val fCl = new ClosureVariable("f")
          val hInF = new Variable("h'")
          let(fCl(hInF) === {
            translate(f)(ctx + ((x, x1)) setH hInF)
          }) {
            spawn(fCl) {
              NewCounter {
                val h1 = new ClosureVariable("h''")
                let(h1() === {
                  restoreCounter {
                    stop(x1) {
                      getCounterHalt { ch =>
                        ch()
                      }
                    }
                  } {
                    Die()
                  }
                }) {
                  setCounterHalt(h1) {
                    val p1 = new ClosureVariable("p'")
                    val hlocal = new ClosureVariable("hlocal")
                    val hInP1 = new Variable("h''")
                    val xv = new Variable("xv")

                    let(p1(xv, hInP1) === {
                      bind(x1, xv) { hInP1() }
                    },
                      hlocal() === {
                        restoreCounter {
                          DecrCounter { stop(x1) { h() } }
                        } {
                          h()
                        }
                      }) {
                        translate(g)(ctx setP p1 setH hlocal)
                      }
                  }
                }
              }
            }
          }
        }
      }
      case Limit(f) => {
        getTerminator { t =>
          NewTerminator {
            val killHandler = new ClosureVariable("kH")
            let(killHandler() === {
              kill {
                CallKillHandlers {
                  Die()
                }
              } {
                Die()
              }
            }) {
              addKillHandler(t, killHandler) {
                val p1 = new ClosureVariable("p'")
                val hInP1 = new Variable("h'")
                val xv = new Variable("xv")
                let(p1(xv, hInP1) === {
                  kill {
                    CallKillHandlers {
                      p(xv, hInP1)
                    }
                  } {
                    hInP1()
                  }
                }) {
                  translate(f)(ctx setP p1)
                }
              }
            }
          }
        }
      }
      case Call(target : BoundVar, args, _) if ctx.isRecursive(target) => {
        IsKilled(h(),
            argumentToPorc(target, ctx) sitecall (Tuple(args.map(argumentToPorc(_, ctx))), p, h)
        )
      }      
      case Call(target : BoundVar, args, _) => {
        val pp = new ClosureVariable("pp")
        val x = new Variable("x")
        val x1 = new Variable("x'")
        let(pp(x) === Unpack(List(x1), x, x1 sitecall (Tuple(args.map(argumentToPorc(_, ctx))), p, h))) {
          Force(Tuple(ctx(target)), pp, h)
        } 
      }
      case Call(target, args, _) => {
        argumentToPorc(target, ctx) sitecall (Tuple(args.map(argumentToPorc(_, ctx))), p, h)
      }
      case DeclareDefs(defs, body) => {
        val names = defs.map(_.name)
        val newnames = (for(name <- names) yield (name, new SiteVariable(name.optionalVariableName))).toMap
        val closedVariables = (for(Def(_, formals, body, _, _, _) <- defs) yield {
          body.freevars -- formals
        }).flatten.toSet -- names
        val closedPorcVars = closedVariables.map(ctx(_))
        val ctx1 : TranslationContext = (ctx ++ newnames).addSiteInfo(names map {(_, closedPorcVars)})
        val ctxdefs = ctx1 addRecursives names
        val sitedefs = for(Def(name, formals, body, _, _, _) <- defs) yield {
          val newformals = for(x <- formals) yield new Variable(x.optionalVariableName)
          val p1 = new Variable("P")
          val h1 = new Variable("H")
          val args = new Variable("args")
          SiteDef(newnames(name), 
              List(args, p1, h1),
              Unpack(newformals, args, translate(body)(ctxdefs ++ (formals zip newformals) setP p1 setH h1)))
        }
        Site(sitedefs, translate(body)(ctx1))
      }
      case DeclareType(_, _, b) => translate(b)
      case HasType(b, _) => translate(b)
      case v : Constant => {
        p(argumentToPorc(v, ctx), h)
      }
      case v : BoundVar if (ctx site v).isEmpty => {
        val pp = new ClosureVariable("pp")
        val x = new Variable("x")
        val x1 = new Variable("x'")
        let(pp(x) === Unpack(List(x1), x,p(x1, h))) {
          Force(Tuple(ctx(v)), pp, h)
        }
      }
      case v : BoundVar if (ctx site v).isDefined => {
        val Some(cvs) = ctx site v
        val pp = new ClosureVariable("pp")
        val x = new Variable("x")
        val xs = (0 to cvs.size).map(i => new Variable(s"x${i}_")).toList
        let(pp(x) === Unpack(xs, x,p(xs(0), h))) {
          Force(Tuple(ctx(v) :: cvs.toList), pp, h)
        }
      }
      case _ => throw new Error(s"Unable to handle expression $e")
    }
    
  }
  
  def argumentToPorc(v : Argument, ctx : TranslationContext) : Value = {
    v -> {
      case v : BoundVar => ctx(v)
      case Constant(s : OrcSite) if ctx.sites.contains(s) => ctx(s)
      case Constant(c) => porc.Constant(c)
    }
  }
}