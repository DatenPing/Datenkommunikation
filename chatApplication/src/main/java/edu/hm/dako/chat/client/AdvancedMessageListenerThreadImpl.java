package edu.hm.dako.chat.client;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.connection.Connection;

/**
 * Thread wartet auf ankommende Nachrichten vom Server und bearbeitet diese.
 * 
 * @author
 *
 */
public class AdvancedMessageListenerThreadImpl extends AbstractMessageListenerThread {

    public AdvancedMessageListenerThreadImpl(ClientUserInterface userInterface, Connection con, SharedClientData sharedData) {
        super(userInterface, con, sharedData);
    }

    @Override
    protected void chatMessageResponseAction(ChatPDU receivedPdu) {

    }

    @Override
    protected void chatMessageEventAction(ChatPDU receivedPdu) {

    }

    @Override
    protected void loginResponseAction(ChatPDU receivedPdu) {

    }

    @Override
    protected void loginEventAction(ChatPDU receivedPdu) {

    }

    @Override
    protected void logoutEventAction(ChatPDU receivedPdu) {

    }

    @Override
    protected void logoutResponseAction(ChatPDU receivedPdu) {

    }
}