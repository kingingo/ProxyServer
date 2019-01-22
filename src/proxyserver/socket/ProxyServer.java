package proxyserver.socket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

import proxyserver.http.HttpHandler;

public class ProxyServer implements Runnable{

	public static void main(String[] args) {
		ProxyServer server = new ProxyServer(2000);
		server.start();
	}
	
	private int listen;
	private ServerSocket server;
	private Thread thread;
	private ArrayList<ProxyConnection> connections = new ArrayList<>();
	
	public ProxyServer(int listen) {
		this.listen=listen;
		this.thread=new Thread(this);
		try {
			this.server=new ServerSocket(this.listen);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void start() {
		this.thread.start();
	}

	@Override
	public void run() {
		print("starts...");
		while(!this.server.isClosed()) {
			try {
				Socket client = this.server.accept();
				print(client.getInetAddress().getHostAddress()+ " connected");
				this.connections.add(new ProxyConnection(client));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if(!this.server.isClosed()) {
			try {
				this.server.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		print("stopped");
	}
	
	public void print(String msg) {
		System.out.println("[ProxyServer:] "+msg);
	}
}
