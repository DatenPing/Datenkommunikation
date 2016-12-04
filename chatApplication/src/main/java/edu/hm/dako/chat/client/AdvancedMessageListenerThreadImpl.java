package edu.hm.dako.chat.client;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.common.ClientConversationStatus;
import edu.hm.dako.chat.common.ExceptionHandler;
import edu.hm.dako.chat.connection.Connection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Thread wartet auf ankommende Nachrichten vom Server und bearbeitet diese.
 *
 * @author
 */
public class AdvancedMessageListenerThreadImpl extends AbstractMessageListenerThread {

    private static Log log = LogFactory.getLog(SimpleMessageListenerThreadImpl.class);

    public AdvancedMessageListenerThreadImpl(ClientUserInterface userInterface, Connection con, SharedClientData sharedData) {
        super(userInterface, con, sharedData);
    }

    @Override
    protected void loginResponseAction(ChatPDU receivedPdu) {

        if (receivedPdu.getErrorCode() == ChatPDU.LOGIN_ERROR) {

            // Login hat nicht funktioniert
            log.error(String.format("Login-Response-PDU fuer Client %s mit Login-Error empfangen",
                    receivedPdu.getUserName()));
            userInterface.setErrorMessage("Chat-Server",
                    String.format("Anmelden beim Server nicht erfolgreich, Benutzer %s vermutlich schon angemeldet",
                            receivedPdu.getUserName()),
                    receivedPdu.getErrorCode());
            sharedClientData.status = ClientConversationStatus.UNREGISTERED;

            // Verbindung wird gleich geschlossen
            try {
                connection.close();
            } catch (Exception e) {
            }

        } else {
            // Login hat funktioniert
            sharedClientData.status = ClientConversationStatus.REGISTERED;

            userInterface.loginComplete();

            Thread.currentThread().setName(String.format("Listener-%s", sharedClientData.userName));
            log.debug(String.format("Login-Response-PDU fuer Client %s empfangen", receivedPdu.getUserName()));
        }
    }

    @Override
    protected void loginEventAction(ChatPDU receivedPdu) {

        ChatPDU confirmEventPdu = ChatPDU.createLoginEventConfirm(receivedPdu.getUserName(), receivedPdu);

        // Eventzaehler fuer Testzwecke erhoehen
        sharedClientData.eventCounter.getAndIncrement();

        try {
            handleUserListEvent(receivedPdu);
        } catch (Exception e) {
            ExceptionHandler.logException(e);
        }

        //sende confirmEventPdu an Server
        try {
            connection.send(confirmEventPdu);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void logoutResponseAction(ChatPDU receivedPdu) {

        log.debug(String.format("%s empfaengt Logout-Response-PDU fuer Client %s", sharedClientData.userName, receivedPdu.getUserName()));
        sharedClientData.status = ClientConversationStatus.UNREGISTERED;

        userInterface.setSessionStatisticsCounter(sharedClientData.eventCounter.longValue(),
                sharedClientData.confirmCounter.longValue(), 0, 0, 0);

        log.debug(String.format("Vom Client gesendete Chat-Nachrichten:  %d", sharedClientData.messageCounter.get()));

        finished = true;
        userInterface.logoutComplete();
    }

    @Override
    protected void logoutEventAction(ChatPDU receivedPdu) {

        ChatPDU logoutConfirm = ChatPDU.createLogoutEventConfirm(receivedPdu.getUserName(), receivedPdu);

        // Eventzaehler fuer Testzwecke erhoehen
        sharedClientData.eventCounter.getAndIncrement();

        try {
            handleUserListEvent(receivedPdu);
        } catch (Exception e) {
            ExceptionHandler.logException(e);
        }

        //sende EventConfirmPDU an Server
        try {
            connection.send(logoutConfirm);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void chatMessageResponseAction(ChatPDU receivedPdu) {

        log.debug(String.format("Sequenznummer der Chat-Response-PDU %s: %d, Messagecounter: %d",
                receivedPdu.getUserName(), receivedPdu.getSequenceNumber(), sharedClientData.messageCounter.get()));

        log.debug(String.format("%s, Benoetigte Serverzeit gleich nach Empfang der Response-Nachricht: %d ns = %d ms",
                Thread.currentThread().getName(), receivedPdu.getServerTime(), receivedPdu.getServerTime() / 1000000));

        if (receivedPdu.getSequenceNumber() == sharedClientData.messageCounter.get()) {

            // Zuletzt gemessene Serverzeit fuer das Benchmarking
            // merken
            userInterface.setLastServerTime(receivedPdu.getServerTime());

            // Naechste Chat-Nachricht darf eingegeben werden
            userInterface.setLock(false);

            log.debug(
                    String.format("Chat-Response-PDU fuer Client %s empfangen", receivedPdu.getUserName()));

        } else {
            log.debug(String.format("Sequenznummer der Chat-Response-PDU %s passt nicht: %d/%d",
                    receivedPdu.getUserName(), receivedPdu.getSequenceNumber(), sharedClientData.messageCounter.get()));
        }
    }

    @Override
    protected void chatMessageEventAction(ChatPDU receivedPdu) {

        ChatPDU confirmMessagePdu = ChatPDU.createChatMessageEventConfirm(receivedPdu.getUserName(), receivedPdu);

        log.debug(String.format("Chat-Message-Event-PDU von %s empfangen", receivedPdu.getEventUserName()));

        // Eventzaehler fuer Testzwecke erhoehen
        sharedClientData.eventCounter.getAndIncrement();

        // Empfangene Chat-Nachricht an User Interface zur
        // Darstellung uebergeben
        userInterface.setMessageLine(receivedPdu.getEventUserName(),
                (String) receivedPdu.getMessage());

        //sende confirmEventPdu an Server
        try {
            connection.send(confirmMessagePdu);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Bearbeitung aller vom Server ankommenden Nachrichten
     */
    public void run() {

        ChatPDU receivedPdu = null;

        log.debug("SimpleMessageListenerThread gestartet");

        while (!finished) {

            try {
                // Naechste ankommende Nachricht empfangen
                log.debug("Auf die naechste Nachricht vom Server warten");
                receivedPdu = receive();
                log.debug("Nach receive Aufruf, ankommende PDU mit PduType = "
                        + receivedPdu.getPduType());
            } catch (Exception e) {
                finished = true;
            }

            if (receivedPdu != null) {

                switch (sharedClientData.status) {

                    case REGISTERING:

                        switch (receivedPdu.getPduType()) {

                            case LOGIN_RESPONSE:
                                // Login-Bestaetigung vom Server angekommen
                                loginResponseAction(receivedPdu);

                                break;

                            case LOGIN_EVENT:
                                // Meldung vom Server, dass sich die Liste der
                                // angemeldeten User erweitert hat
                                loginEventAction(receivedPdu);

                                break;

                            case LOGOUT_EVENT:
                                // Meldung vom Server, dass sich die Liste der
                                // angemeldeten User veraendert hat
                                logoutEventAction(receivedPdu);

                                break;

                            case CHAT_MESSAGE_EVENT:
                                // Chat-Nachricht vom Server gesendet
                                chatMessageEventAction(receivedPdu);
                                break;

                            default:
                                log.debug("Ankommende PDU im Zustand " + sharedClientData.status
                                        + " wird verworfen");
                        }
                        break;

                    case REGISTERED:

                        switch (receivedPdu.getPduType()) {

                            case CHAT_MESSAGE_RESPONSE:

                                // Die eigene zuletzt gesendete Chat-Nachricht wird vom
                                // Server bestaetigt.
                                chatMessageResponseAction(receivedPdu);
                                break;

                            case CHAT_MESSAGE_EVENT:
                                // Chat-Nachricht vom Server gesendet
                                chatMessageEventAction(receivedPdu);
                                break;

                            case LOGIN_EVENT:
                                // Meldung vom Server, dass sich die Liste der
                                // angemeldeten User erweitert hat
                                loginEventAction(receivedPdu);

                                break;

                            case LOGOUT_EVENT:
                                // Meldung vom Server, dass sich die Liste der
                                // angemeldeten User veraendert hat
                                logoutEventAction(receivedPdu);

                                break;

                            default:
                                log.debug("Ankommende PDU im Zustand " + sharedClientData.status
                                        + " wird verworfen");
                        }
                        break;

                    case UNREGISTERING:

                        switch (receivedPdu.getPduType()) {

                            case CHAT_MESSAGE_EVENT:
                                // Chat-Nachricht vom Server gesendet
                                chatMessageEventAction(receivedPdu);
                                break;

                            case LOGOUT_RESPONSE:
                                // Bestaetigung des eigenen Logout
                                logoutResponseAction(receivedPdu);
                                break;

                            case LOGIN_EVENT:
                                // Meldung vom Server, dass sich die Liste der
                                // angemeldeten User erweitert hat
                                loginEventAction(receivedPdu);

                                break;

                            case LOGOUT_EVENT:
                                // Meldung vom Server, dass sich die Liste der
                                // angemeldeten User veraendert hat
                                logoutEventAction(receivedPdu);

                                break;

                            default:
                                log.debug(String.format("Ankommende PDU im Zustand %s wird verworfen", sharedClientData.status));
                                break;
                        }
                        break;

                    case UNREGISTERED:
                        log.debug(
                                String.format("Ankommende PDU im Zustand %s wird verworfen", sharedClientData.status));

                        break;

                    default:
                        log.debug(String.format("Unzulaessiger Zustand %s", sharedClientData.status));
                }
            }
        }

        // Verbindung noch schliessen
        try {
            connection.close();
        } catch (Exception e) {
            ExceptionHandler.logException(e);
        }
        log.debug(String.format("Ordnungsgemaesses Ende des SimpleMessageListener-Threads fuer User%s, Status: %s", sharedClientData.userName, sharedClientData.status));
    } // run
}