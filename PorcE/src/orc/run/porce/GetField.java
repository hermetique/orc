
package orc.run.porce;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Introspectable;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import orc.Accessor;
import orc.CaughtEvent;
import orc.ErrorAccessor;
import orc.error.runtime.HaltException;
import orc.run.porce.runtime.PorcEExecutionRef;
import orc.values.Field;

@NodeChild(value = "object", type = Expression.class)
@Introspectable
public abstract class GetField extends Expression {
    protected final Field field;
    protected final PorcEExecutionRef execution;

    protected GetField(final Field field, final PorcEExecutionRef execution) {
        this.field = field;
        this.execution = execution;
    }

    @Specialization(guards = { "isNotError(accessor)", "canGetWithBoundary(accessor, obj)" }, limit = "getMaxCacheSize()")
    public Object cachedAccessor(final Object obj, @Cached("getAccessorWithBoundary(obj)") final Accessor accessor) {
        try {
            return accessWithBoundary(accessor, obj);
        } catch (final Exception e) {
            CompilerDirectives.transferToInterpreter();
            execution.get().notifyOrcWithBoundary(new CaughtEvent(e));
            throw HaltException.SINGLETON();
        }
    }

    @Specialization(replaces = { "cachedAccessor" })
    public Object slowPath(final Object obj) {
        try {
            final Accessor accessor = getAccessorWithBoundary(obj);
            return accessWithBoundary(accessor, obj);
        } catch (final Exception e) {
            CompilerDirectives.transferToInterpreter();
			// TODO: Wrap exception to include Orc stack information. This will mean wrapping this in JavaException if needed and calling setBacktrace
            execution.get().notifyOrcWithBoundary(new CaughtEvent(e));
            throw HaltException.SINGLETON();
        }
    }

    @TruffleBoundary
    protected Accessor getAccessorWithBoundary(final Object t) {
        return execution.get().runtime().getAccessor(t, field);
    }

    @TruffleBoundary(allowInlining = true, throwsControlFlowException = true)
    protected static Object accessWithBoundary(final Accessor accessor, final Object obj) {
        return accessor.get(obj);
    }

    @TruffleBoundary(allowInlining = true)
    protected static boolean canGetWithBoundary(final Accessor accessor, final Object obj) {
        return accessor.canGet(obj);
    }

    protected static boolean isNotError(final Accessor accessor) {
        return !(accessor instanceof ErrorAccessor);
    }

    protected static int getMaxCacheSize() {
        return SpecializationConfiguration.GetFieldMaxCacheSize;
    }

    public static GetField create(final Expression object, final Field field, final PorcEExecutionRef execution) {
        return GetFieldNodeGen.create(field, execution, object);
    }
}
