package proxyserver.http.client;

import java.io.BufferedReader;
import java.io.IOException;

import proxyserver.http.Header;

public class ClientHeader extends Header{

	public ClientHeader(String... s) {
		super(s);
	}

	/**
	 * Faegt die Kopfzeile mit einen StatusCode hinzu mit der HTTP Version 1.0
	 */
	public Header addHeadline(RequestMethod request,String URL) {
		StringBuilder builder = new StringBuilder();
		
		builder.append(request.name()).append(" ");
		builder.append(URL).append(" ");
		builder.append("HTTP/1.0");
		
		super.addHeadline(builder.toString());
		return this;
	}
}
