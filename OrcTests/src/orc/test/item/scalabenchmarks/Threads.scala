//
// Threads.scala -- Scala benchmark Threads
// Project OrcTests
//
// Copyright (c) 2017 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.test.item.scalabenchmarks

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration

object Threads {
  import scala.concurrent.ExecutionContext.Implicits.global

  /*
	This program creates 2^20 (or about 1 million) threads
	and waits for them to terminate.
	*/

  val N = 18
  def threads(n: Int): Unit = {
    if (n != 0) {
      val t = Future {
        threads(n - 1)
      }
      threads(n - 1)
      Await.ready(t, Duration.Inf)
    }
  }

  def main(args: Array[String]): Unit = {
    // Don't actually run it. It will DOS, at least linux systems, by spawning 2^N threads. Good fun.
    //threads(N)
    //ts.foreach(_.join())
  }
}
