package proxyserver.socket.protocol;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import lombok.Getter;
import proxyserver.socket.Constants;
import proxyserver.socket.ProxyConnection;
import proxyserver.socket.Utils;

public class Socks4Protocol {
	public byte socksVersion = 0;
	protected ProxyConnection connection;

	protected byte[] DST_Port = new byte[2];
	protected byte[] DST_IP = new byte[4];

	private String UID;
	public byte UserID[] = null;

	@Getter
	protected InetAddress clientAdress = null;
	@Getter
	protected int clientPort = 0;

	@Getter
	protected InetAddress serverAdress;
	@Getter
	protected int serverPort;
	protected InetAddress extLocalAdress = null;

	@Getter
	private byte successCode = 90;
	@Getter
	private byte failCode = 91;

	public Socks4Protocol(ProxyConnection connection) {
		this.connection = connection;
	}

	public void authenticate(byte SOCKS_Ver) throws Exception {
		this.socksVersion = SOCKS_Ver;
		// Not supported for Socks4
	}

	protected byte getByte() {
		byte b;
		try {
			b = this.connection.getByteFromClient();
		} catch (Exception e) {
			b = 0x00;
		}
		return b;
	}

	public byte getClientCommand() throws Exception {
		byte CD = getByte(); // get the Command / VN was already get
		this.DST_Port[0] = getByte();
		this.DST_Port[1] = getByte();

		for (int i = 0; i < 4; i++) {
			this.DST_IP[i] = getByte();
		}

		byte b;
		while ((b = getByte()) != 0x00) {
			UID += (char) b;
		}

		if ((CD < Constants.SC_CONNECT) || (CD > Constants.SC_BIND)) {
			refuseCommand((byte) 91);
			throw new Exception("Socks 4 - Unsupported Command : " + Constants.commandName(CD));
		}
		
		if (!calculateAddress()) { // Gets the IP Address
			refuseCommand((byte) 92); // Host Not Exists...
			throw new Exception("Socks 4 - Unknown Host/IP address '" + serverAdress.toString());
		}

		return CD;
	}

	protected void refuseCommand(byte errorCode) {
		replyCommand(errorCode);
	}

	public void bind() throws Exception {
		ServerSocket ssock = null;
		InetAddress MyIP = null;
		int MyPort = 0;

		System.out.println("Binding...");
		// Resolve External IP
		MyIP = resolveExternalLocalIP();

		System.out.println("Local IP : " + MyIP.toString());

		try {
			ssock = new ServerSocket(0);
			ssock.setSoTimeout(Constants.DEFAULT_PROXY_TIMEOUT);
			MyPort = ssock.getLocalPort();
		} catch (IOException e) { // MyIP == null
			System.out.println("Error in BIND() - Can't BIND at any Port");
			bindReply((byte) 92, MyIP, MyPort);
			ssock.close();
			return;
		}

		System.out.println("BIND at : <" + MyIP.toString() + ":" + MyPort + ">");
		bindReply((byte) 90, MyIP, MyPort);

		Socket socket = null;

		while (socket == null) {
			if (this.connection.readClientData() >= 0) {
				System.out.println("BIND - Client connection closed");
				ssock.close();
				return;
			}

			try {
				socket = ssock.accept();
				socket.setSoTimeout(Constants.DEFAULT_PROXY_TIMEOUT);
			} catch (InterruptedIOException e) {
				if (socket != null)
					socket.close();
			}
			Thread.yield();
		}

		/*
		 * if( socket.getInetAddress() != m_m_ServerIP ) { BIND_Reply( (byte)91,
		 * socket.getInetAddress(), socket.getPort() ); Log.Warning( m_Server,
		 * "BIND Accepts different IP/P" ); m_Server.Close(); return; }
		 */

		this.serverAdress = socket.getInetAddress();
		this.serverPort = socket.getPort();

		bindReply((byte) 90, socket.getInetAddress(), socket.getPort());

		this.connection.setServer(socket);
		this.connection.prepareServer();

		System.out.println("BIND Connection from " + Utils.getSocketInfo(connection.getServer()));
		ssock.close();
	}

	public String replyName(byte code) {

		switch (code) {
		case 0:
			return "SUCCESS";
		case 1:
			return "General SOCKS Server failure";
		case 2:
			return "Connection not allowed by ruleset";
		case 3:
			return "Network Unreachable";
		case 4:
			return "HOST Unreachable";
		case 5:
			return "Connection Refused";
		case 6:
			return "TTL Expired";
		case 7:
			return "Command not supported";
		case 8:
			return "Address Type not Supported";
		case 9:
			return "to 0xFF UnAssigned";

		case 90:
			return "Request GRANTED";
		case 91:
			return "Request REJECTED or FAILED";
		case 92:
			return "Request REJECTED - SOCKS server can't connect to Identd on the client";
		case 93:
			return "Request REJECTED - Client and Identd report diff user-ID";

		default:
			return "Unknown Command";
		}
	}

	public void bindReply(byte ReplyCode, InetAddress IA, int PT) throws IOException {
		byte IP[] = { 0, 0, 0, 0 };

		System.out.println("Reply to Client : \"" + replyName(ReplyCode) + "\"");

		byte[] REPLY = new byte[8];
		if (IA != null)
			IP = IA.getAddress();

		REPLY[0] = 0;
		REPLY[1] = ReplyCode;
		REPLY[2] = (byte) ((PT & 0xFF00) >> 8);
		REPLY[3] = (byte) (PT & 0x00FF);
		REPLY[4] = IP[0];
		REPLY[5] = IP[1];
		REPLY[6] = IP[2];
		REPLY[7] = IP[3];

		if (this.connection.isActive()) {
			this.connection.sendToClient(REPLY);
		} else {
			System.out.println("Closed BIND Client Connection");
		}
	}

	public InetAddress resolveExternalLocalIP() {

		InetAddress adress = null;

		if (this.extLocalAdress != null) {
			Socket sct = null;
			try {
				sct = new Socket(this.extLocalAdress, connection.getServer().getPort());
				adress = sct.getLocalAddress();
				sct.close();
				return this.extLocalAdress;
			} catch (IOException e) {
				connection.print("WARNING !!! THE LOCAL IP ADDRESS WAS CHANGED !");
			}
		}

		String[] hosts = { "www.sun.com", "www.microsoft.com", "www.aol.com", "www.google.de", "www.yahoo.com" };

		for (int i = 0; i < hosts.length; i++) {
			try {
				Socket sct = new Socket(InetAddress.getByName(hosts[i]), 80);
				adress = sct.getLocalAddress();
				sct.close();
				break;
			} catch (Exception e) { // IP == null
				connection.print("Error in BIND() - BIND reip Failed at " + i);
			}
		}

		this.extLocalAdress = adress;
		return adress;
	}

	public void connect() {
		try {
			this.connection.connectToServer(serverAdress, serverPort);
			replyCommand(getSuccessCode());
		} catch (Exception e) {
			replyCommand(getFailCode());
			e.printStackTrace();
		}
	}

	public void replyCommand(byte replyCode) {
		byte[] REPLY = new byte[8];
		REPLY[0] = 0;
		REPLY[1] = replyCode;
		REPLY[2] = DST_Port[0];
		REPLY[3] = DST_Port[1];
		REPLY[4] = DST_IP[0];
		REPLY[5] = DST_IP[1];
		REPLY[6] = DST_IP[2];
		REPLY[7] = DST_IP[3];

		this.connection.sendToClient(REPLY);
	}

	public void udp() throws IOException {
		connection.print("Error - Socks 4 don't support UDP Association!");
		connection.print("Check your Software please...");
		refuseCommand(getFailCode()); // SOCKS4 don't support UDP
	}

	public void calculateUserID() {
		String s = UID + " ";
		UserID = s.getBytes();
		UserID[UserID.length - 1] = 0x00;
	}

	public boolean calculateAddress() {
		this.serverAdress = Utils.calcInetAddress(DST_IP);
		this.serverPort = Utils.calcPort(DST_Port[0], DST_Port[1]);
		return ((this.serverAdress != null) && (this.serverPort > 0));
	}

}
