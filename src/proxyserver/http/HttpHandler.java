package proxyserver.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

public class HttpHandler extends Thread{
	private static HashMap<String,HttpHandler> http_handlers = new HashMap<>();
	
	public static HttpHandler get(InetAddress adress) {
		if(!http_handlers.containsKey(adress.getHostAddress())) {
			System.out.println("IP:"+adress.getHostName());
			http_handlers.put(adress.getHostAddress(), new HttpHandler());
		}
		return http_handlers.get(adress.getHostAddress());
	}
	
	private OutInInputRedirect client;
	private OutInInputRedirect server;
	
	private HttpHandler() {
		prepareStream();
		super.start();
	}
	
	public void prepareStream() {
		try {
			this.client = new OutInInputRedirect();
			this.server = new OutInInputRedirect();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void run() {
		try {
			int d;
			while( (d=this.client.reader.read())!=-1) {
				System.out.println("D:"+d);
			}
			System.out.println("END READING");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void writeToServer(byte[] bytes,int start, int length) {
		try {
			this.server.os.write(bytes,start,length);
			this.server.os.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void writeToClient(byte[] bytes,int start, int length) {
		try {
			this.client.os.write(bytes,start,length);
			this.client.os.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
