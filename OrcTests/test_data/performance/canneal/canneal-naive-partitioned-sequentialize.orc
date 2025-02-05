{-
An extremely naive implementation of Canneal using Scala data structures and computation and atomic primitives.

The algorithm is expressed in Orc however.

This is so naive that it isn't even cache-aware. That means this is not even quite canneal; it's really just anneal.
-}

include "benchmark.inc"

import site Sequentialize = "orc.compile.orctimizer.Sequentialize"

import class Canneal = "orc.test.item.scalabenchmarks.canneal.Canneal"
import class NetList = "orc.test.item.scalabenchmarks.canneal.NetList"
import class ThreadLocalRandom = "java.util.concurrent.ThreadLocalRandom"

def random() = ThreadLocalRandom.current().nextDouble()

val swapsPerTemp = problemSizeScaledInt(3750)
val initialTemperature = 2000 
val pathname = Canneal.localInputFile()
val nTempSteps = 128
val nThreads = nPartitions
val nSwapsPerThread = swapsPerTemp / nThreads

def run(netlist) =
    def keepGoing(tempStep) = Sequentialize() >> tempStep <: nTempSteps
    def swapCost(a, b) = Sequentialize() >> (
	    val aLoc = a.location().get()
	    val bLoc = b.location().get()
	    a.swapCost(aLoc, bLoc) + b.swapCost(bLoc, aLoc)
    )
    
    def anneal(temperature, tempStep) =
    	upto(nThreads) >> Sequentialize() >> upto(nSwapsPerThread) >> (
	        val a = netlist.randomElement(ThreadLocalRandom.current())
	        val b = netlist.randomElement(a, ThreadLocalRandom.current())
	        val cost = swapCost(a, b)
	        if cost <: 0 then
	        	a.location().swap(b.location())
	        else (
				val boltsman = Exp(- cost / temperature)
				if random() <: boltsman then
					a.location().swap(b.location())
				else
					signal
	        )
        ) >> stop ; (
        if keepGoing(tempStep) then
	        anneal(temperature / 1.5, tempStep + 1)
	    else
	    	signal
    	)
    
    anneal(initialTemperature, 0)

val netlist = NetList(pathname)
val _ = Println(netlist.elements().size()) 
val _ = Println((nThreads, nSwapsPerThread))

benchmarkSized("Canneal-naive-partitioned-sequentialize", nTempSteps * swapsPerTemp, { netlist.resetLocations() >> netlist }, run, { _ >> false {- TODO: Implement checking -} })

{-
BENCHMARK
-}
