/*
 *  Copyright (c) 2002-2003, The Joust Project
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
 *  File created by keith @ Feb 22, 2003
 *
 */

package net.kano.joscar.snaccmd.loc;

import net.kano.joscar.DefensiveTools;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.flapcmd.SnacPacket;
import net.kano.joscar.snac.CmdType;
import net.kano.joscar.snac.SnacCmdFactory;

import java.util.List;

/**
 * A SNAC command factory for the server-bound commands provided in this
 * package, appropriate for use in an AIM server.
 */
public class ServerLocCmdFactory implements SnacCmdFactory {
    /** The supported SNAC command types. */
    protected static final List<CmdType> SUPPORTED_TYPES = DefensiveTools.asUnmodifiableList(
        new CmdType(LocCommand.FAMILY_LOC, LocCommand.CMD_RIGHTS_REQ),
        new CmdType(LocCommand.FAMILY_LOC, LocCommand.CMD_SET_INFO),
        new CmdType(LocCommand.FAMILY_LOC, LocCommand.CMD_OLD_GET_INFO),
        new CmdType(LocCommand.FAMILY_LOC, LocCommand.CMD_GET_DIR),
        new CmdType(LocCommand.FAMILY_LOC, LocCommand.CMD_SET_INTERESTS),
        new CmdType(LocCommand.FAMILY_LOC, LocCommand.CMD_SET_DIR),
        new CmdType(LocCommand.FAMILY_LOC, LocCommand.CMD_NEW_GET_INFO));

    public List<CmdType> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    public SnacCommand genSnacCommand(SnacPacket packet) {
        if (packet.getFamily() != LocCommand.FAMILY_LOC) return null;

        int command = packet.getCommand();

        if (command == LocCommand.CMD_RIGHTS_REQ) {
            return new LocRightsRequest(packet);
        } else if (command == LocCommand.CMD_SET_INFO) {
            return new SetInfoCmd(packet);
        } else if (command == LocCommand.CMD_OLD_GET_INFO) {
            return new OldGetInfoCmd(packet);
        } else if (command == LocCommand.CMD_GET_DIR) {
            return new GetDirInfoCmd(packet);
        } else if (command == LocCommand.CMD_SET_INTERESTS) {
            return new SetInterestsCmd(packet);
        } else if (command == LocCommand.CMD_SET_DIR) {
            return new SetDirInfoCmd(packet);
        } else if (command == LocCommand.CMD_NEW_GET_INFO) {
            return new GetInfoCmd(packet);
        } else {
            return null;
        }
    }
}