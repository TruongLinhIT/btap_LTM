package browser;

import java.util.HashMap;
import java.util.Map;

public class HttpRequest {
    private String url;
    private String method; // GET, HEAD, POST
    private Map<String, String> headers;
    private byte[] body;
    private String contentType;

    public HttpRequest(String url, String method) {
        this.url = url;
        this.method = method.toUpperCase();
        this.headers = new HashMap<>();
        this.headers.put("User-Agent", "LTM-WebBrowser/1.0 (Chrome/CocCoc-like)");
        this.headers.put("Accept", "*/*");
        this.headers.put("Connection", "close");
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method.toUpperCase();
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void addHeader(String key, String value) {
        this.headers.put(key, value);
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
        if (contentType != null) {
            headers.put("Content-Type", contentType);
        }
    }
}
