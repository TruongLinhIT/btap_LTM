package browser;

import java.util.LinkedHashMap;
import java.util.Map;

public class HttpRequest {
    private String url;
    private String method; // GET, HEAD, POST
    private Map<String, String> headers;
    private byte[] body;
    private String contentType;

    public HttpRequest(String url, String method) {
        this.url = url;
        this.method = method != null ? method.toUpperCase() : "GET";
        this.headers = new LinkedHashMap<>();
        this.headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) LTM-GoogleChromeClient/1.1");
        this.headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/*,*/*;q=0.8");
        this.headers.put("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7");
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
        if (contentType != null && !contentType.isEmpty()) {
            headers.put("Content-Type", contentType);
        }
    }
}
