package proxyserver.http.client;

import java.io.BufferedReader;

public class ClientRequest {
	private ClientHeader header;
	
	public static ClientRequest get(BufferedReader reader) {
		return new ClientRequest();
	}
	
	public ClientRequest() {
		
	}
}
