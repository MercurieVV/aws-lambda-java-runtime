package com.github.mercurievv.aws.lambda.nio;

import java.util.*;

public class HttpHeaders {
    private Map<String, List<String>> headers;

    public HttpHeaders() {
        headers = new LinkedHashMap<>();
    }

    public HttpHeaders add(String name, String value) {
        name = name.toLowerCase();

        if (!headers.containsKey(name)) {
            headers.put(name, new ArrayList<>());
        }
        headers.get(name).add(value);
        return this;
    }

    public Set<String> keySet() {
        return headers.keySet();
    }

    public List<String> get(String key) {
        return headers.get(key);
    }

    @Override
    public String toString() {
        return "HttpHeaders{" +
                "headers=" + headers +
                '}';
    }
}
