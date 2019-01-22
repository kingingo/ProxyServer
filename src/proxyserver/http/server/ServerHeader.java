package proxyserver.http.server;

import java.util.Date;

import proxyserver.http.Header;
import proxyserver.http.StatusCode;

public class ServerHeader extends Header{
	
	// Erstellt einen Header mit den Server & Date angaben.
	@SuppressWarnings("deprecation")
	public static Header create() {
		return new ServerHeader(new String[] { "Server: Java-Webserver", "Date: " + new Date().toGMTString() });
	}
	
	public ServerHeader(String... s) {
		super(s);
	}

	/**
	 * Faegt die Kopfzeile mit einen StatusCode hinzu mit der HTTP Version 1.0
	 */
	public Header addHeadline(StatusCode code) {
		super.addHeadline("HTTP/1.0 " + code.getMessage());
		return this;
	}
}
