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
 *  File created by keith @ Feb 27, 2003
 *
 */

package net.kano.joscar.snaccmd.chat;

import net.kano.joscar.*;
import net.kano.joscar.tlv.MutableTlvChain;
import net.kano.joscar.tlv.Tlv;
import net.kano.joscar.tlv.TlvChain;
import net.kano.joscar.tlv.TlvTools;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Represents a single message sent or received in a chat room.
 */
public class ChatMsg implements LiveWritable {
    public static final String CONTENTTYPE_DEFAULT = "text/x-aolrtf";
    public static final String CONTENTENCODING_DEFAULT = "binary";

    /**
     * Reads a chat message from the given data block.
     *
     * @param msgBlock the data block containing chat message TLV's
     * @return a chat message object read from the given data block
     */
    public static ChatMsg readChatMsg(ByteBlock msgBlock) {
        DefensiveTools.checkNull(msgBlock, "msgBlock");

        TlvChain msgChain = TlvTools.readChain(msgBlock);

        String contentType = msgChain.getString(TYPE_CONTENT_TYPE);
        String contentEncoding = msgChain.getString(TYPE_CONTENT_ENCODING);
        String charset = msgChain.getString(TYPE_CHARSET);
        ByteBlock messageData = msgChain.getLastTlv(TYPE_BODY).getData();
        String language = msgChain.getString(TYPE_LANG);

        return new ChatMsg(contentType, contentEncoding, charset, messageData,
                new Locale(language));
    }

    /** A TLV type containing the charset in which this message is encoded. */
    private static final int TYPE_CHARSET = 0x0002;
    /** A TLV type containing the text of the message. */
    private static final int TYPE_BODY = 0x0001;
    /**
     * A TLV type containing the language in which this message was supposedly
     * written, as a two-letter code.
     */
    private static final int TYPE_LANG = 0x0003;

    private static final int TYPE_CONTENT_TYPE = 0x0004;
    private static final int TYPE_CONTENT_ENCODING = 0x0005;

    /** The chat message. */
    private final ByteBlock messageData;
    /** The locale (language code only) under which this message was written. */
    private final Locale language;

    /** The content type of this message. */
    private final String contentType;
    /** The content type of this message. */
    private final String contentEncoding;
    /** The charset with which the message is encoded. */
    private String charset;

    /**
     * Creates a new unencrypted chat message in the JVM's current language.
     * Calling this method is equivalent to calling {@link #ChatMsg(String,
     * Locale) new ChatMessage(message, Locale.getDefault())}.
     *
     * @param message the text of this chat message
     */
    public ChatMsg(String message) {
        this(message, Locale.getDefault());
    }

    /**
     * Creates a new unencrypted chat message in the given language.
     *
     * @param message the text of this chat message
     * @param locale a locale object representing the language in which this
     *        message is written
     */
    public ChatMsg(String message, Locale locale) {
        EncodedStringInfo stringInfo = MinimalEncoder.encodeMinimally(message);
        this.contentType = CONTENTTYPE_DEFAULT;
        this.contentEncoding = CONTENTENCODING_DEFAULT;
        this.charset = stringInfo.getCharset();
        this.messageData = ByteBlock.wrap(stringInfo.getData());
        this.language = locale;
    }

    /**
     * Creates a new chat message with the given properties.
     *
     * @param contentType a content type string, like {@link
     *        #CONTENTTYPE_DEFAULT}
     * @param contentEncoding a content encoding string, like {@link
     *        #CONTENTENCODING_DEFAULT}
     * @param charset the charset in which the message is encoded
     * @param messageData the message data, possibly encrypted
     * @param language a <code>Locale</code> object representing the language
     *        in which the message was written
     */
    public ChatMsg(String contentType, String contentEncoding, String charset,
            ByteBlock messageData, Locale language) {
        this.contentType = contentType;
        this.contentEncoding = contentEncoding;
        this.charset = charset;
        this.messageData = messageData;
        this.language = language;
    }

    /**
     * Returns the binary message data block contained in this message. If this
     * is an encrypted message, this will normally be the CMS signed data block;
     * otherwise, this will normally simply be the binary form of the message
     * text. See {@link #getMessage()} for details on extracting a message from
     * an unencrypted message block.
     *
     * @return
     */
    public final ByteBlock getMessageData() { return messageData; }

    public final String getContentType() { return contentType; }

    public final String getContentEncoding() { return contentEncoding; }

    public final String getCharset() { return charset; }

    public final Locale getLanguage() { return language; }

    /*
     * This method returns null if contentType is non-null and not equal to
     * CONTENTTYPE_DEFAULT.
     */
    public final String getMessage() {
        if (contentType == null || contentType.equals(CONTENTTYPE_DEFAULT)) {
            return getMessageAsString();
        } else {
            return null;
        }
    }

    public final String getMessageAsString() {
        return OscarTools.getString(messageData, charset);
    }

    public void write(OutputStream out) throws IOException {
        MutableTlvChain msgChain = TlvTools.createMutableChain();

        if (contentType != null) {
            msgChain.addTlv(Tlv.getStringInstance(
                    TYPE_CONTENT_TYPE, contentType));
        }
        if (charset != null) {
            msgChain.addTlv(Tlv.getStringInstance(TYPE_CHARSET, charset));
        }
        if (language != null) {
            msgChain.addTlv(Tlv.getStringInstance(TYPE_LANG,
                    language.getLanguage()));
        }
        if (contentEncoding != null) {
            msgChain.addTlv(Tlv.getStringInstance(
                    TYPE_CONTENT_ENCODING, contentEncoding));
        }
        if (messageData != null) {
            msgChain.addTlv(new Tlv(TYPE_BODY, messageData));
        }

        msgChain.write(out);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("ChatMsg: ");
        String msgStr = getMessage();
        if (msgStr != null) {
            sb.append(msgStr + ": ");
        }
        sb.append("contentType=" + contentType);
        sb.append(", charset=" + charset);
        sb.append(", language="
                + (language != null ? language.getLanguage() : null));
        sb.append(", contentEncoding=" + contentEncoding);
        sb.append(", msgData: " + messageData.getLength() + " bytes");

        return sb.toString();
    }
}
