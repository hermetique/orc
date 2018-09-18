//
// Semaphore.java -- Java class Semaphore
// Project OrcScala
//
// Copyright (c) 2018 The University of Texas at Austin. All rights reserved.
//
// Use and redistribution of this file is governed by the license terms in
// the LICENSE file found in the project's top-level directory and also found at
// URL: http://orc.csres.utexas.edu/license.shtml .
//

package orc.lib.state;

import java.util.LinkedList;

import orc.values.sites.compatibility.CallContext;
import orc.MaterializedCallContext;
import orc.error.runtime.ArityMismatchException;
import orc.error.runtime.TokenException;
import orc.lib.state.types.SemaphoreType;
import orc.run.distrib.AbstractLocation;
import orc.run.distrib.ClusterLocations;
import orc.run.distrib.DOrcPlacementPolicy;
import orc.types.Type;
import orc.values.sites.TypedSite;
import orc.values.sites.compatibility.Args;
import orc.values.sites.compatibility.DotSite;
import orc.values.sites.compatibility.EvalSite;
import orc.values.sites.compatibility.SiteAdaptor;

/**
 * @author quark
 * @author dkitchin
 */
@SuppressWarnings("hiding")
public class Semaphore extends EvalSite implements TypedSite {

    @Override
    public Object evaluate(final Args args) throws TokenException {
        final int initialValue = args.intArg(0);

        if (args.size() != 1) {
            throw new ArityMismatchException(1, args.size());
        }

        if (initialValue >= 0) {
            return new SemaphoreInstance(initialValue);
        } else {
            throw new IllegalArgumentException("Semaphore requires a non-negative argument");
        }

    }

    @Override
    public Type orcType() {
        return SemaphoreType.getBuilder();
    }

    public static class SemaphoreInstance extends DotSite implements DOrcPlacementPolicy {

        protected final LinkedList<MaterializedCallContext> waiters = new LinkedList<MaterializedCallContext>();
        protected final LinkedList<MaterializedCallContext> snoopers = new LinkedList<MaterializedCallContext>();

        /* Invariant: n >= 0 */
        protected int n;

        /* Precondition: n >= 0 */
        public SemaphoreInstance(final int n) {
            this.n = n;
        }

        @Override
        protected void addMembers() {
            addMember("acquire", new SiteAdaptor() {
                @Override
                public void callSite(final Args args, final CallContext waiter) {
                    synchronized (SemaphoreInstance.this) {
                        if (0 == n) {
                            waiter.setQuiescent();
                            waiters.offer(waiter.materialize());
                            if (!snoopers.isEmpty()) {
                                LinkedList<MaterializedCallContext> oldSnoopers = (LinkedList<MaterializedCallContext>) snoopers.clone();
                                snoopers.clear();
                                for (final MaterializedCallContext snooper : oldSnoopers) {
                                    snooper.publish(signal());
                                }
                            }
                        } else {
                            --n;
                            waiter.publish(signal());
                        }
                    }
                }
            });
            addMember("acquireD", new SiteAdaptor() {
                @Override
                public void callSite(final Args args, final CallContext waiter) {
                    synchronized (SemaphoreInstance.this) {
                        if (0 == n) {
                            waiter.halt();
                        } else {
                            --n;
                            waiter.publish(signal());
                        }
                    }
                }

                @Override
                public boolean nonBlocking() {
                    return true;
                }
            });
            addMember("release", new SiteAdaptor() {
                @Override
                public void callSite(final Args args, final CallContext sender) throws TokenException {
                    synchronized (SemaphoreInstance.this) {
                        if (waiters.isEmpty()) {
                            ++n;
                        } else {
                            final MaterializedCallContext waiter = waiters.poll();
                            waiter.publish(signal());
                        }
                        sender.publish(signal());
                    }
                }

                @Override
                public boolean nonBlocking() {
                    return true;
                }
            });
            addMember("snoop", new SiteAdaptor() {
                @Override
                public void callSite(final Args args, final CallContext snooper) throws TokenException {
                    synchronized (SemaphoreInstance.this) {
                        if (waiters.isEmpty()) {
                            snooper.setQuiescent();
                            snoopers.offer(snooper.materialize());
                        } else {
                            snooper.publish(signal());
                        }
                    }
                }
            });
            addMember("snoopD", new SiteAdaptor() {
                @Override
                public void callSite(final Args args, final CallContext caller) throws TokenException {
                    synchronized (SemaphoreInstance.this) {
                        if (waiters.isEmpty()) {
                            caller.halt();
                        } else {
                            caller.publish(signal());
                        }
                    }
                }

                @Override
                public boolean nonBlocking() {
                    return true;
                }
            });
        }

        @Override
        public <L extends AbstractLocation> scala.collection.immutable.Set<L> permittedLocations(final ClusterLocations<L> locations) {
            return locations.hereSet();
        }

    }

    @Override
    public boolean nonBlocking() {
        return true;
    }

    @Override
    public int maxPublications() {
        return 1;
    }

    @Override
    public boolean effectFree() {
        return true;
    }
}
