package proxyserver.socket;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.URL;
import java.util.Base64;

import lombok.Getter;
import lombok.Setter;
import proxyserver.socket.protocol.Socks4Protocol;
import proxyserver.socket.protocol.Socks5Protocol;

public class ProxyConnection implements Runnable {

	@Getter
	@Setter
	private Socket client;
	private InputStream fromClient;
	private OutputStream toClient;

	@Getter
	@Setter
	private Socket server;
	private InputStream fromServer;
	private OutputStream toServer;

	public byte[] m_Buffer = null;
	protected Object m_lock;

	private Thread thread;
	private Socks4Protocol protocol;

	public ProxyConnection(Socket client) {
		this.client = client;
		if (this.client != null) {
			try {
				this.client.setSoTimeout(Constants.DEFAULT_PROXY_TIMEOUT);
			} catch (SocketException e) {
				print("Socket Exception during seting Timeout.");
			}
		}
		this.m_Buffer = new byte[Constants.DEFAULT_BUF_SIZE];
		this.thread = new Thread(this);
		this.thread.start();
	}

	private void prepareClient() throws Exception {
		if (this.client != null) {
			try {
				this.fromClient = this.client.getInputStream();
				this.toClient = this.client.getOutputStream();
				return;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		throw new Exception("prepareClient() made a mistake...");
	}

	public void prepareServer() throws Exception {
		synchronized (m_lock) {
			if (this.server != null) {
				try {
					this.fromServer = this.server.getInputStream();
					this.toServer = this.server.getOutputStream();
					return;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		throw new Exception("prepareServer() made a mistake...");
	}

	public void setLock(Object lock) {
		this.m_lock = lock;
	}

	public void close() {
		try {
			if (this.toClient != null) {
				this.toClient.flush();
				this.toClient.close();
			}
		} catch (IOException e) {
		}
		try {
			if (this.toServer != null) {
				this.toServer.flush();
				this.toServer.close();
			}
		} catch (IOException e) {
		}

		try {
			if (this.client != null) {
				this.client.close();
			}
		} catch (IOException e) {
		}

		try {
			if (this.server != null) {
				this.server.close();
			}
		} catch (IOException e) {
		}

		this.server = null;
		this.client = null;

		print("Proxy Closed.");
	}

	public byte getByteFromClient() throws Exception {
		int b;
		while (this.client != null) {

			try {
				b = this.fromClient.read();
			} catch (InterruptedIOException e) {
				Thread.yield();
				continue;
			}

			return (byte) b;

		}
		throw new Exception("Interrupted Reading GetByteFromClient()");
	}

	public void processRelay() throws Exception {
		byte SOCKET_VERSION = getByteFromClient();

		switch (SOCKET_VERSION) {
		case Constants.SOCKS4_Version:
			this.protocol = new Socks4Protocol(this);
			print("Socket Version 4");
			break;
		case Constants.SOCKS5_Version:
			this.protocol = new Socks5Protocol(this);
			print("Socket Version 5");
			break;
		default:
			throw new Exception("Socket version couldn't suit " + SOCKET_VERSION);
		}
		this.protocol.authenticate( SOCKET_VERSION );
		byte command = this.protocol.getClientCommand();

		switch (command) {
		case Constants.SC_CONNECT:
			this.protocol.connect();
			relay();
			break;

		case Constants.SC_BIND:
			this.protocol.bind();
			relay();
			break;

		case Constants.SC_UDP:
			this.protocol.udp();
			break;
		}

	}

	public boolean isActive() {
		return (client != null && server != null);
	}

	public void relay() {

		boolean isActive = true;
		int dlen = 0;

		while (isActive) {

			// ---> read from client <---

			dlen = readClientData();
			translateToHttp("Read from Client");
			if (dlen < 0)
				isActive = false;
			if (dlen > 0) {
				logClientData(dlen);
				sendToServer(m_Buffer, dlen);
			}

			// ---> read from Server <---
			dlen = readServerData();
			translateToHttp("Read from Server");
			if (dlen < 0)
				isActive = false;
			if (dlen > 0) {
				logServerData(dlen);
				sendToClient(m_Buffer, dlen);
			}

			Thread.currentThread();
			Thread.yield();
		} // while
	}
	
	public void translateToHttp(String s) {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(m_Buffer.clone())));
			
//			String line;
//			while((line=in.readLine())!=null)System.out.println("LINE: "+line);
			
			Header header = Header.read(in);
			if(!header.isEmpty())System.out.println(s+": "+header);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public int readClientData() {
		synchronized (m_lock) {
			// The client side is not opened.
			if (this.fromClient == null)
				return -1;

			int dlen = 0;

			try {
				dlen = this.fromClient.read(m_Buffer, 0, Constants.DEFAULT_BUF_SIZE);
			} catch (InterruptedIOException e) {
				return 0;
			} catch (IOException e) {
				print("Client connection Closed!");
				close(); // Close the server on this exception
				return -1;
			}

			if (dlen < 0)
				close();

			return dlen;
		}
	}

	public int readServerData() {
		synchronized (m_lock) {
			// The client side is not opened.
			if (this.fromServer == null)
				return -1;

			int dlen = 0;

			try {
				dlen = this.fromServer.read(m_Buffer, 0, Constants.DEFAULT_BUF_SIZE);
			} catch (InterruptedIOException e) {
				return 0;
			} catch (IOException e) {
				print("Server connection Closed!");
				close(); // Close the server on this exception
				return -1;
			}

			if (dlen < 0)
				close();

			return dlen;
		}
	}

	public void connectToServer(InetAddress adress, int port) throws Exception {
		this.server = new Socket(adress, port);
		this.server.setSoTimeout(Constants.DEFAULT_PROXY_TIMEOUT);
		prepareServer();
	}

	@Override
	public void run() {
		try {
			setLock(this);
			prepareClient();
			processRelay();
			close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void logServerData(int traffic) {
		print("Srv data : " + Utils.getSocketInfo(this.server) + " << <" + this.protocol.getServerAdress().getHostName()
				+ "/" + this.protocol.getServerAdress().getHostAddress() + ":" + this.protocol.getServerPort() + "> : " + traffic
				+ " bytes.");
	}

	public void logClientData(int traffic) {
		print("Cli data : " + Utils.getSocketInfo(this.client) + " >> <" + this.protocol.getServerAdress().getHostName()
				+ "/" + this.protocol.getServerAdress().getHostAddress() + ":" + this.protocol.getServerPort() + "> : " + traffic
				+ " bytes.");
	}

	public void print(String msg) {
		//System.out.println("[ProxyConnection|]: " + msg);
	}

	public void sendToServer(byte[] buffer) {
		sendToServer(buffer, buffer.length);
	}

	public void sendToServer(byte[] buffer, int len) {
		if (this.toServer == null)
			return;
		if (len <= 0 || len > buffer.length)
			return;

		try {
			this.toServer.write(buffer, 0, len);
			this.toServer.flush();
		} catch (IOException e) {
			print("Sending data to server");
		}
	}

	public void sendToClient(byte[] buffer) {
		sendToClient(buffer, buffer.length);
	}

	public void sendToClient(byte[] buffer, int len) {
		if (this.toClient == null)
			return;
		if (len <= 0 || len > buffer.length)
			return;

		try {
			this.toClient.write(buffer, 0, len);
			this.toClient.flush();
		} catch (IOException e) {
			print("Sending data to client");
		}
	}
}
