package orc.ast.extended.pattern;

import orc.ast.extended.Visitor;
import orc.ast.extended.expression.Literal;
import orc.ast.simple.*;
import orc.ast.simple.argument.*;
import orc.error.compiletime.NonlinearPatternException;
import orc.error.compiletime.PatternException;
import xtc.util.Utilities;

public class EqPattern extends Pattern {

	public NamedVar x;
	
	public EqPattern(String s)
	{
		x = new NamedVar(s);
	}

	@Override
	public void process(Var fragment, PatternSimplifier visitor)
			throws PatternException {
		Var test = new Var();
		visitor.assign(test, new WithLocation(
				Pattern.compare(fragment, x),
				getSourceLocation()));
		visitor.require(test);
	}
	
	public String toString() {
		return x.name.toString();
	}

	/* (non-Javadoc)
	 * @see orc.ast.extended.ASTNode#accept(orc.ast.oil.Visitor)
	 */
	public <E> E accept(Visitor<E> visitor) {
		return visitor.visit(this);
	}
}
