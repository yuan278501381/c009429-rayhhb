package com.chinajey.dwork.common;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.lang.NonNull;

import java.net.URI;
import java.net.URISyntaxException;

public class CustomHttpRequestWrapper extends HttpRequestWrapper {

    private final String url;

    public CustomHttpRequestWrapper(HttpRequest request, String url) {
        super(request);
        this.url = url;
    }

    @NonNull
    @Override
    public URI getURI() {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}