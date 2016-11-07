package edu.hm.dako.chat.server;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.common.PduType;
import edu.hm.dako.chat.connection.Connection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static edu.hm.dako.chat.common.PduType.*;

/**
 * Worker-Thread zur serverseitigen Bedienung einer Session mit einem Client.
 * Jedem Chat-Client wird serverseitig ein Worker-Thread zugeordnet.
 *
 * @author Benjamin Königsberg
 */
public class AdvancedChatWorkerThreadImpl extends SimpleChatWorkerThreadImpl {

    private static Log log = LogFactory.getLog(AdvancedChatWorkerThreadImpl.class);

    public AdvancedChatWorkerThreadImpl(Connection con, SharedChatClientList clients, SharedServerCounter counter, ChatServerGuiInterface serverGuiInterface) {
        super(con, clients, counter, serverGuiInterface);
    }

    @Override
    void handleReceivedPDU(ChatPDU receivedPdu) {
        if (isConfirmationPDU(receivedPdu)) {
            log.debug("PDU ist eine Bestätigung und kann nur vom AdvancedChat behandelt werden");
            handleConfirmationPdu(receivedPdu);
        } else {
            super.handleReceivedPDU(receivedPdu);
        }
    }

    private boolean isConfirmationPDU(ChatPDU receivedPdu) {
        PduType pduType = receivedPdu.getPduType();

        return pduType == CHAT_MESSAGE_EVENT_CONFIRM
                || pduType == LOGIN_EVENT_CONFIRM
                || pduType == LOGOUT_EVENT_CONFIRM;
    }

    private void handleConfirmationPdu(ChatPDU receivedPdu) {
        // Prüft ob alle erhalten haben
        // sendet response an ursprünglichen Absender

        switch (receivedPdu.getPduType()) {
            case CHAT_MESSAGE_EVENT_CONFIRM:

            case LOGIN_EVENT_CONFIRM:

            case LOGOUT_EVENT_CONFIRM:

                break;

        }
    }
}