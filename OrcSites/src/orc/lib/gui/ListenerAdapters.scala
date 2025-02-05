//
// ListenerAdapters.scala -- Scala Listener Adapter classes
// Project OrcSites
//
// Created by amp on Feb 13, 2015.
//
// Copyright (c) 2017 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//
package orc.lib.gui

import java.awt.event.{ ActionEvent, ActionListener, MouseEvent, MouseListener, WindowFocusListener, WindowListener, WindowStateListener }

import orc.values.sites.compatibility.CallContext
import orc.error.runtime.ArgumentTypeMismatchException
import orc.run.core.CallController
import orc.run.extensions.SupportForCallsIntoOrc
import orc.values.{ Field }
import orc.values.sites.compatibility.{ Site1, HasMembers }

abstract class ListenerAdapter {
  val deligate: HasMembers
  val execution: SupportForCallsIntoOrc

  def call(f: Field, arguments: List[AnyRef]): Unit = {
    if (deligate hasMember f)
      execution.callOrcMethod(deligate, f, arguments)
  }
  def call(f: Field, arguments: AnyRef*): Unit = call(f, arguments.toList)
  def call(f: String, arguments: AnyRef*): Unit = call(Field(f), arguments.toList)
}

// TODO: Make this typed once we have object types.
abstract class ListenerAdapterSite extends Site1 {
  def call(arg: AnyRef, callContext: CallContext) = {
    val execution = callContext.underlying.asInstanceOf[CallController].caller.execution match {
      case r: SupportForCallsIntoOrc => r
      case _ => throw new AssertionError("CallableToRunnable only works with a runtime that includes SupportForCallsIntoOrc.")
    }
    val del = arg match {
      case d: HasMembers => d
      case a => throw new ArgumentTypeMismatchException(0, "<has members>", if (a != null) a.getClass().toString() else "null")
    }
    callContext.publish(buildAdapter(execution, del))
  }

  def buildAdapter(execution: SupportForCallsIntoOrc, del: HasMembers): AnyRef
}

class ActionListenerAdapter(val deligate: HasMembers, val execution: SupportForCallsIntoOrc) extends ListenerAdapter with ActionListener {
  def actionPerformed(e: ActionEvent) = {
    call(Field("actionPerformed"), List(e))
  }
}
object ActionListenerAdapter extends ListenerAdapterSite {
  def buildAdapter(execution: SupportForCallsIntoOrc, del: HasMembers): AnyRef = {
    new ActionListenerAdapter(del, execution)
  }
}

class WindowListenerAdapter(val deligate: HasMembers, val execution: SupportForCallsIntoOrc)
  extends ListenerAdapter with WindowListener with WindowFocusListener with WindowStateListener {
  // Members declared in java.awt.event.WindowFocusListener
  def windowGainedFocus(e: java.awt.event.WindowEvent): Unit = call(Field("windowGainedFocus"), List(e))
  def windowLostFocus(e: java.awt.event.WindowEvent): Unit = call(Field("windowLostFocus"), List(e))
  // Members declared in java.awt.event.WindowListener
  def windowActivated(e: java.awt.event.WindowEvent): Unit = call(Field("windowActivated"), List(e))
  def windowClosed(e: java.awt.event.WindowEvent): Unit = call(Field("windowClosed"), List(e))
  def windowClosing(e: java.awt.event.WindowEvent): Unit = call(Field("windowClosing"), List(e))
  def windowDeactivated(e: java.awt.event.WindowEvent): Unit = call(Field("windowDeactivated"), List(e))
  def windowDeiconified(e: java.awt.event.WindowEvent): Unit = call(Field("windowDeiconified"), List(e))
  def windowIconified(e: java.awt.event.WindowEvent): Unit = call(Field("windowIconified"), List(e))
  def windowOpened(e: java.awt.event.WindowEvent): Unit = call(Field("windowOpened"), List(e))
  // Members declared in java.awt.event.WindowStateListener
  def windowStateChanged(e: java.awt.event.WindowEvent): Unit = call(Field("windowStateChanged"), List(e))
}
object WindowListenerAdapter extends ListenerAdapterSite {
  def buildAdapter(runtime: SupportForCallsIntoOrc, del: HasMembers): AnyRef = {
    new WindowListenerAdapter(del, runtime)
  }
}

class MouseListenerAdapter(val deligate: HasMembers, val execution: SupportForCallsIntoOrc) extends ListenerAdapter with MouseListener {
  def mouseClicked(e: MouseEvent): Unit = call("mouseClicked", e)
  def mousePressed(e: MouseEvent): Unit = call("mousePressed", e)
  def mouseReleased(e: MouseEvent): Unit = call("mouseReleased", e)
  def mouseEntered(e: MouseEvent): Unit = call("mouseEntered", e)
  def mouseExited(e: MouseEvent): Unit = call("mouseExited", e)
}
object MouseListenerAdapter extends ListenerAdapterSite {
  def buildAdapter(runtime: SupportForCallsIntoOrc, del: HasMembers): AnyRef = {
    new MouseListenerAdapter(del, runtime)
  }
}
