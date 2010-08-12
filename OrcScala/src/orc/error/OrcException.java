//
// OrcException.java -- Java class OrcException
// Project OrcScala
//
// $Id$
//
// Copyright (c) 2010 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.error;

import java.io.PrintWriter;
import java.io.StringWriter;

import scala.util.parsing.input.NoPosition$;
import scala.util.parsing.input.Position;

/**
 * Any exception generated by Orc, during compilation, loading, or execution.
 * Though sites written in Java will sometimes produce Java-level exceptions,
 * those exceptions are wrapped in a subclass of OrcException to localize and
 * isolate failures (see TokenException, JavaException).
 * 
 * @author dkitchin
 */
@SuppressWarnings("serial") // We don't care about serialization compatibility of Orc Exceptions
public abstract class OrcException extends Exception {
	Position position;

	/**
     * Constructs a new OrcException with the specified detail message.  The
     * cause is not initialized, and may subsequently be initialized by
     * a call to {@link #initCause}.
     *
     * @param   message   the detail message. The detail message is saved for
     *          later retrieval by the {@link #getMessage()} method.
	 */
	public OrcException(final String message) {
		super(message);
	}

	/**
     * Constructs a new OrcException with the specified detail message and
     * cause.  <p>Note that the detail message associated with
     * <code>cause</code> is <i>not</i> automatically incorporated in
     * this throwable's detail message.
     *
     * @param  message the detail message (which is saved for later retrieval
     *         by the {@link #getMessage()} method).
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A <tt>null</tt> value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
	 */
	public OrcException(final String message, final Throwable cause) {
		super(message, cause);
	}

	/**
     * Constructs a new OrcException with the specified cause and a detail
     * message of <tt>(cause==null ? null : cause.toString())</tt> (which
     * typically contains the class and detail message of <tt>cause</tt>).
     *
     * @param  cause the cause (which is saved for later retrieval by the
     *         {@link #getCause()} method).  (A <tt>null</tt> value is
     *         permitted, and indicates that the cause is nonexistent or
     *         unknown.)
	 */
	public OrcException(final Throwable cause) {
		super(cause);
	}

	/**
	 * @return "position: ClassName: detailMessage (newline) position.longString"
	 */
	public String getMessageAndPositon() {
		if (position != null && !(position instanceof NoPosition$)) {
			return position.toString() + ": " + getClass().getName() + ": " + getLocalizedMessage() + "\n" + position.longString();
		} else {
			return getLocalizedMessage();
		}
	}

	/**
	 * This returns a string with, as applicable and available, position, 
	 * exception class name, detailed message, the line of source code with
	 * a caret, Orc stack trace, Java stack trace, etc.
	 * 
	 * Various subclasses change the format as appropriate.
	 * 
	 * @return String, ending with newline
	 */
	public String getMessageAndDiagnostics() {
		return getMessageAndPositon()  + "\n";
	}

	/**
	 * @param e Throwable to retrieve stack trace from
	 * @return The stack trace, as would be printed, without the leading line "ClasName: detailMessage"
	 */
	public String getJavaStacktraceAsString(final Throwable e) {
		final StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		final StringBuffer traceBuf = sw.getBuffer();
		traceBuf.delete(0, traceBuf.indexOf("\n\tat "));
		return traceBuf.toString();
	}

	public Position getPosition() {
		return position;
	}

	public void resetPosition(final Position newpos) {
		position = newpos;
	}

	public OrcException setPosition(final Position newpos) {
		if (position == null || position instanceof NoPosition$) {
			position = newpos;
		}
		return this;
	}
}
