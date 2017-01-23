//
// Options.scala -- Implementations of option manipulation sites
// Project OrcScala
//
// Created by dkitchin on March 31, 2011.
//
// Copyright (c) 2017 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.lib.builtin.structured

import orc.types._
import orc.values._
import orc.values.sites._

object OptionType extends SimpleTypeConstructor("Option", Covariant)

object NoneSite extends StructurePairSite(NoneConstructor, NoneExtractor)
object NoneConstructor extends TotalSite0 with TypedSite with FunctionalSite {
  override def name = "None"
  def eval() = None
  def orcType() = SimpleFunctionType(OptionType(Bot))
}
object NoneExtractor extends PartialSite1 with TypedSite with FunctionalSite {
  override def name = "None.unapply"
  def eval(a: AnyRef) = {
    a match {
      case None => Some(Signal)
      case Some(_) => None
      case _ => None
    }
  }
  def orcType() = SimpleFunctionType(OptionType(Top), SignalType)
}

object SomeSite extends StructurePairSite(SomeConstructor, SomeExtractor)
object SomeConstructor extends TotalSite1 with TypedSite with FunctionalSite {
  override def name = "Some"
  def eval(a: AnyRef) = Some(a)
  def orcType() = {
    val X = new TypeVariable()
    new FunctionType(List(X), List(X), OptionType(X))
  }
}
object SomeExtractor extends PartialSite1 with TypedSite with FunctionalSite {
  override def name = "Some.unapply"
  def eval(arg: AnyRef) = {
    arg match {
      case Some(v: AnyRef) => Some(v)
      case None => None
      case _ => None
    }
  }
  def orcType() = {
    val X = new TypeVariable()
    new FunctionType(List(X), List(OptionType(X)), X)
  }
}
