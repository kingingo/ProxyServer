package proxyserver.http;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class HttpHandler extends Thread {
	private static int counter = 0;
	
	private OutInInputRedirect client;
	private OutInInputRedirect server;
	private int ID;
	private boolean running = false;

	public HttpHandler() {
		this.ID = counter++;
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
	
	public void close() {
		this.running=false;
	}

	public void run() {
		try {
			this.running=true;
			Header header;
			int content_length;
			
			while(this.running) {
				//Client read
				header = Header.read(this.client.reader, Type.CLIENT);
				System.out.println("["+this.ID+"]: Client-Header: \n"+header);
				
				content_length = header.getInt("Content-Length");
				if(content_length!=-1)this.client.reader.skip(content_length);
				//---
				
				//Server read
				header = Header.read(this.server.reader, Type.SERVER);
				System.out.println("["+this.ID+"]: Server-Header: \n"+header);
				
				content_length = header.getInt("Content-Length");
				if(content_length!=-1)this.server.reader.skip(content_length);
				//---

				Thread.currentThread();
				Thread.yield();
			}
		} catch (IOException e) {
		}finally {
			close();
			this.client.close();
			this.server.close();
		}

	}

	public void writeTo(OutputStream os, byte[] bytes, int offset, int length) {
		try {
			for (int i = 0; i < length; i++) {
				os.write(bytes[i]+offset);
			}
			os.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void writeToServer(byte[] bytes, int offset, int length) {
		writeTo(this.server.os, bytes,offset,length);
	}

	public void writeToClient(byte[] bytes, int offset, int length) {
		writeTo(this.client.os, bytes,offset,length);
	}
}
