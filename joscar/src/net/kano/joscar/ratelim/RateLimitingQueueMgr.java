/*
 *  Copyright (c) 2003, The Joust Project
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without 
 *  modification, are permitted provided that the following conditions 
 *  are met:
 *
 *  - Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer. 
 *  - Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution. 
 *  - Neither the name of the Joust Project nor the names of its 
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission. 
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 *  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 *  COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; 
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER 
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT 
 *  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN 
 *  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 *  POSSIBILITY OF SUCH DAMAGE.
 *
 *  File created by keith @ May 25, 2003
 *
 */

package net.kano.joscar.ratelim;

import net.kano.joscar.DefensiveTools;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.snac.*;
import net.kano.joscar.snaccmd.conn.RateClassInfo;
import net.kano.joscar.snaccmd.conn.RateInfoCmd;
import net.kano.joscar.snaccmd.conn.RateChange;

import java.util.*;

/**
 * A SNAC queue manager which utilizes the "official" rate limiting algorithm to
 * avoid ever becoming rate-limited.
 */
public class RateLimitingQueueMgr extends AbstractSnacQueueMgr {
    public static final int ERRORMARGIN_DEFAULT = 100;

    private int errorMargin = ERRORMARGIN_DEFAULT;
    private boolean avoidLimiting = true;

    private Map conns = new IdentityHashMap();

    private QueueRunner runner = null;

    private SnacPacketListener packetListener = new SnacPacketListener() {
        public void handleSnacPacket(SnacPacketEvent e) {
            SnacCommand cmd = e.getSnacCommand();

            if (cmd instanceof RateChange) {
                RateChange rc = (RateChange) cmd;

                RateClassInfo rateInfo = rc.getRateInfo();
                if (rateInfo != null) {
                    int code = rc.getChangeCode();
                    updateRateClass(e.getSnacProcessor(), code, rateInfo);
                }
            }
        }
    };
    private SnacResponseListener responseListener = new SnacResponseListener() {
        public void handleResponse(SnacResponseEvent e) {
            SnacCommand cmd = e.getSnacCommand();

            if (cmd instanceof RateInfoCmd) {
                RateInfoCmd ric = (RateInfoCmd) cmd;

                setRateClasses(e.getSnacProcessor(), ric.getRateClassInfos());
            }
        }
    };

    final QueueRunner getRunner() { return runner; }

    public final void attach(SnacProcessor processor) {
        synchronized(conns) {
            if (conns.containsKey(processor)) {
                throw new IllegalArgumentException("already attached to " +
                        "processor " + processor);
            }

            conns.put(processor, new ConnectionQueueMgr(this, processor));

            processor.setSnacQueueManager(this);
            processor.addPacketListener(packetListener);
            processor.addGlobalResponseListener(responseListener);
        }
    }

    private ConnectionQueueMgr getQueueMgr(SnacProcessor processor) {
        ConnectionQueueMgr rcs;
        synchronized(conns) {
            rcs = (ConnectionQueueMgr) conns.get(processor);
        }
        if (rcs == null) {
            throw new IllegalArgumentException("this rate manager is not " +
                    "currently attached to processor " + processor);
        }
        return rcs;
    }

    public final void detach(SnacProcessor processor) {
        synchronized(conns) {
            if (conns.remove(processor) == null) {
                throw new IllegalArgumentException("not attached to processor "
                        + processor);
            }
            processor.removePacketListener(packetListener);
            processor.removeGlobalResponseListener(responseListener);
            synchronized(processor) {
                if (processor.getSnacQueueManager() == this) {
                    processor.setSnacQueueManager(null);
                }
            }
        }
    }

    public void setRateClasses(SnacProcessor processor,
            RateClassInfo[] rateInfos) {
        DefensiveTools.checkNull(processor, "processor");
        DefensiveTools.checkNull(rateInfos, "rateInfos");

        rateInfos = (RateClassInfo[]) rateInfos.clone();

        DefensiveTools.checkNullElements(rateInfos, "rateInfos");

        ConnectionQueueMgr mgr = getQueueMgr(processor);

        for (int i = 0; i < rateInfos.length; i++) {
            RateClassInfo rateInfo = rateInfos[i];

            mgr.setRateClass(rateInfo);
        }
    }

    public void updateRateClass(SnacProcessor processor, int changeCode,
            RateClassInfo rateInfo) {
        DefensiveTools.checkNull(processor, "processor");
        DefensiveTools.checkNull(rateInfo, "rateInfo");

        getQueueMgr(processor).updateRateClass(changeCode, rateInfo);
    }

    public synchronized final boolean isAvoidingLimiting() {
        return avoidLimiting;
    }

    public synchronized final void setAvoidingLimiting(boolean avoidLimiting) {
        if (avoidLimiting = this.avoidLimiting) return;

        this.avoidLimiting = avoidLimiting;

        if (!avoidLimiting) runner.update();
    }

    public synchronized final void setErrorMargin(int errorMargin) {
        DefensiveTools.checkRange(errorMargin, "errorMargin", 0);

        this.errorMargin = errorMargin;
    }

    public final synchronized int getErrorMargin() { return errorMargin; }


    public void queueSnac(SnacProcessor processor, SnacRequest request) {
        DefensiveTools.checkNull(request, "request");

        getQueueMgr(processor).queueSnac(request);
    }

    public void clearQueue(SnacProcessor processor) {
        getQueueMgr(processor).clearQueue();
    }

    public void pause(SnacProcessor processor) {
        getQueueMgr(processor).pause();
    }

    public void unpause(SnacProcessor processor) {
        getQueueMgr(processor).unpause();
    }

    protected void sendSnac(SnacProcessor processor, SnacRequest request) {
        // sigh
        super.sendSnac(processor, request);
    }

    class QueueRunner implements Runnable {
        private boolean started = false;

        private final Object lock = new Object();

        private boolean updated = false;
        private Set queues = new HashSet();

        public synchronized void start() {
            if (started) {
                throw new IllegalStateException("already started queue " +
                        "manager");
            }
            started = true;

            // I think this is correct
            updated = true;

            Thread thread = new Thread(this, "SNAC queue manager");
            thread.start();
        }

        public void run() {
            long minWait = 0;
            for (;;) {
                RateQueue[] queueArray;
                synchronized(lock) {
                    if (!updated) {
                        // if we haven't been updated, we need to wait until a
                        // call to update() or until we need to send the next
                        // command (this time is specified in minWait, if non-
                        // zero)
                        try {
                            if (minWait == 0) lock.wait();
                            else lock.wait(minWait);
                        } catch (InterruptedException ignored) { }
                    }

                    // cache the list of queues so we don't need to lock while
                    // iterating
                    queueArray = (RateQueue[])
                            queues.toArray(new RateQueue[queues.size()]);

                    // and set this flag back to off while we're in the lock
                    updated = false;
                }

                // now we go through the queues to send any "ready" commands and
                // figure out when the next command should be sent (so we can
                // wait that long in the next iteration of the outer loop)
                minWait = 0;
                for (int i = 0; i < queueArray.length; i++) {
                    RateQueue queue = queueArray[i];

                    boolean finished;
                    long wait = 0;
                    List reqs = null;
                    synchronized(queue) {
                        // if the queue is paused or there aren't any requests,
                        // we can skip it
                        if (queue.getParentMgr().isPaused()
                                || !queue.hasRequests()) {
                            continue;
                        }

                        // if there are one or more commands that can be sent
                        // right now, dequeue them
                        if (isReady(queue)) reqs = dequeueAll(queue);

                        // see whether the queue needs to be waited upon (if it
                        // doesn't have any queued commands, there's nothing to
                        // wait for -- we'll be notified with a call to
                        // update() if any are added)
                        finished = !queue.hasRequests();

                        // and, if necessary, compute how long we need to wait
                        // for this queue (the time until the next command can
                        // be sent)
                        if (!finished) wait = getWaitTime(queue);
                    }

                    // if there are any commands that were dequeued above, we
                    // send them now, outside of the lock on the queue
                    if (reqs != null && !reqs.isEmpty()) { 
                        sendRequests(queue, reqs);
                    }

                    // and if there's nothing more to wait for, move on to the
                    // next queue
                    if (finished) continue;

                    // we make sure wait isn't zero, because that would cause us
                    // to wait forever, which we don't want to do unless we
                    // explicitly decide to do so.
                    if (wait < 1) wait = 1;

                    // now change the minimum waiting time if necessary
                    if (minWait == 0 || wait < minWait)  minWait = wait;
                }
            }
        }

        private long getWaitTime(RateQueue queue) {
            return queue.getOptimalWaitTime();
        }

        private boolean isReady(RateQueue queue) {
            return getWaitTime(queue) <= 0;
        }

        private List dequeueAll(RateQueue queue) {
            List reqs = null;
            synchronized(queue) {
                for (;;) {
                    if (!queue.hasRequests() || !isReady(queue)) break;

                    if (reqs == null) reqs = new ArrayList(5);
                    reqs.add(queue.dequeue());
                }
            }

            return reqs;
        }

        private void sendRequests(RateQueue queue, List reqs) {
            SnacProcessor processor = queue.getParentMgr().getSnacProcessor();

            for (Iterator it = reqs.iterator(); it.hasNext();) {
                SnacRequest req = (SnacRequest) it.next();

                sendSnac(processor, req);
            }
        }

        public void update(RateQueue updated) {
            if (isAvoidingLimiting()) {
                forceUpdate();
            } else {
                sendRequests(updated, dequeueAll(updated));
            }
        }

        public void update() {
            if (isAvoidingLimiting()) {
                forceUpdate();
            } else {
                flushAllQueues();
            }
        }

        private void forceUpdate() {
            synchronized(lock) {
                updated = true;
                lock.notifyAll();
            }
        }

        private void flushAllQueues() {
            // we make a copy of the queue list since we'd need to make a copy
            // of something anyway (SNAC requests can't be sent inside a lock)
            // and there's no reason to not just copy the queue list itself
            List queues;
            synchronized(this.queues) {
                 queues = new ArrayList(this.queues);
            }

            for (Iterator it = queues.iterator(); it.hasNext();) {
                RateQueue queue = (RateQueue) it.next();

                sendRequests(queue, dequeueAll(queue));
            }
        }

        public void addQueue(RateQueue queue) {
            DefensiveTools.checkNull(queue, "queue");

            synchronized(lock) {
                queues.add(queue);
                update(queue);
            }
        }
    }
}
