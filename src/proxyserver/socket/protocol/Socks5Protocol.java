package proxyserver.socket.protocol;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

import lombok.Getter;
import proxyserver.socket.Constants;
import proxyserver.socket.ProxyConnection;
import proxyserver.socket.Utils;

public class Socks5Protocol extends Socks4Protocol {

	static final int ADDR_Size[] = { -1, // '00' No such AType
			4, // '01' IP v4 - 4Bytes
			-1, // '02' No such AType
			-1, // '03' First Byte is Len bc Adress is a Domain
			16 // '04' IP v6 - 16bytes
	};

	protected DatagramSocket DGSocket = null;
	protected DatagramPacket DGPack = null;

	private InetAddress UDP_IA = null;
	private int UDP_port = 0;

	public byte RSV; // Reserved.Must be'00'
	public byte ATYP; // Address Type (IP V4 address: X'01', DOMAINNAME: X'03',IP V6 address: X'04')


	@Getter
	private byte successCode = 00;
	@Getter
	private byte failCode = 04;
	
	public Socks5Protocol(ProxyConnection connection) {
		super(connection);
		DST_IP = new byte[Constants.MAX_ADDR_LEN];
	}

	@Override
	public void authenticate(byte SOCKS_Ver) throws Exception {
		super.authenticate(SOCKS_Ver); // set Version

		if (super.socksVersion == Constants.SOCKS5_Version) {
			if (!checkAuthenticate()) {
				refuceAuthenticate();
				throw new Exception("Authentication failed");
			}
			acceptAuthenticate();
		} else {
			System.err.println("The Version isn't correct " + super.socksVersion);
		}
	}

	public void acceptAuthenticate() {
		super.connection.sendToClient(Constants.SRE_Accept);
	}

	public void refuceAuthenticate() {
		super.connection.sendToClient(Constants.SRE_Refuse);
	}

	public boolean checkAuthenticate() {
		byte n_methods = getByte();
		String methods = "";
		for (int i = 0; i < n_methods; i++) {
			methods += ",-" + getByte() + '-';
		}
		return ((methods.indexOf("-0-") != -1) || (methods.indexOf("-00-") != -1));
	}

	public boolean calculateAddress() {
		serverAdress = calcInetAddress(ATYP, DST_IP);
		serverPort = Utils.calcPort(DST_Port[0], DST_Port[1]);

		clientAdress = connection.getClient().getInetAddress();
		clientPort = connection.getClient().getPort();

		return ((serverAdress != null) && (serverPort >= 0));
	}

	public InetAddress calcInetAddress(byte AType, byte[] addr) {
		InetAddress IA = null;

		switch (AType) {
		// Version IP 4
		case 0x01:
			IA = Utils.calcInetAddress(addr);
			break;
		// Version IP DOMAIN NAME
		case 0x03:
			if (addr[0] <= 0) {
				System.err.println("SOCKS 5 - calcInetAddress() : BAD IP in command - size : " + addr[0]);
				return null;
			}
			String sIA = "";
			for (int i = 1; i <= addr[0]; i++) {
				sIA += (char) addr[i];
			}
			try {
				IA = InetAddress.getByName(sIA);
			} catch (UnknownHostException e) {
				return null;
			}
			break;
		case 0x04:
			try {
				IA = InetAddress.getByAddress(DST_IP);
			} catch (UnknownHostException e) {
				e.printStackTrace();
				IA = null;
			}
			break;
		default:
			return null;
		}
		return IA;
	}

	public byte getClientCommand() throws Exception {
		getByte(); // VERSION
		byte socksCommand = getByte();
		this.RSV = getByte();
		this.ATYP = getByte();
		
		int Addr_Len = ADDR_Size[ATYP];
		super.DST_IP[0] = getByte();
		if (ATYP == 0x03) {
			Addr_Len = super.DST_IP[0] + 1;
		}

		for (int i = 1; i < Addr_Len; i++) {
			super.DST_IP[i] = getByte();
		}

		DST_Port[0] = getByte();
		DST_Port[1] = getByte();
		if ((socksCommand < Constants.SC_CONNECT) || (socksCommand > Constants.SC_UDP)) {
			System.err.println("SOCKS 5 - GetClientCommand() - Unsupported Command");
			refuseCommand((byte) 0x07);
			throw new Exception("SOCKS 5 - Unsupported Command: \"" + socksCommand + "\"");
		} else if ((ATYP > 0x04) || (ATYP <= 0)) {
			System.err.println("SOCKS 5 - GetClientCommand() - Unsupported Address Type: " + ATYP);
			refuseCommand((byte) 0x08);
			throw new Exception("SOCKS 5 - Unsupported Address Type: " + ATYP);
		} else if (!calculateAddress()) { // Gets the IP Address
			refuseCommand((byte) 0x04);// Host Not Exists...
			throw new Exception("SOCKS 5 - Unknown Host/IP address '" + serverAdress.toString() + "'");
		}

		System.out.println("Adress: "+serverAdress.getHostAddress()+":"+serverPort);
		
		return socksCommand;
	}

	public void replyCommand(byte replyCode) {
		System.out.println("SOCKS 5 - Reply to Client \"" + replyName(replyCode) + "\"");

		int pt = 0;

		byte[] REPLY = new byte[10];
		byte IP[] = new byte[4];

		if (connection.getServer() != null) {
			// IA = connection.m_ServerSocket.getInetAddress();
			// DN = IA.toString();
			pt = connection.getServer().getLocalPort();
		} else {
			IP[0] = 0;
			IP[1] = 0;
			IP[2] = 0;
			IP[3] = 0;
			pt = 0;
		}

		REPLY[0] = Constants.SOCKS5_Version;
		REPLY[1] = replyCode; // Reply Code;
		REPLY[2] = 0x00; // Reserved '00'
		REPLY[3] = 0x01; // DOMAIN NAME Type IP ver.4
		REPLY[4] = IP[0];
		REPLY[5] = IP[1];
		REPLY[6] = IP[2];
		REPLY[7] = IP[3];
		REPLY[8] = (byte) ((pt & 0xFF00) >> 8);// Port High
		REPLY[9] = (byte) (pt & 0x00FF); // Port Low

		connection.sendToClient(REPLY);// BND.PORT
	} // Reply_Command()

	public void bindReply(byte replyCode, InetAddress IA, int PT) {
		byte IP[] = { 0, 0, 0, 0 };

		System.out.println("BIND Reply to Client \"" + replyName(replyCode) + "\"");

		byte[] REPLY = new byte[10];
		if (IA != null)
			IP = IA.getAddress();

		REPLY[0] = Constants.SOCKS5_Version;
		REPLY[1] = (byte) ((int) replyCode - 90); // Reply Code;
		REPLY[2] = 0x00; // Reserved '00'
		REPLY[3] = 0x01; // IP ver.4 Type
		REPLY[4] = IP[0];
		REPLY[5] = IP[1];
		REPLY[6] = IP[2];
		REPLY[7] = IP[3];
		REPLY[8] = (byte) ((PT & 0xFF00) >> 8);
		REPLY[9] = (byte) (PT & 0x00FF);

		if (connection.isActive()) {
			connection.sendToClient(REPLY);
		} else {
			System.out.println("BIND - Closed Client Connection");
		}
	}

	public void udpReply(byte replyCode, InetAddress IA, int PT) throws IOException {

		System.out.println("Reply to Client \"" + replyName(replyCode) + "\"");

		if (connection.getClient() == null) {
			System.out.println("Error in UDP_Reply() - Client socket is NULL");
		}
		byte[] IP = IA.getAddress();

		byte[] REPLY = new byte[10];

		REPLY[0] = Constants.SOCKS5_Version;
		REPLY[1] = replyCode; // Reply Code;
		REPLY[2] = 0x00; // Reserved '00'
		REPLY[3] = 0x01; // Address Type IP v4
		REPLY[4] = IP[0];
		REPLY[5] = IP[1];
		REPLY[6] = IP[2];
		REPLY[7] = IP[3];

		REPLY[8] = (byte) ((PT & 0xFF00) >> 8);// Port High
		REPLY[9] = (byte) (PT & 0x00FF); // Port Low

		connection.sendToClient(REPLY);// BND.PORT
	}

	public void udp() throws IOException {

		// Connect to the Remote Host

		try {
			DGSocket = new DatagramSocket();
			initUdpInOut();
		} catch (IOException e) {
			refuseCommand((byte) 0x05); // Connection Refused
			throw new IOException("Connection Refused - FAILED TO INITIALIZE UDP Association.");
		}

		InetAddress MyIP = connection.getClient().getLocalAddress();
		int MyPort = DGSocket.getLocalPort();

		// Return response to the Client
		// Code '00' - Connection Succeeded,
		// IP/Port where Server will listen
		udpReply((byte) 0, MyIP, MyPort);

		System.out.println("UDP Listen at: <" + MyIP.toString() + ":" + MyPort + ">");

		while (connection.readClientData() >= 0) {
			processUdp();
			Thread.yield();
		}
		System.out.println("UDP - Closed TCP Master of UDP Association");
	} // UDP ...

	private void initUdpInOut() throws IOException {

		DGSocket.setSoTimeout(Constants.DEFAULT_PROXY_TIMEOUT);

		connection.m_Buffer = new byte[Constants.DEFAULT_BUF_SIZE];

		DGPack = new DatagramPacket(connection.m_Buffer, Constants.DEFAULT_BUF_SIZE);
	}

	private byte[] addDgpHead(byte[] buffer) {

		// int bl = Buffer.length;
		byte IABuf[] = DGPack.getAddress().getAddress();
		int DGport = DGPack.getPort();
		int HeaderLen = 6 + IABuf.length;
		int DataLen = DGPack.getLength();
		int NewPackLen = HeaderLen + DataLen;

		byte UB[] = new byte[NewPackLen];

		UB[0] = (byte) 0x00; // Reserved 0x00
		UB[1] = (byte) 0x00; // Reserved 0x00
		UB[2] = (byte) 0x00; // FRAG '00' - Standalone DataGram
		UB[3] = (byte) 0x01; // Address Type -->'01'-IP v4
		System.arraycopy(IABuf, 0, UB, 4, IABuf.length);
		UB[4 + IABuf.length] = (byte) ((DGport >> 8) & 0xFF);
		UB[5 + IABuf.length] = (byte) ((DGport) & 0xFF);
		System.arraycopy(buffer, 0, UB, 6 + IABuf.length, DataLen);
		System.arraycopy(UB, 0, buffer, 0, NewPackLen);

		return UB;

	}

	private byte[] clearDgpHead(byte[] buffer) {
		int IAlen = 0;
		// int bl = Buffer.length;
		int p = 4; // First byte of IP Address

		byte AType = buffer[3]; // IP Address Type
		switch (AType) {
		case 0x01:
			IAlen = 4;
			break;
		case 0x03:
			IAlen = buffer[p] + 1;
			break; // One for Size Byte
		default:
			System.out.println("Error in ClearDGPhead() - Invalid Destination IP Addres type " + AType);
			return null;
		}

		byte IABuf[] = new byte[IAlen];
		System.arraycopy(buffer, p, IABuf, 0, IAlen);
		p += IAlen;

		UDP_IA = calcInetAddress(AType, IABuf);
		UDP_port = Utils.calcPort(buffer[p++], buffer[p++]);

		if (UDP_IA == null) {
			System.out.println("Error in ClearDGPHead() - Invalid UDP dest IP address: NULL");
			return null;
		}

		int DataLen = DGPack.getLength();
		DataLen -= p; // <p> is length of UDP Header

		byte UB[] = new byte[DataLen];
		System.arraycopy(buffer, p, UB, 0, DataLen);
		System.arraycopy(UB, 0, buffer, 0, DataLen);

		return UB;

	}

	protected void udpSend(DatagramPacket DGP) {

		if (DGP == null)
			return;

		String LogString = DGP.getAddress() + ":" + DGP.getPort() + "> : " + DGP.getLength() + " bytes";
		try {
			DGSocket.send(DGP);
		} catch (IOException e) {
			System.out.println("Error in ProcessUDPClient() - Failed to Send DGP to " + LogString);
			return;
		}
	}

	public void processUdp() {

		// Trying to Receive DataGram
		try {
			DGSocket.receive(DGPack);
		} catch (InterruptedIOException e) {
			return; // Time Out
		} catch (IOException e) {
			System.out.println("Error in ProcessUDP() - " + e.toString());
			return;
		}

		if (clientAdress.equals(DGPack.getAddress())) {

			processUdpClient();
		} else {

			processUdpRemote();
		}

		try {
			initUdpInOut(); // Clean DGPack & Buffer
		} catch (IOException e) {
			System.out.println("IOError in Init_UDP_IO() - " + e.toString());
			connection.close();
		}
	}

	/**
	 * Processing Client's datagram This Method must be called only from
	 * <ProcessUDP()>
	 */
	public void processUdpClient() {

		clientPort = DGPack.getPort();

		// Also calculates UDP_IA & UDP_port ...
		byte[] Buf = clearDgpHead(DGPack.getData());
		if (Buf == null)
			return;

		if (Buf.length <= 0)
			return;

		if (UDP_IA == null) {
			System.out.println("Error in ProcessUDPClient() - Invalid Destination IP - NULL");
			return;
		}
		if (UDP_port == 0) {
			System.out.println("Error in ProcessUDPClient() - Invalid Destination Port - 0");
			return;
		}

		if (serverAdress != UDP_IA || serverPort != UDP_port) {
			serverAdress = UDP_IA;
			serverPort = UDP_port;
		}

		System.out.println("Datagram : " + Buf.length + " bytes : " + Utils.getSocketInfo(DGPack) + " >> <"
				+ Utils.iP2Str(serverAdress) + ":" + serverPort + ">");

		DatagramPacket DGPSend = new DatagramPacket(Buf, Buf.length, UDP_IA, UDP_port);

		udpSend(DGPSend);
	}

	public void processUdpRemote() {

		System.out.println("Datagram : " + DGPack.getLength() + " bytes : " + "<" + Utils.iP2Str(clientAdress) + ":"
				+ clientPort + "> << " + Utils.getSocketInfo(DGPack));

		// This Method must be CALL only from <ProcessUDP()>
		// ProcessUDP() Reads a Datagram packet <DGPack>

		InetAddress DGP_IP = DGPack.getAddress();
		int DGP_Port = DGPack.getPort();

		byte[] Buf;

		Buf = addDgpHead(connection.m_Buffer);
		if (Buf == null)
			return;

		// SendTo Client
		DatagramPacket DGPSend = new DatagramPacket(Buf, Buf.length, clientAdress, clientPort);
		udpSend(DGPSend);

		if (DGP_IP != UDP_IA || DGP_Port != UDP_port) {
			serverAdress = DGP_IP;
			serverPort = DGP_Port;
		}
	}

}
