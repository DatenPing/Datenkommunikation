package edu.hm.dako.chat.tcp;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.Socket;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.hm.dako.chat.connection.Connection;
import edu.hm.dako.chat.connection.ConnectionFactory;

/**
 * Erzeugen von TCP-Verbindungen zum Server
 * 
 * @author Peter Mandl
 *
 */
public class TcpConnectionFactory implements ConnectionFactory {

	private static Log log = LogFactory.getLog(TcpConnectionFactory.class);

	// Maximale Anzahl an Verbindungsaufbauversuchen zum Server, die ein Client
	// unternimmt, bevor er abbricht
	private static final int MAX_CONNECTION_ATTEMPTS = 50;

	// Zaehlt die Verbindungsaufbauversuche, bis eine Verbindung vom Server
	// angenommen wird
	private long connectionTryCounter = 0;

	/**
	 * Baut eine Verbindung zum Server auf. Der Verbindungsaufbau wird mehrmals
	 * versucht.
	 */
	public Connection connectToServer(String remoteServerAddress, int serverPort,
			int localPort, int sendBufferSize, int receiveBufferSize) throws IOException {

		TcpConnection connection = null;
		boolean connected = false;

		// Es wird "localhost" fuer die lokale IP-Adresse verwendet
		InetAddress localAddress = null;

		int attempts = 0;
		while ((!connected) && (attempts < MAX_CONNECTION_ATTEMPTS)) {
			try {

				connectionTryCounter++;

				connection = new TcpConnection(
						new Socket(remoteServerAddress, serverPort, localAddress, localPort),
						sendBufferSize, receiveBufferSize, false, true);
				connected = true;

			} catch (BindException e) {

				// Lokaler Port schon verwendet
				log.error(String.format("BindException beim Verbindungsaufbau: %s", e.getMessage()));

			} catch (IOException e) {

				log.error(String.format("IOException beim Verbindungsaufbau: %s", e.getMessage()));

				// Ein wenig warten und erneut versuchen
				attempts++;
				try {
					Thread.sleep(100);
				} catch (Exception e2) {
				}

			} catch (Exception e) {
				log.error(String.format("Sonstige Exception beim Verbindungsaufbau %s", e.getMessage()));
			}
			if (attempts >= MAX_CONNECTION_ATTEMPTS) {
				throw new IOException();
			}
		}

		log.debug(String.format("Anzahl der Verbindungsaufbauversuche fuer die Verbindung zum Server: %d", connectionTryCounter));
		return connection;
	}
}
