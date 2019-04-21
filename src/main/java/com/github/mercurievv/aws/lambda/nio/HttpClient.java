package com.github.mercurievv.aws.lambda.nio;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import java.util.logging.Logger;

enum HttpResponseParserCode {
    OK,
    PARTIAL
}

class StringHttpRequest extends HttpRequest {
    private static final Logger log = Logger.getLogger("HttpClient");

    private final String content;
    private final ByteBuffer buffer;
    private final byte[] bytes;
    private long wroteBytes = 0;

    public StringHttpRequest(String method, URI uri, String content) {
        super(method, uri);
        this.content = Objects.requireNonNull(content);
        getHeaders().add("Content-Length", String.valueOf(content.length()));
        this.bytes = content.getBytes(StandardCharsets.UTF_8);
        this.buffer = ByteBuffer.wrap(this.bytes);
    }

    @Override
    public boolean writeEntity(SocketChannel sch) throws IOException {
        int wrote = sch.write(buffer);
        wroteBytes += wrote;
        log.info("wrote " + wrote);
        return this.bytes.length != wroteBytes;
    }

}

class ByteBufferHttpRequest extends HttpRequest {
    private static final Logger log = Logger.getLogger("HttpClient");

//    private final String content;
    private final ByteBuffer buffer;
//    private final byte[] bytes;
    private long wroteBytes = 0;

    public ByteBufferHttpRequest(String method, URI uri, ByteBuffer buffer) {
        super(method, uri);
        getHeaders().add("Content-Length", String.valueOf(content.length()));
        this.buffer = buffer;
//        this.content = Objects.requireNonNull(content);
//        this.bytes = content.getBytes(StandardCharsets.UTF_8);
//        this.buffer = ByteBuffer.wrap(this.bytes);
    }

    @Override
    public boolean writeEntity(SocketChannel sch) throws IOException {
        int wrote = sch.write(buffer);
        wroteBytes += wrote;
        log.info("wrote " + wrote);
        return this.bytes.length != wroteBytes;
    }

}

class HttpRequest {
    private URI uri;
    private String method;
    private HttpHeaders headers;

    public HttpRequest(String method, URI uri) {
        this.uri = uri;
        this.method = method;
        this.headers = new HttpHeaders();
        // should we add port number to host header?
        headers.add("Host", uri.getHost());
    }

    public static HttpRequest get(URI uri) {
        return new HttpRequest("GET", uri);
    }

    private void setMethod(String method) {
        this.method = method;
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public String getHost() {
        return uri.getHost();
    }

    public int getPort() {
        int port = uri.getPort();
        if (port == -1) {
            switch (uri.getScheme()) {
                case "http":
                    return 80;
                case "https":
                    return 443;
                default:
                    throw new IllegalStateException("Invalid scheme: " + uri);
            }
        } else {
            return port;
        }
    }

    public String getMethod() {
        return method;
    }

    public String getHeaderPartAsString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(method).append(" ").append(uri.getRawPath()).append(" HTTP/1.0\r\n");
        for (String key : headers.keySet()) {
            for (String val : headers.get(key)) {
                stringBuilder.append(key + " : " + val + "\r\n");
            }
        }
        stringBuilder.append("\r\n");
        return stringBuilder.toString();
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public boolean writeEntity(SocketChannel sch) throws IOException {
        return false; // finished.
    }
}

enum HttpStateType {
    NOT_CONNECTED,
    CONNECTED,
    HEADER_SENT,
    ENTITY_SENT,
    HEADER_RECEIVED;
}

class HttpState {
    ByteBuffer buf;
    HttpHandler httpHandler;
    HttpStateType state;
    HttpRequest httpRequest;
    long read;

    public HttpState(HttpRequest httpRequest, HttpHandler httpHandler) {
        this.state = HttpStateType.NOT_CONNECTED;
        this.httpRequest = httpRequest;
        this.httpHandler = httpHandler;
        this.buf = ByteBuffer.allocate(1024);
        this.read = 0;
    }
}

public class HttpClient implements Closeable {
    private static final Logger log = Logger.getLogger("HttpClient");
    private Set<SocketChannel> channels;

    private final Selector selector;

    public HttpClient() throws IOException {
        selector = Selector.open();
        this.channels = new HashSet<>();
    }

    public void get(URI uri, HttpHandler httpHandler) throws IOException {
        log.info("get: " + uri);
        this.request(HttpRequest.get(uri), httpHandler);
    }

    public void request(HttpRequest request, HttpHandler handler) throws IOException {
        // TODO keep-alive support
        request.getHeaders().add("Connection", "close");

        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress(request.getHost(), request.getPort()));

        SelectionKey selectionKey = socketChannel.register(selector, SelectionKey.OP_CONNECT);
        selectionKey.attach(new HttpState(request, handler));
        this.channels.add(socketChannel);
    }

    public void waitAll() {
        while (!this.channels.isEmpty()) {
            try {
                selector.select();
            } catch (IOException e) {
                log.warning(e.getMessage());
            }
            Set keys = selector.selectedKeys();
            if (!keys.isEmpty()) {
                Iterator iterator = keys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = (SelectionKey) iterator.next();
                    iterator.remove();

                    HttpState state = (HttpState) key.attachment();

                    if (key.isConnectable()) {
                        log.info("connecting connection!");
                        SocketChannel sch = (SocketChannel) key.channel();
                        try {
                            sch.finishConnect();
                            state.state = HttpStateType.CONNECTED;
                            log.info(String.valueOf(sch.isBlocking()));
                            log.info(String.valueOf(sch.isConnected()));
                            key.interestOps(SelectionKey.OP_WRITE);
                        } catch (IOException e) {
                            try {
                                sch.close();
                            } catch (IOException e1) {
                                log.info("Can't close connection: " + e1.getMessage());
                            }
                        }
                    } else if (key.isWritable()) {
                        log.info("writing");
                        SocketChannel sch = (SocketChannel) key.channel();
                        try {
                            if (state.state == HttpStateType.CONNECTED) {
                                ByteBuffer src = ByteBuffer.wrap(
                                        state.httpRequest.getHeaderPartAsString().getBytes(StandardCharsets.US_ASCII));
                                sch.write(src);
                                state.state = HttpStateType.HEADER_SENT;
                            } else {
                                // TODO send entity
                                if (!state.httpRequest.writeEntity(sch)) {
                                    state.state = HttpStateType.ENTITY_SENT;
                                    key.interestOps(SelectionKey.OP_READ);
                                }
                            }
                        } catch (IOException e) {
                            try {
                                sch.close();
                            } catch (IOException e1) {
                                log.info("Can't close connection: " + e1.getMessage());
                            }
                        }
                    } else if (key.isReadable()) {
                        log.info("reading");
                        SocketChannel sch = (SocketChannel) key.channel();
                        try {
                            ByteBuffer buf = state.buf;
                            int read = sch.read(buf);
                            if (read <= 0) {
                                sch.close();
                                this.channels.remove(sch);
                                continue;
                            }
                            log.info("got " + read);
                            buf.flip();
                            if (state.state == HttpStateType.ENTITY_SENT) {
                                String got = new String(buf.array(), buf.arrayOffset() + buf.position(),
                                        buf.remaining(), StandardCharsets.UTF_8);
                                HttpResponse.HttpResponseParserResult result = HttpResponse.parse(got);
                                switch (result.getCode()) {
                                    case OK:
                                        log.info("PARSED");
                                        state.state = HttpStateType.HEADER_RECEIVED;
                                        state.httpHandler.onHeader(result.getResponse());
                                        state.read += result.getRemains().remaining();
                                        state.httpHandler.onBody(result.getRemains());
                                        break;
                                    case PARTIAL:
                                        log.info("PARTIAL");
                                        break;
                                }
                            } else {
                                state.read += buf.remaining();
                                state.httpHandler.onBody(buf);
                            }
                        } catch (IOException e) {
                            log.info("Can't read from connection: " + e.getMessage());
                        }
                    }
                    if (!key.isValid()) {
                        try {
                            key.channel().close();
                        } catch (IOException e) {
                            log.info("Can't close connection: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        selector.close();
    }

    public void post(URI uri, ByteBuffer content, HttpHandler httpHandler) throws IOException {
        ByteBufferHttpRequest httpRequest = new ByteBufferHttpRequest("POST", uri, content);
        this.request(httpRequest, httpHandler);
    }
}

class Main {

    public static void main(String[] args) throws IOException {
        try (HttpClient httpClient = new HttpClient()) {
            httpClient.post(URI.create("http://64p.org/"), "hoge", new HttpHandler() {
                @Override
                public void onHeader(HttpResponse response) {
                    System.out.println(response);
                }

                @Override
                public void onBody(ByteBuffer buf) {
                    String got = new String(buf.array(), buf.arrayOffset() + buf.position(),
                            buf.remaining(), StandardCharsets.UTF_8);

                    System.out.println("GOT: " + got);
                }
            });

//            for (int i = 0; i < 10; ++i) {
//                httpClient.get(URI.create("http://64p.org/"), new HttpHandler() {
//                    @Override
//                    public void onHeader(HttpResponse response) {
//                        System.out.println(response);
//                    }
//
//                    @Override
//                    public void onBody(ByteBuffer buf) {
//                        String got = new String(buf.array(), buf.arrayOffset() + buf.position(),
//                                buf.remaining(), StandardCharsets.UTF_8);
//
//                        System.out.println("GOT: " + got);
//                    }
//                });
//            }
            httpClient.waitAll();
        }
    }
}

