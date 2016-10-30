package edu.hm.dako.chat.server;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.connection.Connection;

/**
 * Worker-Thread zur serverseitigen Bedienung einer Session mit einem Client.
 * Jedem Chat-Client wird serverseitig ein Worker-Thread zugeordnet.
 *
 * @author Benjamin Königsberg
 */
public class AdvancedChatWorkerThreadImpl extends SimpleChatWorkerThreadImpl {

    public AdvancedChatWorkerThreadImpl(Connection con, SharedChatClientList clients, SharedServerCounter counter, ChatServerGuiInterface serverGuiInterface) {
        super(con, clients, counter, serverGuiInterface);
    }

}