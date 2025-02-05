//
// NQueens.scala -- Scala benchmark NQueens
// Project OrcTests
//
// Copyright (c) 2018 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.test.item.scalabenchmarks

import NQueensTypes.{ Queen, Solutions }

object NQueensTypes {
  type Queen = (Int, Int)
  type Solutions = Iterable[List[Queen]]
}

// From: https://gist.github.com/ornicar/1115259

// Solves the n-queens problem for an arbitrary board size
// Run for a board size of ten: scala nqueen.scala 10
object NQueens extends BenchmarkApplication[Unit, Solutions] with ExpectedBenchmarkResult[Solutions] {
  val N: Int = (8 + math.log10(BenchmarkConfig.problemSize)).toInt

  def isSafe(queen: Queen, others: List[Queen]) =
    others forall (!isAttacked(queen, _))

  def isAttacked(q1: Queen, q2: Queen) =
    q1._1 == q2._1 ||
      q1._2 == q2._2 ||
      (q2._1 - q1._1).abs == (q2._2 - q1._2).abs

  def benchmark(ctx: Unit): Solutions = {
    def placeQueens(n: Int): Solutions = n match {
      case _ if n == 0 => List(Nil)
      case _ => for {
        queens <- placeQueens(n - 1)
        y <- 0 until N
        queen = (n, y)
        if (isSafe(queen, queens))
      } yield queens :+ queen
    }
    placeQueens(N)
  }

  def setup(): Unit = ()

  val name: String = "N-Queens"

  def factorial(n: BigInt): BigInt = {
    if (n > 1)
      n * factorial(n-1)
    else
      1
  }

  val size: Int = factorial(N).toInt

  override def hash(results: Solutions): Int = results.toSet.##()

  val expectedMap: Map[Int, Int] = Map(
      10 -> 0xae0ba7ef,
      100 -> 0xdcf13a95,
      //100 -> 0xabcb3752,
      )
}
