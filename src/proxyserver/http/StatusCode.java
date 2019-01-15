package proxyserver.http;

/**
 * Gibt die einzelen Status Codes für HTTP an
 * 
 * @author Felix Obenaus (Matrikelnr. 205637)
 * @author Abdulhamed Chribati (Matrikelnr. 206317)
 */
public enum StatusCode {
OK(200,"OK"),
NO_CONTENT(204,"No Content"),
NOT_MODIFIED(304,"Not Modified"),
BAD_REQUEST(400,"Bad Request"),
FORBIDDEN(403,"Forbidden"),
NOT_FOUND(404,"Not Found"),
INTERNAL_SERVER_ERROR(500,"Internal Server Error"),
NOT_IMPLEMENTED(501,"Not Implemented");
	
	private int code;
	private String message;
	
	private StatusCode(int code,String message) {
		this.code=code;
		this.message=message;
	}
	
	public int getCode() {return this.code;}
	public String getMessage() {return getCode()+" "+this.message;}
}
