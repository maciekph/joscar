/*
 *  Copyright (c) 2004, The Joust Project
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
 *  File created by keith @ Jan 17, 2004
 *
 */

package net.kano.joustsim.oscar.oscar.service.icbm;

import net.kano.joscar.CopyOnWriteArrayList;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.snac.SnacPacketEvent;
import net.kano.joscar.snaccmd.FullUserInfo;
import net.kano.joscar.snaccmd.WarningLevel;
import net.kano.joscar.snaccmd.conn.SnacFamilyInfo;
import net.kano.joscar.snaccmd.icbm.IcbmCommand;
import net.kano.joscar.snaccmd.icbm.InstantMessage;
import net.kano.joscar.snaccmd.icbm.MissedMessagesCmd;
import net.kano.joscar.snaccmd.icbm.MissedMsgInfo;
import net.kano.joscar.snaccmd.icbm.ParamInfo;
import net.kano.joscar.snaccmd.icbm.ParamInfoCmd;
import net.kano.joscar.snaccmd.icbm.ParamInfoRequest;
import net.kano.joscar.snaccmd.icbm.RecvImIcbm;
import net.kano.joscar.snaccmd.icbm.RecvTypingNotification;
import net.kano.joscar.snaccmd.icbm.SendImIcbm;
import net.kano.joscar.snaccmd.icbm.SetParamInfoCmd;
import net.kano.joscar.snaccmd.icbm.SendTypingNotification;
import net.kano.joustsim.Screenname;
import net.kano.joustsim.oscar.AimConnection;
import net.kano.joustsim.oscar.BuddyInfo;
import net.kano.joustsim.oscar.BuddyInfoManager;
import net.kano.joustsim.oscar.oscar.OscarConnection;
import net.kano.joustsim.oscar.oscar.service.Service;
import net.kano.joustsim.trust.BuddyCertificateInfo;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IcbmService extends Service {
    private ParamInfo paramInfo = null;
    private SecureAimEncoder encoder = null;

    private CopyOnWriteArrayList<IcbmListener> listeners = new CopyOnWriteArrayList<IcbmListener>();
    private final BuddyInfoManager buddyInfoManager;

    public IcbmService(AimConnection aimConnection,
            OscarConnection oscarConnection) {
        super(aimConnection, oscarConnection, IcbmCommand.FAMILY_ICBM);
        buddyInfoManager = getAimConnection().getBuddyInfoManager();
    }

    public void addIcbmListener(IcbmListener l) {
        listeners.addIfAbsent(l);
    }

    public void removeIcbmListener(IcbmListener l) {
        listeners.remove(l);
    }

    public SnacFamilyInfo getSnacFamilyInfo() {
        return IcbmCommand.FAMILY_INFO;
    }

    public void connected() {
        sendSnac(new ParamInfoRequest());
    }

    public void handleSnacPacket(SnacPacketEvent snacPacketEvent) {
        SnacCommand snac = snacPacketEvent.getSnacCommand();

        if (snac instanceof ParamInfoCmd) {
            ParamInfoCmd pic = (ParamInfoCmd) snac;

            handleParamInfo(pic);

        } else if (snac instanceof RecvImIcbm) {
            RecvImIcbm icbm = (RecvImIcbm) snac;
            handleImIcbm(icbm);

        } else if (snac instanceof MissedMessagesCmd) {
            MissedMessagesCmd mc = (MissedMessagesCmd) snac;
            handleMissedMessages(mc);

        } else if (snac instanceof RecvTypingNotification) {
            RecvTypingNotification typnot = (RecvTypingNotification) snac;
            handleTypingNotification(typnot);
        }
    }

    private void handleParamInfo(ParamInfoCmd pic) {
        // we need to change from the default parameter infos to something
        // cooler, so we do it here
        ParamInfo pi = pic.getParamInfo();
        long newflags = pi.getFlags()
                | ParamInfo.FLAG_CHANMSGS_ALLOWED
                | ParamInfo.FLAG_MISSEDCALLS_ALLOWED
                | ParamInfo.FLAG_TYPING_NOTIFICATION;

        ParamInfo newparams = new ParamInfo(newflags, 8000,
                WarningLevel.getInstanceFromX10(999),
                WarningLevel.getInstanceFromX10(999), 0);
        this.paramInfo = newparams;

        sendSnac(new SetParamInfoCmd(newparams));

        setReady();
    }

    private void handleMissedMessages(MissedMessagesCmd mc) {
        List<MissedMsgInfo> msgs = mc.getMissedMsgInfos();
        for (MissedMsgInfo msg : msgs) {
            Screenname sn = new Screenname(msg.getUserInfo().getScreenname());
            ImConversation conv = getImConversation(sn);

            conv.handleMissedMsg(MissedImInfo.getInstance(getScreenname(),
                    msg));
        }
    }

    private void handleImIcbm(RecvImIcbm icbm) {
        FullUserInfo senderInfo = icbm.getSenderInfo();
        if (senderInfo == null) return;
        Screenname sender = new Screenname(senderInfo.getScreenname());

        InstantMessage message = icbm.getMessage();
        if (message == null) return;

        if (message.isEncrypted()) {
            SecureAimConversation conv = getSecureAimConversation(sender);

            EncryptedAimMessage msg = EncryptedAimMessage.getInstance(icbm);
            if (msg == null) return;

            BuddyInfo info = buddyInfoManager.getBuddyInfo(sender);
            BuddyCertificateInfo certInfo = info.getCertificateInfo();

            EncryptedAimMessageInfo minfo = EncryptedAimMessageInfo.getInstance(
                    getScreenname(), icbm, certInfo, new Date());
            if (minfo == null) return;

            conv.handleIncomingEvent(minfo);

        } else {
            ImConversation conv = getImConversation(sender);

            ImMessageInfo msg = ImMessageInfo.getInstance(getScreenname(),
                    icbm, new Date());

            conv.handleIncomingEvent(msg);
        }
    }

    private void handleTypingNotification(RecvTypingNotification typnot) {
        Screenname sender = new Screenname(typnot.getScreenname());
        Conversation conv = getImConversation(sender);
        conv.handleIncomingEvent(new TypingInfo(sender, getScreenname(),
                new Date(), typnot.getTypingState()));
    }

    private Map<Screenname,SecureAimConversation> secureAimConvs
            = new HashMap<Screenname, SecureAimConversation>();

    public SecureAimConversation getSecureAimConversation(Screenname sn) {
        boolean isnew = false;
        SecureAimConversation conv;
        synchronized(this) {
            conv = secureAimConvs.get(sn);
            if (conv == null) {
                isnew = true;
                conv = new SecureAimConversation(getAimConnection(), sn);
                secureAimConvs.put(sn, conv);
            }
        }
        // we need to initialize this outside of the lock to prevent deadlocks
        if (isnew) initConversation(conv);

        return conv;
    }

    private Map<Screenname,ImConversation> imconvs = new HashMap<Screenname, ImConversation>();

    public synchronized ImConversation getImConversation(Screenname sn) {
        boolean isnew = false;
        ImConversation conv;
        synchronized(this) {
            conv = imconvs.get(sn);
            if (conv == null) {
                isnew = true;
                conv = new ImConversation(getAimConnection(), sn);
                imconvs.put(sn, conv);
            }
        }
        // we need to initialize this outside of the lock to prevent deadlocks
        if (isnew) initConversation(conv);

        return conv;
    }

    private void initConversation(Conversation conv) {
        assert !Thread.holdsLock(this);

        conv.initialize();

        for (IcbmListener listener : listeners) {
            listener.newConversation(this, conv);
        }
    }

    void sendIM(Screenname buddy, String body, boolean autoresponse) {
        sendIM(buddy, new InstantMessage(body), autoresponse);
    }

    void sendIM(Screenname buddy, InstantMessage im,
            boolean autoresponse) {
        sendSnac(new SendImIcbm(buddy.getFormatted(), im, autoresponse, 0,
                false, null, null, true));
    }

    void sendTypingStatus(Screenname buddy, int typingState) {
        sendSnac(new SendTypingNotification(buddy.getFormatted(), typingState));
    }
}
