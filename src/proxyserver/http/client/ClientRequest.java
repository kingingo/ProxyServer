package proxyserver.http.client;

import java.io.BufferedReader;

import proxyserver.http.Header;
import proxyserver.http.server.ServerResponse;

public class ClientRequest {
	private Header header;
	private ServerResponse response;
	
	public static ClientRequest get(BufferedReader reader) {
		return new ClientRequest();
	}
	
	public ClientRequest() {
		
	}
}
