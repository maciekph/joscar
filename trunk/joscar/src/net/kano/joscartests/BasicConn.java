/*
 *  Copyright (c) 2002, The Joust Project
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
 *  File created by keith @ Mar 26, 2003
 *
 */

package net.kano.joscartests;

import net.kano.joscar.ByteBlock;
import net.kano.joscar.FileWritable;
import net.kano.joscar.flap.FlapCommand;
import net.kano.joscar.flap.FlapPacketEvent;
import net.kano.joscar.flapcmd.LoginFlapCmd;
import net.kano.joscar.flapcmd.SnacPacket;
import net.kano.joscar.rv.*;
import net.kano.joscar.rvcmd.DefaultRvCommandFactory;
import net.kano.joscar.rvcmd.RvConnectionInfo;
import net.kano.joscar.rvcmd.addins.AddinsReqRvCmd;
import net.kano.joscar.rvcmd.getfile.GetFileReqRvCmd;
import net.kano.joscar.rvcmd.sendbl.SendBuddyListRvCmd;
import net.kano.joscar.rvcmd.icon.SendBuddyIconRvCmd;
import net.kano.joscar.rvcmd.directim.DirectIMReqRvCmd;
import net.kano.joscar.rvcmd.sendfile.FileSendReqRvCmd;
import net.kano.joscar.rvcmd.trillcrypt.TrillianCryptReqRvCmd;
import net.kano.joscar.rvcmd.trillcrypt.AbstractTrillianCryptRvCmd;
import net.kano.joscar.snac.*;
import net.kano.joscar.snaccmd.*;
import net.kano.joscar.snaccmd.buddy.BuddyOfflineCmd;
import net.kano.joscar.snaccmd.buddy.BuddyStatusCmd;
import net.kano.joscar.snaccmd.conn.*;
import net.kano.joscar.snaccmd.icbm.*;

import java.io.*;
import java.net.InetAddress;
import java.text.DateFormat;
import java.util.*;

public abstract class BasicConn extends AbstractFlapConn {
    protected final ByteBlock cookie;
    protected boolean sentClientReady = false;

    protected int[] snacFamilies = null;
    protected SnacFamilyInfo[] snacFamilyInfos;
    protected RateClassInfo[] rateClasses = null;
    protected RateDataQueueMgr rateMgr = new RateDataQueueMgr();
    protected RvProcessor rvProcessor = new RvProcessor(snacProcessor);
    protected RvProcessorListener rvListener = new RvProcessorListener() {
        public void handleNewSession(NewRvSessionEvent event) {
            System.out.println("new RV session: " + event.getSession());

            event.getSession().addListener(rvSessionListener);
        }
    };
    protected Map trillianEncSessions = new HashMap();

    protected RvSessionListener rvSessionListener = new RvSessionListener() {
        public void handleRv(RecvRvEvent event) {
            RvCommand cmd = event.getRvCommand();

            RvSession session = event.getRvSession();
            RecvRvIcbm icbm = (RecvRvIcbm) event.getSnacCommand();
            System.out.println("got rendezvous on session <" + session + ">");
            System.out.println("- command: " + cmd);

            if (cmd instanceof FileSendReqRvCmd) {
                FileSendReqRvCmd rv = (FileSendReqRvCmd) cmd;

                RvConnectionInfo connInfo = rv.getConnInfo();
                InetAddress ip = connInfo.getExternalIP();
                int port = connInfo.getPort();

                if (ip != null && port != -1) {
                    System.out.println("starting ft thread..");
                    long cookie = icbm.getIcbmMessageId();
                    new RecvFileThread(ip, port, session, cookie).start();
                }

            } else if (cmd instanceof AbstractTrillianCryptRvCmd) {
                String key = OscarTools.normalize(session.getScreenname());
                TrillianEncSession encSession = (TrillianEncSession)
                        trillianEncSessions.get(key);
                if (encSession == null) {
                    encSession = new TrillianEncSession(session);
                    trillianEncSessions.put(key, encSession);
                }
                encSession.handleRv(event);
            } else if (cmd instanceof DirectIMReqRvCmd) {
                new DirectIMSession(tester.getScreenname(), session, event);
            } else if (cmd instanceof SendBuddyIconRvCmd) {
                SendBuddyIconRvCmd iconCmd = (SendBuddyIconRvCmd) cmd;
                ByteBlock iconData = iconCmd.getIconData();
                if (iconData != null) {
                    try {
                        File dir = new File("buddy-icons");
                        if (!dir.isDirectory()) dir.mkdir();

                        File file = new File(dir, session.getScreenname()
                                + ".icon");
                        System.out.println("writing icon to " + file + "!!");

                        FileOutputStream out = new FileOutputStream(file);
                        iconData.write(out);
                        out.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else if (cmd instanceof SendBuddyListRvCmd) {
                session.sendResponse(RvResponse.CODE_NOT_ACCEPTING);
            } else if (cmd instanceof GetFileReqRvCmd) {
                if (((GetFileReqRvCmd) cmd).getCode() != -1) {
                    new HostGetFileThread(session, event).start();
                }
            } else if (cmd instanceof AddinsReqRvCmd) {
                session.sendRv(cmd);
            }
        }

        public void handleSnacResponse(RvSnacResponseEvent event) {
            System.out.println("got SNAC response for <"
                    + event.getRvSession() + ">: "
                    + event.getSnacCommand());
        }
    };

    { // initialization
        snacProcessor.setSnacQueueManager(rateMgr);
        rvProcessor.registerRvCmdFactory(new DefaultRvCommandFactory());
        rvProcessor.registerRvCmdFactory(new GenericRvCommandFactory());
        rvProcessor.addListener(rvListener);
    }

    public BasicConn(JoscarTester tester, ByteBlock cookie) {
        super(tester);
        this.cookie = cookie;
    }

    public BasicConn(String host, int port, JoscarTester tester,
            ByteBlock cookie) {
        super(host, port, tester);
        this.cookie = cookie;
    }

    public BasicConn(InetAddress ip, int port, JoscarTester tester,
            ByteBlock cookie) {
        super(ip, port, tester);
        this.cookie = cookie;
    }

    protected DateFormat dateFormat
            = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                    DateFormat.SHORT);

    protected PrintWriter imLogger = null;
    protected ImTestFrame frame;

    public void setLogIms(ImTestFrame frame) {
        this.frame = frame;
        if (frame != null) {
            try {
                String file = System.getProperty("user.home")
                        + System.getProperty("file.separator") + "ims.log";
                System.out.println("writing to " + file);
                imLogger = new PrintWriter(new FileOutputStream(
                        file, true), true);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            imLogger.close();
        }
    }

    protected void handleFlapPacket(FlapPacketEvent e) {
        FlapCommand cmd = e.getFlapCommand();

        if (cmd instanceof LoginFlapCmd) {
            getFlapProcessor().send(new LoginFlapCmd(cookie));
        } else {
            System.out.println("got FLAP command on channel 0x"
                    + Integer.toHexString(e.getFlapPacket().getChannel())
                    + ": " + cmd);
        }
    }

    protected void handleSnacPacket(SnacPacketEvent e) {
        SnacPacket packet = e.getSnacPacket();
        System.out.println("got snac packet type "
                + Integer.toHexString(packet.getFamily()) + "/"
                + Integer.toHexString(packet.getCommand()) + ": "
                + e.getSnacCommand());

        SnacCommand cmd = e.getSnacCommand();
        if (cmd instanceof ServerReadyCmd) {
            ServerReadyCmd src = (ServerReadyCmd) cmd;

            setSnacFamilies(src.getSnacFamilies());

            SnacFamilyInfo[] familyInfos = SnacFamilyInfoFactory
                    .getDefaultFamilyInfos(src.getSnacFamilies());

            setSnacFamilyInfos(familyInfos);

            tester.registerSnacFamilies(this);

            request(new ClientVersionsCmd(familyInfos));
            request(new RateInfoRequest());

        } else if (cmd instanceof RecvImIcbm) {
            RecvImIcbm icbm = (RecvImIcbm) cmd;

            String sn = icbm.getSenderInfo().getScreenname();
            System.out.println("*" + sn + "* "
                    + OscarTools.stripHtml(icbm.getMessage()));

            OldIconHashInfo iconInfo = icbm.getIconInfo();

            if (iconInfo != null) {
                System.out.println("(" + sn
                        + " has a buddy icon: " + iconInfo + ")");
            }

            String str = dateFormat.format(new Date()) + " IM from "
                    + sn + ": " + icbm.getMessage();
            if (imLogger != null) {
                imLogger.println(str);
            }
            if (frame != null) {
                frame.echo("");
                frame.echo(str);
            }

            if (icbm.senderWantsIcon()) {
                System.out.println(sn + " wants our icon.. sending.");
                RvSession sess = rvProcessor.createRvSession(sn);
                sess.sendRv(new SendBuddyIconRvCmd(tester.oldIconInfo,
                        new FileWritable(tester.iconFile)));
            }

        } else if (cmd instanceof WarningNotification) {
            WarningNotification wn = (WarningNotification) cmd;
            MiniUserInfo warner = wn.getWarner();
            if (warner == null) {
                System.out.println("*** You were warned anonymously to "
                        + wn.getNewLevel() + "%");
            } else {
                System.out.println("*** " + warner.getScreenname()
                        + " warned you up to " + wn.getNewLevel() + "%");
            }
        } else if (cmd instanceof BuddyStatusCmd) {
            BuddyStatusCmd bsc = (BuddyStatusCmd) cmd;

            FullUserInfo info = bsc.getUserInfo();

            String sn = info.getScreenname();

            tester.setUserInfo(sn, info);

            ExtraIconInfo[] iconInfos = info.getIconInfos();

            if (false && iconInfos != null) {
                for (int i = 0; i < iconInfos.length; i++) {
                    IconHashInfo hashInfo = iconInfos[i].getIconHashInfo();

                    if (hashInfo.getCode() == IconHashInfo.CODE_ICON_PRESENT) {
//                        System.out.println(sn +
//                                " has an icon! requesting it.. (excode="
//                                + iconInfos[i].getExtraCode() + ")");

//                        request(new IconRequest(sn, iconInfos[i]));

                        break;
                    }
                }
            }

            if (info.getCapabilityBlocks() != null) {
                List known = Arrays.asList(new CapabilityBlock[] {
                    CapabilityBlock.BLOCK_CHAT,
                    CapabilityBlock.BLOCK_DIRECTIM,
                    CapabilityBlock.BLOCK_FILE_GET,
                    CapabilityBlock.BLOCK_FILE_SEND,
                    CapabilityBlock.BLOCK_GAMES,
                    CapabilityBlock.BLOCK_GAMES2,
                    CapabilityBlock.BLOCK_ICON,
                    CapabilityBlock.BLOCK_SENDBUDDYLIST,
                    CapabilityBlock.BLOCK_TRILLIANCRYPT,
                    CapabilityBlock.BLOCK_VOICE,
                    CapabilityBlock.BLOCK_ADDINS,
                });

                List caps = new ArrayList(Arrays.asList(
                        info.getCapabilityBlocks()));
                caps.removeAll(known);
                if (!caps.isEmpty()) {
                    System.out.println(sn + " has " + caps.size()
                            + " unknown caps:");
                    for (Iterator it = caps.iterator(); it.hasNext();) {
                        System.out.println("- " + it.next());
                    }
                }
            }

        } else if (cmd instanceof BuddyOfflineCmd) {
            BuddyOfflineCmd boc = (BuddyOfflineCmd) cmd;

            tester.setOffline(boc.getScreenname());
        } else if (cmd instanceof RateChange) {
            RateChange rc = (RateChange) cmd;

            System.out.println("rate change: current avg is "
                    + rc.getRateInfo().getCurrentAvg());

            rateMgr.setRateClass(rc.getRateInfo());
        }
    }

    protected void handleSnacResponse(SnacResponseEvent e) {
        SnacPacket packet = e.getSnacPacket();
        System.out.println("got snac response type "
                + Integer.toHexString(packet.getFamily()) + "/"
                + Integer.toHexString(packet.getCommand()) + ": "
                + e.getSnacCommand());

        SnacCommand cmd = e.getSnacCommand();

        if (cmd instanceof RateInfoCmd) {
            RateInfoCmd ric = (RateInfoCmd) cmd;

            rateClasses = ric.getRateClassInfos();

            rateMgr.setRateClasses(rateClasses);

            int[] classes = new int[rateClasses.length];
            for (int i = 0; i < rateClasses.length; i++) {
                classes[i] = rateClasses[i].getRateClass();
            }

            request(new RateAck(classes));
        }
    }

    public int[] getSnacFamilies() { return snacFamilies; }

    public RateClassInfo[] getRateClasses() {
        return rateClasses;
    }

    protected void setSnacFamilies(int[] families) {
        this.snacFamilies = (int[]) families.clone();
        Arrays.sort(snacFamilies);
    }

    protected void setSnacFamilyInfos(SnacFamilyInfo[] infos) {
        snacFamilyInfos = infos;
    }

    protected boolean supportsFamily(int family) {
        return Arrays.binarySearch(snacFamilies, family) >= 0;
    }

    protected void clientReady() {
        if (!sentClientReady) {
            sentClientReady = true;
            request(new ClientReadyCmd(snacFamilyInfos));
        }
    }

    protected SnacRequest dispatchRequest(SnacCommand cmd) {
        return dispatchRequest(cmd, null);
    }

    protected SnacRequest dispatchRequest(SnacCommand cmd,
            SnacRequestListener listener) {
        SnacRequest req = new SnacRequest(cmd, listener);
        dispatchRequest(req);
        return req;
    }

    protected void dispatchRequest(SnacRequest req) {
        tester.handleRequest(req);
    }

    protected SnacRequest request(SnacCommand cmd,
            SnacRequestListener listener) {
        SnacRequest req = new SnacRequest(cmd, listener);

        handleReq(req);

        return req;
    }

    private void handleReq(SnacRequest request) {
        int family = request.getCommand().getFamily();
        if (snacFamilies == null || supportsFamily(family)) {
            // this connection supports this snac, so we'll send it here
            sendRequest(request);
        } else {
            tester.handleRequest(request);
        }
    }

}