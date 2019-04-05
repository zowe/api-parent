/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2018, 2019
 */
package org.zowe.api.common.connectors.zss;

import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.zowe.api.common.connectors.ZConnector;
import org.zowe.api.common.security.CustomUser;
import org.zowe.api.common.utils.ResponseCache;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
@Service
public class ZssConnector implements ZConnector {

    private final String zssHost;
    private final int zssPort;

    @Override
    public URI getFullUrl(String relativePath) throws URISyntaxException {
        return getFullUrl(relativePath, null);
    }

    @Override
    public URI getFullUrl(String relativePath, String query) throws URISyntaxException {
        try {
            return new URI("http", null, zssHost, zssPort, "/" + relativePath, query, null);
        } catch (URISyntaxException e) {
            log.error("getFullUrl", e);
            throw e;
        }
    }

    @Autowired
    public ZssConnector(ZssProperties properties) {
        zssHost = properties.getIpAddress();
        zssPort = properties.getPort();
    }

    @Override
    public HttpResponse request(RequestBuilder requestBuilder) throws IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUser customUser = (CustomUser) authentication.getPrincipal();
        requestBuilder.setHeader("Cookie", customUser.getLtpa());

        HttpClient client;
        // ZSS doesn't support SSL
        client = HttpClientBuilder.create().build();
        return client.execute(requestBuilder.build());
    }

    // TODO unit tests for failed login
    public Header login(String username, String password) throws IOException, URISyntaxException {
        URI requestUrl = getFullUrl("login");
        // TODO - model zss login model object?
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("username", username);
        requestBody.addProperty("password", password);
        StringEntity requestEntity = new StringEntity(requestBody.toString(), ContentType.APPLICATION_JSON);
        RequestBuilder requestBuilder = RequestBuilder.post(requestUrl).setEntity(requestEntity);
        HttpResponse response = HttpClientBuilder.create().build().execute(requestBuilder.build());
        ResponseCache responseCache = new ResponseCache(response);
        if (responseCache.getStatus() == 200) {
            Header setCookieHeader = responseCache.getFirstHeader("Set-Cookie");
            return setCookieHeader;
        }
        // TODO NOW - better exception
        throw new RuntimeException("zss login failed" + responseCache.getEntity());
    }
}