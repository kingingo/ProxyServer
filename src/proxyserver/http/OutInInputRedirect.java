package proxyserver.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import proxyserver.socket.Constants;

class OutInInputRedirect {
    public final transient InputStream is;
    public final transient OutputStream os;
    public final BufferedReader reader;

    public OutInInputRedirect() throws IOException {
        this(Constants.DEFAULT_BUF_SIZE);
    }

    public OutInInputRedirect(int size) throws IOException {
        PipedInputStream is = new PipedInputStream(size);
        PipedOutputStream os = new PipedOutputStream(is);

        this.is = is;
        this.os = os;
        this.reader = new BufferedReader(new InputStreamReader(is));
    }
    
    public void close() {
    	try {
			is.close();
		} catch (IOException e) {
		}
    	try {
			os.close();
		} catch (IOException e) {
		}
    	try {
			reader.close();
		} catch (IOException e) {
		}
    }
}