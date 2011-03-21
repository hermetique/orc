//
// Value.scala -- Scala class Value
// Project OrcScala
//
// $Id: OrcValue.scala 2175 2010-10-29 23:45:34Z dkitchin $
//
// Created by dkitchin on May 10, 2010.
//
// Copyright (c) 2010 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.values

import orc.lib.builtin.DataSite

/**
 * An Orc-specific value, such as: a closure, a tagged value 
 * of a user-defined Orc datatype, or a signal. 
 *
 * @author dkitchin
 */
trait OrcValue extends AnyRef {
  
  /**
   * The method toOrcSyntax has the following contract:
   * 
   * A value which can be written in an Orc program is formatted as a string which
   * the parser would parse as an expression evaluating to that value.
   * 
   * A value which cannot be written in an Orc program is formatted in some
   * readable pseudo-syntax, but with no guarantees about its parsability.
   * @return
   */
  def toOrcSyntax(): String = super.toString() 
  override def toString() = toOrcSyntax()  
  
}

object OrcValue {
  
  /**
   * Condense a list of values, using the classic Let site behavior.
   * @param vs
   * @return
   */
  def letLike(vs: List[AnyRef]) = {
    vs match {
      case Nil => Signal
      case List(v) => v
      case _ => OrcTuple(vs)
    }
  }
  
}



// Some very simple values

case object Signal extends OrcValue {
  override def toOrcSyntax() = "signal"
}

case class Field(field: String) extends OrcValue {
  override def toOrcSyntax() = "." + field
}

case class TaggedValue(tag: DataSite, values: List[AnyRef]) extends OrcValue {
  override def toOrcSyntax = tag.toOrcSyntax() + "(" + Format.formatSequence(values) + ")"
}

