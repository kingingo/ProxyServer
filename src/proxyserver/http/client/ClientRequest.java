package proxyserver.http.client;

import java.io.BufferedReader;

import proxyserver.http.server.ServerResponse;

public class ClientRequest {
	private ClientHeader header;
	private ServerResponse response;
	
	public static ClientRequest get(BufferedReader reader) {
		return new ClientRequest();
	}
	
	public ClientRequest() {
		
	}
}
