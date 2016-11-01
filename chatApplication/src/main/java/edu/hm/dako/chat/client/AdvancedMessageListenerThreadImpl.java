package edu.hm.dako.chat.client;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.connection.Connection;

/**
 * Thread wartet auf ankommende Nachrichten vom Server und bearbeitet diese.
 * 
 * @author
 *
 */
public class AdvancedMessageListenerThreadImpl extends SimpleMessageListenerThreadImpl {


    public AdvancedMessageListenerThreadImpl(ClientUserInterface userInterface, Connection con, SharedClientData sharedData) {
        super(userInterface, con, sharedData);
    }

}