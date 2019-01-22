package proxyserver.http.client;

import proxyserver.http.Header;

public class ClientHeader extends Header{
	
	public ClientHeader(String... s) {
		super(s);
	}

	/**
	 * Faegt die Kopfzeile mit einen StatusCode hinzu mit der HTTP Version 1.0
	 */
	public Header addHeadline(RequestMethod request,String URL) {
		StringBuilder builder = new StringBuilder();
		
		builder.append(request.name()).append(" ");
		builder.append(URL).append(" ");
		builder.append("HTTP/1.0");
		
		super.addHeadline(builder.toString());
		return this;
	}

	public String containsHeadline(String line) {
		if(line.contains("HTTP/")) {
			String type = "";
			if(line.contains("POST")) type="POST";
			else if(line.contains("GET")) type="GET";
			else if(line.contains("HEAD")) type="HEAD";
			
			if(!type.isEmpty()) {
				int index = line.indexOf(type);
				
				if(index!=-1) {
					return line.substring(index, line.length());
				}
			}
		}
		return "";
	}
}
