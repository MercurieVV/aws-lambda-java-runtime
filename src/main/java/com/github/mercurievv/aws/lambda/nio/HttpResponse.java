package com.github.mercurievv.aws.lambda.nio;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: Victor Mercurievv
 * Date: 4/20/2019
 * Time: 1:11 PM
 * Contacts: email: mercurievvss@gmail.com Skype: 'grobokopytoff' or 'mercurievv'
 */
public class HttpResponse {
    private static final Pattern statusPattern = Pattern.compile("\\A(HTTP/1\\.[01]+) ([0-9]+) (\\S+?)\\r?\\n");
    private static final Pattern headerPattern = Pattern.compile("\\A([a-zA-Z0-9_-]+)[ ]*:[ ]*([^\\r\\n]+?)\\r?\\n", Pattern.UNIX_LINES);

    private String protocol;
    private int status;
    private String message;
    private HttpHeaders headers;

    public HttpResponse() {
        this.headers = new HttpHeaders();
    }

    public static HttpResponseParserResult parse(String buf) {
        buf = "" + buf;
        Matcher matcher = statusPattern.matcher(buf);
        HttpResponse httpResponse = new HttpResponse();

        if (matcher.find()) {
            buf = buf.substring(matcher.group(0).length());

            httpResponse.protocol = matcher.group(1);
            httpResponse.status = Integer.parseInt(matcher.group(2));
            httpResponse.message = matcher.group(3);
        } else {
            return HttpResponseParserResult.partial();
        }

        while (true) {
            Matcher matcher2 = headerPattern.matcher(buf);
            if (matcher2.find()) {
                String key = matcher2.group(1);
                String value = matcher2.group(2);
                httpResponse.headers.add(key, value);
                buf = buf.substring(matcher2.group(0).length());
            } else {
                if (buf.startsWith("\r\n") || buf.startsWith("\n")) {
                    return HttpResponseParserResult.ok(httpResponse, buf);
                }
                break;
            }
        }
        return HttpResponseParserResult.partial();
    }

    public String getProtocol() {
        return protocol;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public String toString() {
        return "HttpResponse{" +
                "protocol='" + protocol + '\'' +
                ", status=" + status +
                ", message='" + message + '\'' +
                ", headers=" + headers +
                '}';
    }

    static class HttpResponseParserResult {
        private HttpResponseParserCode code;
        private HttpResponse response;
        private byte[] remains;

        public HttpResponseParserResult(HttpResponseParserCode code) {
            this.code = code;
        }

        public static HttpResponseParserResult partial() {
            return new HttpResponseParserResult(HttpResponseParserCode.PARTIAL);
        }

        public static HttpResponseParserResult ok(HttpResponse response, String remains) {
            HttpResponseParserResult httpResponseParserResult = new HttpResponseParserResult(HttpResponseParserCode.OK);
            httpResponseParserResult.response = response;
            httpResponseParserResult.remains = remains.getBytes(StandardCharsets.US_ASCII);
            return httpResponseParserResult;
        }

        public ByteBuffer getRemains() {
            return ByteBuffer.wrap(this.remains);
        }

        public HttpResponseParserCode getCode() {
            return code;
        }

        public HttpResponse getResponse() {
            return response;
        }
    }
}
