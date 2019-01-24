package proxyserver.http;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

import proxyserver.http.client.RequestMethod;

/**
 * Sammelt alle Informationen zum Request/Response Header
 */
public class Header {
	// Erstellt einen leeren Header ohne irgendeinen Inhalt
	public static Header createEmpty(Type type) {
		return new Header(type);
	}

	private static String containsHeadline(Type type, String line) {
		if (Type.SERVER == type) {
			int index = line.indexOf("HTTP/");

			if (index != -1) {
				return line.substring(index, line.length());
			}
		} else {
			if (line.contains("HTTP/")) {
				String method = "";
				if (line.contains("POST"))
					method = "POST";
				else if (line.contains("GET"))
					method = "GET";
				else if (line.contains("HEAD"))
					method = "HEAD";

				if (!method.isEmpty()) {
					int index = line.indexOf(method);

					if (index != -1) {
						return line.substring(index, line.length());
					}
				}
			}
		}
		return "";
	}

	/**
	 * Liest den InputStream (in) aus und fügt die Informationen in einen Header
	 */
	public static Header read(BufferedReader in, Type type) throws IOException {
		// Erstellen eines leeren Headers
		Header header = createEmpty(type);

		// Liest die einzelnen Zeilen des Headers aus und fügt sie hinzu
		String line;
		boolean found = false;
		while ((line = in.readLine()) != null && !line.isEmpty()) {
			if (!found) {
				line = containsHeadline(type, line);
				if (line.isEmpty())
					continue;

				found = true;
			}
			header.add(line);
		}

		return header;
	}

	/**
	 * (key,value) => key: value (z.b Server: Java-Webserver, key=Server &
	 * value=Java-Webserver)
	 */
	private HashMap<String, String> header = new HashMap<>();
	// head beinhaltet den Kopf des Headers also z.b (REQUEST) head = GET / HTTP/1.1
	// oder (RESPONSE) head = HTTP/1.0 200 OK
	private String head = "";
	private Type type;

	/**
	 * Der Konstruktor ist private weil ein Header Object nur Ueber die static
	 * Methoden Header.create() oder Header.createEmpty() erstellt werden soll
	 * 
	 * strings beinhaltet einige Optionen die zum Header direkt hinzugefügt werden
	 * sollen
	 */
	protected Header(Type type, String... strings) {
		this.type=type;
		for (String s : strings)
			add(s);
	}

	/**
	 * Faegt die Kopfzeile mit einen StatusCode hinzu mit der HTTP Version 1.0
	 */
	public Header addHeadline(StatusCode code) {
		addHeadline("HTTP/1.0 " + code.getMessage());
		return this;
	}

	// Um auf Inhalte vom Header zu zugreifen, über den KEY
	public String get(String name) {
		return this.header.get(name);
	}

	public int getInt(String name) {
		String value = get(name);
		if (value != null)
			return Integer.valueOf(value);
		return -1;
	}

	public Header addHeadline(RequestMethod request, String URL) {
		StringBuilder builder = new StringBuilder();

		builder.append(request.name()).append(" ");
		builder.append(URL).append(" ");
		builder.append("HTTP/1.0");

		addHeadline(builder.toString());
		return this;
	}

	/**
	 * Loescht inhalte im Header und gibt zurück ob diese gefunden wurden oder nicht
	 * vorhanden waren.
	 */
	public boolean remove(String name) {
		return this.header.remove(name) != null;
	}

	/**
	 * FÜgt @line zum Header hinzu dabei wird unterschieden ob line die Kopfzeile
	 * ist dann wird head=line gesetzt oder ob line eine Option (Aufbau line = Key:
	 * Value) ist die gesetzt werden soll
	 */
	public Header add(String line) {
		// Überprüft ob line die Kopfzeile ist
		if (line.startsWith("HTTP") || line.startsWith("GET") || line.startsWith("POST") || line.startsWith("HEAD")) {
			this.head = line;
			return this;
		}

		// Gibt den Index an wo sich der Charakter ':' im String line befindet
		int index = line.indexOf(':');
		// Falls der index = -1 ist dann stimmt das Format (key:value) nicht und es wird
		// eine Exception ausgegeben
		if (index == -1) {
			throw new IllegalArgumentException("Unexpected header: " + line);
		}

		/*
		 * Trennt line in key und value auf an dem Charakter ':' und übermittelt diese
		 * beiden Strings zu einer weiteren Methode add(String,String) trim() entfernt
		 * leerzeichen im String
		 */
		add(line.substring(0, index).trim(), line.substring(index + 1));
		return this;
	}

	// Fügt die Option zum Header hinzu
	public Header add(String name, String value) {
		// Entfernt sie erst falls schon eine Angabe vorhanden ist
		remove(name);
		// Fügt die neue Angabe hinzu
		this.header.put(name, value.trim());
		return this;
	}

	/**
	 * Gibt angegebene Version im Header zurück als Float
	 */
	public float getVersion() {
		// Falls der head String leer ist wurde der Header nicht richtig geladen ->
		// NullpointerException
		if (!this.head.isEmpty()) {
			/*
			 * Spaltet die Kopfzeile in mehere String bei jedem Leerzeichen z.b
			 * "GET / HTTP/1.0" zu token[0]="GET", token[1]="/" & token[2]="HTTP/1.0"
			 */
			String[] tokens = this.head.split(" ");

			// Geht alle tokens durch bis der richtige gefunden wurde
			for (String token : tokens) {
				// Überprüfung ob in diesem Token die Version angegeben ist
				if (token.contains("HTTP")) {
					// Gibt den Index vom Slash (/) im String zurück
					int index = token.indexOf("/");
					// Falls index=-1 dann wurde kein Slash gefunden
					if (index == -1) {
						throw new IllegalArgumentException("Unexpected head " + token);
					}
					// Schneidet die Version aus dem String raus und fügt es in einen Float ein
					// z.b HTTP/1.0 => 1.0F
					return Float.valueOf(token.substring(index + 1, token.length()));
				}
			}
		}

		throw new NullPointerException("The Version couldn't be found!");
	}

	/**
	 * Faegt die Kopfzeile mit einen StatusCode hinzu mit der HTTP Version 1.0
	 */
	protected Header addHeadline(String head) {
		this.head = head;
		return this;
	}

	// Gibt die Kopfzeile zurueck
	public String getHead() {
		return this.head;
	}

	/**
	 * TRUE => Header ist leer also keine Kopfzeile und keine weiteren Angaben FALSE
	 * => Header hat entweder eine Kopfzeile oder weitere Angaben oder beides
	 */
	public boolean isEmpty() {
		return this.header.isEmpty() && this.head.isEmpty();
	}

	/**
	 * Wandelt alle Angaben in einen String um.
	 */
	public String toString() {
		// Um einen String Aufzubauen
		StringBuilder builder = new StringBuilder();
		// Fügt die Kopfzeile hinzu und startet eine neue Zeile (\n)
		builder.append(this.head).append("\n");
		// Geht alle Angaben durch
		for (String name : this.header.keySet()) {
			// Fügt die Angaben zum String hinzu mit \n für eine neue Zeile
			// Format NAME: VALUE\n
			builder.append(name).append(": ").append(this.header.get(name)).append("\r\n");
		}
		// Erstellt den kompletten String
		return builder.toString();
	}

	// Sendet alle Angaben zum Client
	public Header write(DataOutputStream out) {
		try {
			// Schreibt den Header in den Stream
			out.writeBytes(toString());
			// Eine Leere Zeile nach RFC 1945
			out.writeBytes("\r\n");
			// Sendet alles zusammen zum Client
			out.flush();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return this;
	}
}
