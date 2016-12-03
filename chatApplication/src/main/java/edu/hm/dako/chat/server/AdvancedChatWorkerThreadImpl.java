package edu.hm.dako.chat.server;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.common.ClientConversationStatus;
import edu.hm.dako.chat.common.ClientListEntry;
import edu.hm.dako.chat.common.ExceptionHandler;
import edu.hm.dako.chat.connection.Connection;
import edu.hm.dako.chat.connection.ConnectionTimeoutException;
import edu.hm.dako.chat.connection.EndOfFileException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Vector;

/**
 * Worker-Thread zur serverseitigen Bedienung einer Session mit einem Client.
 * Jedem Chat-Client wird serverseitig ein Worker-Thread zugeordnet.
 *
 * @author Benjamin Königsberg
 */
public class AdvancedChatWorkerThreadImpl extends AbstractWorkerThread {

    private static Log log = LogFactory.getLog(AdvancedChatWorkerThreadImpl.class);

    private ChatPDU requestInProgress;
    private Thread requestResponderThread;

    public AdvancedChatWorkerThreadImpl(Connection con, SharedChatClientList clients, SharedServerCounter counter, ChatServerGuiInterface serverGuiInterface) {
        super(con, clients, counter, serverGuiInterface);
    }

    @Override
    public void run() {
        log.debug(String.format("ChatWorker-Thread erzeugt, Threadname: %s", Thread.currentThread().getName()));
        while (!finished && !Thread.currentThread().isInterrupted()) {
            try {
                // Warte auf naechste Nachricht des Clients und fuehre
                // entsprechende Aktion aus
                handleIncomingMessage();
            } catch (Exception e) {
                log.error("Exception waehrend der Nachrichtenverarbeitung");
                ExceptionHandler.logException(e);
            }
        }
        log.debug(Thread.currentThread().getName() + " beendet sich");
        closeConnection();
    }

    @Override
    protected void sendLoginListUpdateEvent(ChatPDU pdu) {

        // Liste der eingeloggten bzw. sich einloggenden User ermitteln
        Vector<String> clientList = clients.getRegisteredClientNameList();

        log.debug("Aktuelle Clientliste, die an die Clients uebertragen wird: " + clientList);

        pdu.setClients(clientList);

        Vector<String> clientList2 = clients.getClientNameList();
        for (String s : new Vector<String>(clientList2)) {
            log.debug(String.format("Fuer %s wird Login- oder Logout-Event-PDU an alle aktiven Clients gesendet", s));

            ClientListEntry client = clients.getClient(s);
            try {
                if (client != null) {

                    client.getConnection().send(pdu);
                    log.debug(String.format("Login- oder Logout-Event-PDU an %s gesendet", client.getUserName()));
                    clients.incrNumberOfSentChatEvents(client.getUserName());
                    eventCounter.getAndIncrement();
                    log.debug(String.format("%s: EventCounter bei Login/Logout erhoeht = %d, ConfirmCounter = %d",
                            userName, eventCounter.get(), confirmCounter.get()));
                }
            } catch (Exception e) {
                log.debug(String.format("Senden einer Login- oder Logout-Event-PDU an %s nicht moeglich", s));
                ExceptionHandler.logException(e);
            }
        }
    }

    @Override
    protected void loginRequestAction(ChatPDU receivedPdu) {

        ChatPDU pdu;
        log.debug("Login-Request-PDU fuer " + receivedPdu.getUserName() + " empfangen");

        // Neuer Client moechte sich einloggen, Client in Client-Liste
        // eintragen
        if (!clients.existsClient(receivedPdu.getUserName())) {
            log.debug("User nicht in Clientliste: " + receivedPdu.getUserName());
            ClientListEntry client = new ClientListEntry(receivedPdu.getUserName(), connection);
            client.setLoginTime(System.nanoTime());
            clients.createClient(receivedPdu.getUserName(), client);
            clients.changeClientStatus(receivedPdu.getUserName(),
                    ClientConversationStatus.REGISTERING);
            log.debug(String.format("User %s nun in Clientliste", receivedPdu.getUserName()));

            userName = receivedPdu.getUserName();
            clientThreadName = receivedPdu.getClientThreadName();
            Thread.currentThread().setName(receivedPdu.getUserName());
            log.debug(String.format("Laenge der Clientliste: %d", clients.size()));
            serverGuiInterface.incrNumberOfLoggedInClients();

            // Login-Event an alle Clients (auch an den gerade aktuell
            // anfragenden) senden
            pdu = ChatPDU.createLoginEventPdu(userName, receivedPdu);
            sendLoginListUpdateEvent(pdu);
        } else {
            // User bereits angemeldet, Fehlermeldung an Client senden,
            // Fehlercode an Client senden
            pdu = ChatPDU.createLoginErrorResponsePdu(receivedPdu, ChatPDU.LOGIN_ERROR);

            try {
                connection.send(pdu);
                log.debug(String.format("Login-Response-PDU an %s mit Fehlercode %d gesendet",
                        receivedPdu.getUserName(), ChatPDU.LOGIN_ERROR));
            } catch (Exception e) {
                log.debug(String.format("Senden einer Login-Response-PDU an %s nicth moeglich",
                        receivedPdu.getUserName()));
                ExceptionHandler.logExceptionAndTerminate(e);
            }
        }
    }

    private void sendLoginResponsePdu(ChatPDU receivedPdu) {
        // Login Response senden
        ChatPDU responsePdu = ChatPDU.createLoginResponsePdu(userName, receivedPdu);

        try {
            clients.getClient(userName).getConnection().send(responsePdu);
        } catch (Exception e) {
            log.debug(String.format("Senden einer Login-Response-PDU an %s fehlgeschlagen", userName));
            log.debug(String.format("Exception Message: %s", e.getMessage()));
        }

        log.debug("Login-Response-PDU an Client " + userName + " gesendet");

        // Zustand des Clients aendern
        clients.changeClientStatus(userName, ClientConversationStatus.REGISTERED);
    }

    @Override
    protected void logoutRequestAction(ChatPDU receivedPdu) {

        ChatPDU pdu;
        logoutCounter.getAndIncrement();
        log.debug(String.format("Logout-Request von %s, LogoutCount = %d", receivedPdu.getUserName(), logoutCounter.get()));

        log.debug(String.format("Logout-Request-PDU von %s empfangen", receivedPdu.getUserName()));

        if (!clients.existsClient(userName)) {
            log.debug(String.format("User nicht in Clientliste: %s", receivedPdu.getUserName()));
        } else {

            // Event an Client versenden
            pdu = ChatPDU.createLogoutEventPdu(userName, receivedPdu);

            clients.changeClientStatus(receivedPdu.getUserName(),
                    ClientConversationStatus.UNREGISTERING);
            sendLoginListUpdateEvent(pdu);
            serverGuiInterface.decrNumberOfLoggedInClients();

            clients.changeClientStatus(receivedPdu.getUserName(),
                    ClientConversationStatus.UNREGISTERED);
        }
    }

    /**
     * Antwort-PDU fuer den initiierenden Client aufbauen und senden
     *
     * @param eventInitiatorClient Name des Clients
     */
    private void sendLogoutResponse(String eventInitiatorClient) {

        ClientListEntry client = clients.getClient(eventInitiatorClient);

        if (client != null) {
            ChatPDU responsePdu = ChatPDU.createLogoutResponsePdu(eventInitiatorClient, 0, 0, 0,
                    0, client.getNumberOfReceivedChatMessages(), clientThreadName);

            log.debug(String.format("%s: SentEvents aus Clientliste: %d: ReceivedConfirms aus Clientliste: %d",
                    eventInitiatorClient, client.getNumberOfSentEvents(), client.getNumberOfReceivedEventConfirms()));
            try {
                clients.getClient(eventInitiatorClient).getConnection().send(responsePdu);
            } catch (Exception e) {
                log.debug(String.format("Senden einer Logout-Response-PDU an %s fehlgeschlagen", eventInitiatorClient));
                log.debug(String.format("Exception Message: %s", e.getMessage()));
            }

            log.debug(String.format("Logout-Response-PDU an Client %s gesendet", eventInitiatorClient));
        }

        // Worker-Thread des Clients, der den Logout-Request gesendet
        // hat, auch gleich zum Beenden markieren
        clients.finish(requestInProgress.getUserName());
        log.debug(String.format("Laenge der Clientliste beim Vormerken zum Loeschen von %s: %d",
                requestInProgress.getUserName(), clients.size()));
    }

    @Override
    protected void chatMessageRequestAction(ChatPDU receivedPdu) {

        String clientName = receivedPdu.getUserName();

        clients.setRequestStartTime(clientName, startTime);
        // Hier Anzahl für den angebundenen Client erhöt
        clients.incrNumberOfReceivedChatMessages(clientName);
        serverGuiInterface.incrNumberOfRequests();
        log.debug(String.format("Chat-Message-Request-PDU von %s mit Sequenznummer %d empfangen", clientName, receivedPdu.getSequenceNumber()));

        if (!clients.existsClient(clientName)) {
            log.debug(String.format("User nicht in Clientliste: %s", clientName));
        } else {
            // Liste der betroffenen Clients ermitteln
            Vector<String> sendList = clients.getClientNameList();
            ChatPDU pdu = ChatPDU.createChatMessageEventPdu(this.userName, receivedPdu);

            // Event an Clients senden
            for (String s : new Vector<>(sendList)) {
                ClientListEntry oneClient = clients.getClient(s);
                try {
                    if ((oneClient != null)
                            && (oneClient.getStatus() != ClientConversationStatus.UNREGISTERED)) {
                        pdu.setUserName(oneClient.getUserName());

                        // Resultiert innerhalb des MessageListenerThreadImpl in einem chatMessageEventAction() Aufruf
                        // Weil PDU-Type = PduType.CHAT_MESSAGE_EVENT
                        oneClient.getConnection().send(pdu);

                        log.debug(String.format("Chat-Event-PDU an %s gesendet", oneClient.getUserName()));
                        clients.incrNumberOfSentChatEvents(oneClient.getUserName());
                        eventCounter.getAndIncrement();
                        log.debug(String.format("%s: EventCounter erhoeht = %d, Aktueller ConfirmCounter = %d, " +
                                        "Anzahl gesendeter ChatMessages von dem Client = %d",
                                this.userName, eventCounter.get(), confirmCounter.get(), receivedPdu.getSequenceNumber()));
                    }
                } catch (Exception e) {
                    log.debug(String.format("Senden einer Chat-Event-PDU an %s nicht moeglich", oneClient.getUserName()));
                    ExceptionHandler.logException(e);
                }
            }

            ClientListEntry ownerClient = clients.getClient(clientName);
            if (ownerClient != null) {
                ChatPDU responsePdu = ChatPDU.createChatMessageResponsePdu(
                        clientName, 0, 0, 0, 0,
                        ownerClient.getNumberOfReceivedChatMessages(), receivedPdu.getClientThreadName(),
                        (System.nanoTime() - ownerClient.getStartTime()));

                if (responsePdu.getServerTime() / 1000000 > 100) {
                    log.debug(String.format("%s, Benoetigte Serverzeit vor dem Senden der Response-Nachricht > 100 ms: %d ns = %d ms",
                            Thread.currentThread().getName(), responsePdu.getServerTime(), responsePdu.getServerTime() / 1000000));
                }

                try {
                    // Resultiert innerhalb des MessageListenerThreadImpl in einem chatMessageResponseAction() Aufruf
                    // Weil PDU-Type = PduType.CHAT_MESSAGE_RESPONSE
                    ownerClient.getConnection().send(responsePdu);
                    log.debug(
                            String.format("Chat-Message-Response-PDU an %s gesendet", clientName));
                } catch (Exception e) {
                    log.debug(String.format("Senden einer Chat-Message-Response-PDU an %s nicht moeglich",
                            ownerClient.getUserName()));
                    ExceptionHandler.logExceptionAndTerminate(e);
                }
            }
            log.debug(String.format("Aktuelle Laenge der Clientliste: %d", clients.size()));
        }
    }

    /**
     * Verbindung zu einem Client ordentlich abbauen
     */
    private void closeConnection() {

        log.debug(String.format("Schliessen der Chat-Connection zum %s", userName));

        // Bereinigen der Clientliste falls erforderlich

        if (clients.existsClient(userName)) {
            log.debug(String.format("Close Connection fuer %s, Laenge der Clientliste " +
                            "vor dem bedingungslosen Loeschen: %d",
                    userName, clients.size()));

            clients.deleteClientWithoutCondition(userName);
            log.debug(String.format("Laenge der Clientliste nach dem bedingungslosen Loeschen von %s: %d",
                    userName, clients.size()));
        }

        try {
            connection.close();
        } catch (Exception e) {
            log.debug("Exception bei close");
            // ExceptionHandler.logException(e);
        }
    }

    /**
     * Prueft, ob Clients aus der Clientliste geloescht werden koennen
     *
     * @return boolean, true: Client geloescht, false: Client nicht geloescht
     */
    private boolean checkIfClientIsDeletable() {

        ClientListEntry client;

        // Worker-Thread beenden, wenn sein Client schon abgemeldet ist
        if (userName != null) {
            client = clients.getClient(userName);
            if (client != null) {
                if (client.isFinished()) {
                    // Loesche den Client aus der Clientliste
                    // Ein Loeschen ist aber nur zulaessig, wenn der Client
                    // nicht mehr in einer anderen Warteliste ist
                    log.debug(String.format("Laenge der Clientliste vor dem Entfernen von %s: %d", userName, clients.size()));
                    if (clients.deleteClient(userName) == true) {
                        // Jetzt kann auch Worker-Thread beendet werden

                        log.debug(String.format("Laenge der Clientliste nach dem Entfernen von %s: %d", userName, clients.size()));
                        log.debug(String.format("Worker-Thread fuer %s zum Beenden vorgemerkt", userName));
                        return true;
                    }
                }
            }
        }

        // Garbage Collection in der Clientliste durchfuehren
        Vector<String> deletedClients = clients.gcClientList();
        if (deletedClients.contains(userName)) {
            log.debug(String.format("Ueber Garbage Collector ermittelt: Laufender Worker-Thread fuer %s kann beendet werden", userName));
            finished = true;
            return true;
        }
        return false;
    }

    @Override
    protected void handleIncomingMessage() throws Exception {
        if (checkIfClientIsDeletable()) {
            return;
        }

        // Warten auf naechste Nachricht
        ChatPDU receivedPdu = null;

        // Nach einer Minute wird geprueft, ob Client noch eingeloggt ist
        final int RECEIVE_TIMEOUT = 60000;

        try {
            receivedPdu = (ChatPDU) connection.receive(RECEIVE_TIMEOUT);
            // Nachricht empfangen
            // Zeitmessung fuer Serverbearbeitungszeit starten
            startTime = System.nanoTime();

        } catch (ConnectionTimeoutException e) {

            // Wartezeit beim Empfang abgelaufen, pruefen, ob der Client
            // ueberhaupt noch etwas sendet
            log.debug(String.format("Timeout beim Empfangen, %d ms ohne Nachricht vom Client", RECEIVE_TIMEOUT));

            if (clients.getClient(userName) != null) {
                if (clients.getClient(userName)
                        .getStatus() == ClientConversationStatus.UNREGISTERING) {
                    // Worker-Thread wartet auf eine Nachricht vom Client, aber es
                    // kommt nichts mehr an
                    log.error("Client ist im Zustand UNREGISTERING und bekommt aber keine Nachricht mehr");
                    // Zur Sicherheit eine Logout-Response-PDU an Client senden und
                    // dann Worker-Thread beenden
                    finished = true;
                }
            }
            return;

        } catch (EndOfFileException e) {
            log.debug("End of File beim Empfang, vermutlich Verbindungsabbau des Partners");
            finished = true;
            return;

        } catch (java.net.SocketException e) {
            log.debug("Verbindungsabbruch beim Empfang der naechsten Nachricht vom Client "
                    + getName());
            finished = true;
            return;

        } catch (Exception e) {
            log.debug(
                    "Empfang einer Nachricht fehlgeschlagen, Workerthread fuer User: " + userName);
            ExceptionHandler.logException(e);
            finished = true;
            return;
        }

        // Empfangene Nachricht bearbeiten
        handleReceivedPDU(receivedPdu);
    }

    private void handleReceivedPDU(ChatPDU receivedPdu) {
        try {
            switch (receivedPdu.getPduType()) {
                case LOGIN_REQUEST:
                case CHAT_MESSAGE_REQUEST:
                case LOGOUT_REQUEST:
                    initiateRequest(receivedPdu);
                    break;
                case LOGIN_EVENT_CONFIRM:
                case CHAT_MESSAGE_EVENT_CONFIRM:
                case LOGOUT_EVENT_CONFIRM:
                    removeUsFromInitalRequestsWaitList(receivedPdu);
                    break;
                default:
                    log.debug(String.format("Falsche PDU empfangen von Client: %s, PduType: %s",
                            receivedPdu.getUserName(), receivedPdu.getPduType()));
                    break;
            }
        } catch (Exception e) {
            log.error("Exception bei der Nachrichtenverarbeitung");
            ExceptionHandler.logExceptionAndTerminate(e);
        }
    }

    private void initiateRequest(ChatPDU receivedPdu) {
        requestInProgress = receivedPdu;

        Vector<String> validClients = clients.getRegisteredClientNameList();
        clients.getClient(userName)
                .setWaitList(validClients);

        switch (receivedPdu.getPduType()) {
            case LOGIN_REQUEST:
                loginRequestAction(receivedPdu);
                break;
            case CHAT_MESSAGE_REQUEST:
                // Chat-Nachricht angekommen, an alle verteilen
                chatMessageRequestAction(receivedPdu);
                break;
            case LOGOUT_REQUEST:
                // Logout-Request vom Client empfangen
                logoutRequestAction(receivedPdu);
                break;
        }

        startResponderThread();
    }

    private void startResponderThread() {
        if (requestResponderThread != null) {
            requestResponderThread.interrupt();
        }

        requestResponderThread = new Thread(() -> {
            while (!isInterrupted()) {

                if (clients.getWaitListSize(userName) == 0) {
                    finishRequestInProgress();
                }

                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    // Propagate the interrupt to be checkable in the while-head
                    interrupt();
                }
            }
        });
        requestResponderThread.start();
    }

    private void removeUsFromInitalRequestsWaitList(ChatPDU receivedPdu) {
        String eventInitiator = receivedPdu.getEventUserName();

        clients.deleteWaitListEntry(eventInitiator, userName);
    }

    private void finishRequestInProgress() {
        switch (requestInProgress.getPduType()) {
            case LOGIN_REQUEST:
                sendLoginResponsePdu(requestInProgress);
                break;
            case CHAT_MESSAGE_REQUEST:
                // sendMessageResponse
                break;
            case LOGOUT_REQUEST:
                // Logout Response senden
                sendLogoutResponse(requestInProgress.getUserName());
                break;
        }
    }
}