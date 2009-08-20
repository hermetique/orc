package orc.runtime.nodes;

import java.util.List;
import java.util.Set;

import orc.ast.oil.expression.argument.Var;
import orc.error.SourceLocation;

/**
 * 
 * A unit of syntax that encapsulates an expression definition.
 * 
 * Groups of mutually recursive definitions are embedded in the execution graph
 * by a Defs node.
 * 
 * @author dkitchin
 * 
 */

public class Def {

	public int arity;
	public Node body;
	public Set<Var> free;
	public SourceLocation location;

	public Def(int arity, Node body, Set<Var> free, SourceLocation location) {
		this.arity = arity;
		this.body = body;
		this.free = free;
		this.location = location;
	}
}
