package edu.hm.dako.chat.server;

import edu.hm.dako.chat.connection.ServerSocketInterface;

import java.util.concurrent.ExecutorService;

/**
 * <p/>
 * Advanced-Chat-Server-Implementierung Der ChatServer wird in einem eigenen
 * Thread gestartet. Er nimmt alle Verbindungsaufbauwuensche der ChatClients
 * entgegen und startet fuer jede Verbindung jeweils einen eigenen Worker-Thread
 *
 * @author Benjamin Königsberg
 */
public class AdvancedChatServerImpl extends SimpleChatServerImpl{

    /**
     * Konstruktor
     *
     * @param executorService
     * @param socket
     * @param serverGuiInterface
     */
    public AdvancedChatServerImpl(ExecutorService executorService, ServerSocketInterface socket, ChatServerGuiInterface serverGuiInterface) {
        super(executorService, socket, serverGuiInterface);
    }
}