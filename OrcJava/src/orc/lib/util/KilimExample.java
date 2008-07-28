package orc.lib.util;

import java.util.concurrent.Callable;

import orc.runtime.Kilim;
import kilim.Task;
import kilim.pausable;

/**
 * This example class shows how to write a Java
 * site which uses features of Kilim. See examples/kilim.orc.
 * 
 * @author quark
 */
public class KilimExample {
	private String id;
	public KilimExample(String id) {
		this.id = id;
	}
	
	/** Do not publish */
	public @pausable void exit() {
		Task.exit(id + " exiting");
	}
	
	/** Signal an error */
	public @pausable void error() throws Exception {
		throw new Exception("ERROR");
	}
	
	/** Publish after millis milliseconds. */
	public @pausable String sleep(Number millis) {
		Task.sleep(millis.longValue());
		return id;
	}
	
	public @pausable String sleepThread(final Number millis) throws Exception {
		Kilim.runThreaded(new Callable<Object>() {
			public Object call() throws InterruptedException {
				Thread.sleep(millis.longValue());
				return Kilim.signal;
			}
		});
		return id;
	}
	
	/** Send a signal. */
	public void signal() {}
}
