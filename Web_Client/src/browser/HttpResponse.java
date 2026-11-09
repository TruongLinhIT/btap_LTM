package browser;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpResponse {
    private int statusCode;
    private String statusMessage;
    private Map<String, String> headers = new LinkedHashMap<>();
    private byte[] bodyBytes;
    private String contentType = "text/plain";
    private long contentLength = -1;
    private long responseTimeMs = 0;
    private String resolvedIp = "";
    private String requestUrl = "";
    private String requestMethod = "GET";

    public HttpResponse() {
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
        if ("content-type".equalsIgnoreCase(key)) {
            this.contentType = value;
        } else if ("content-length".equalsIgnoreCase(key)) {
            try {
                this.contentLength = Long.parseLong(value.trim());
            } catch (NumberFormatException ignored) {}
        }
    }

    public byte[] getBodyBytes() {
        return bodyBytes != null ? bodyBytes : new byte[0];
    }

    public void setBodyBytes(byte[] bodyBytes) {
        this.bodyBytes = bodyBytes;
        if (bodyBytes != null && contentLength <= 0) {
            contentLength = bodyBytes.length;
        }
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getContentLength() {
        return contentLength;
    }

    public void setContentLength(long contentLength) {
        this.contentLength = contentLength;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public String getResolvedIp() {
        return resolvedIp;
    }

    public void setResolvedIp(String resolvedIp) {
        this.resolvedIp = resolvedIp;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public boolean isHtml() {
        if (contentType == null) return false;
        String lower = contentType.toLowerCase();
        return lower.contains("text/html") || lower.contains("xhtml");
    }

    public boolean isImage() {
        if (contentType == null) return false;
        String lower = contentType.toLowerCase();
        return lower.startsWith("image/");
    }

    public boolean isAudioOrVideoOrPdf() {
        if (contentType == null) return false;
        String lower = contentType.toLowerCase();
        return lower.startsWith("audio/") || lower.startsWith("video/") || lower.contains("pdf");
    }

    public String getStringBody() {
        if (bodyBytes == null || bodyBytes.length == 0) {
            return "";
        }
        Charset charset = StandardCharsets.UTF_8;
        if (contentType != null && contentType.toLowerCase().contains("charset=")) {
            try {
                String csName = contentType.substring(contentType.toLowerCase().indexOf("charset=") + 8).trim();
                if (csName.contains(";")) {
                    csName = csName.substring(0, csName.indexOf(";")).trim();
                }
                csName = csName.replace("\"", "").replace("'", "");
                charset = Charset.forName(csName);
            } catch (Exception ignored) {}
        }
        return new String(bodyBytes, charset);
    }

    public String getRawHeadersString() {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusMessage != null ? statusMessage : "").append("\n");
        for (Map.Entry<String, String> e : headers.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append("\n");
        }
        return sb.toString();
    }
}
