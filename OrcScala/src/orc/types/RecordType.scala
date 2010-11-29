//
// RecordType.scala -- Scala class/trait/object RecordType
// Project OrcScala
//
// $Id$
//
// Created by dkitchin on Nov 20, 2010.
//
// Copyright (c) 2010 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//
package orc.types

import scala.collection.immutable.HashMap
import orc.error.compiletime.typing.ArgumentTypeMismatchException

/**
 * 
 * Semantic type of records.
 *
 * @author dkitchin
 */
case class RecordType(entries: Map[String,Type]) extends UnaryCallableType {

  override def toString = {
    val ks = entries.keys.toList
    val entryStrings = ks map { k => k + " :: " + entries(k) }
    entryStrings.mkString("{. ",", "," .}")
  }
  
  override def join(that: Type): Type = {
     that match {
       case RecordType(otherEntries) => {
         val joinKeySet = entries.keySet intersect otherEntries.keySet
         val joinEntries =
           for (k <- joinKeySet) yield {
             val t = entries(k)
             val u = otherEntries(k)
             (k, t join u)
           }
         RecordType(HashMap.empty ++ joinEntries)
       }
       case _ => super.join(that)
     }
   }
  
  override def meet(that: Type): Type = {
    that match {
      case RecordType(otherEntries) => {
        val meetKeySet = entries.keySet union otherEntries.keySet
        val meetEntries =
          for (k <- meetKeySet) yield {
            val t = entries.getOrElse(k, Top)
            val u = otherEntries.getOrElse(k, Top)
            (k, t meet u)
          }
        RecordType(HashMap.empty ++ meetEntries)
      }
      case _ => super.join(that)
    }
  }
  
  override def <(that: Type): Boolean = {
    that match {
      case RecordType(otherEntries) => {
        val keys = entries.keySet
        val otherKeys = otherEntries.keySet
        
        (otherKeys subsetOf keys) &&
        (otherKeys forall { k => entries(k) < otherEntries(k) })
      }
      case _ => super.<(that)
    }
  }
  
  override def subst(sigma: Map[TypeVariable, Type]): Type = {
    RecordType(entries mapValues { _ subst sigma })
  }
  
  override def call(argType: Type) = {
    argType match {
      case FieldType(f) => entries.getOrElse(f, Bot)
      case t => throw new ArgumentTypeMismatchException(0, FieldType("_"), t)
    }
  }
  
}