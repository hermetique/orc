package orc.trace.events;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;

import orc.trace.handles.RepeatHandle;
import orc.trace.values.AbstractValue;

/**
 * Spawning a new thread.
 * @author quark
 */
public class ForkEvent extends Event {
	@Override
	public void prettyPrint(Writer out, int indent) throws IOException {
		super.prettyPrint(out, indent);
		out.write("(");
		out.write(label());
		out.write(")");
	}
	@Override
	public String getType() { return "fork"; }
}
