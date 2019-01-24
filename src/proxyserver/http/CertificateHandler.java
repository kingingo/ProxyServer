package proxyserver.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collection;
import sun.security.ec.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.crypto.Cipher;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class CertificateHandler {
	private static CertificateHandler instance = null;

	public static CertificateHandler getInstance() {
		if (instance == null)
			instance = new CertificateHandler();
		return instance;
	}

	public HashMap<String, X509Certificate> certificates = new HashMap<>();

	private CertificateHandler() {
		loadCertificates();
	}

	private void loadCertificates() {
		File folder = new File("certificates");
		if (!folder.exists())
			folder.mkdirs();

		if (folder.isDirectory()) {
			for (File file : folder.listFiles()) {
				try {
					loadCertification(file);
				} catch (Exception e) {
				}
			}
			System.out.println("[CertificateHandler]: loaded " + this.certificates.size() + " certificates.");

		} else
			throw new NullPointerException(
					"Couldn't load the certifications folder doesn't exist perhaps " + folder.getAbsolutePath());
	}

	private void loadCertification(File file) {
		if (!file.exists())
			throw new NullPointerException("This file doesn't exist. " + file.getAbsolutePath());
		if (!file.isFile())
			throw new NullPointerException("This isn't a file " + file.getAbsolutePath());
		if (!file.getName().endsWith(".crt"))
			throw new NullPointerException("This file isn't an certification file. " + file.getAbsolutePath());

		InputStream in = null;
		try {
			in = new FileInputStream(file);
			CertificateFactory factory = CertificateFactory.getInstance("X.509");
			X509Certificate cert = (X509Certificate) factory.generateCertificate(in);

			Collection<List<?>> list = cert.getSubjectAlternativeNames();

			Iterator<List<?>> it = list.iterator();
			List<?> l = it.next();
			String url = String.valueOf(l.get(1));

			this.certificates.put(url, cert);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				in.close();
			} catch (IOException e) {
			}
		}

	}

	private byte[] crypt(byte[] bytes, int type, String url) throws Exception {
		X509Certificate cert;
		if ((cert = this.certificates.get(url.toLowerCase())) != null) {
			PublicKey pk = cert.getPublicKey();
			Cipher cipher = Cipher.getInstance(cert.getSigAlgName());
			cipher.init(type, pk);
			return cipher.doFinal(bytes);
		}
		System.err.println("No Certicate found for " + url);
		return bytes;
	}

	public byte[] dencrypt(byte[] bytes, String url) throws Exception {
		return crypt(bytes, Cipher.DECRYPT_MODE, url);
	}

	public byte[] encrypt(byte[] bytes, String url) throws Exception {
		return crypt(bytes, Cipher.ENCRYPT_MODE, url);
	}
}
