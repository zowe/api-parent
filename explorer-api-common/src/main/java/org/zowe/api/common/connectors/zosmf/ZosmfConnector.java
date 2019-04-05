/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2018, 2018
 */
package org.zowe.api.common.connectors.zosmf;

import lombok.extern.slf4j.Slf4j;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.zowe.api.common.connectors.ZConnector;
import org.zowe.api.common.connectors.zosmf.exceptions.ZosmfConnectionException;
import org.zowe.api.common.security.CustomUser;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

@Slf4j
@Service
public class ZosmfConnector implements ZConnector {

    private final String zosmfHost;
    private final int zosmfPort;

    @Override
    public URI getFullUrl(String relativePath) throws URISyntaxException {
        return getFullUrl(relativePath, null);
    }

    @Override
    public URI getFullUrl(String relativePath, String query) throws URISyntaxException {
        try {
            return new URI("https", null, zosmfHost, zosmfPort, "/zosmf/" + relativePath, query, null);
        } catch (URISyntaxException e) {
            log.error("getFullUrl", e);
            throw e;
        }
    }

    @Autowired
    public ZosmfConnector(ZosmfProperties properties) {
        zosmfHost = properties.getIpAddress();
        zosmfPort = properties.getHttpsPort();
    }

    @Override
    public HttpResponse request(RequestBuilder requestBuilder) throws IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUser customUser = (CustomUser) authentication.getPrincipal();
        requestBuilder.setHeader("Cookie", customUser.getLtpa());
        requestBuilder.setHeader("X-CSRF-ZOSMF-HEADER", "");
        requestBuilder.setHeader("X-IBM-Response-Timeout", "600");

        HttpClient client;
        try {
            client = createIgnoreSSLClient();
        } catch (GeneralSecurityException e) {
            log.error("request", e);
            throw new ZosmfConnectionException(e);
        }
        return client.execute(requestBuilder.build());

    }

    public Header getLtpaHeader(String username, String password)
            throws IOException, KeyManagementException, NoSuchAlgorithmException, URISyntaxException {
        URI targetUrl = getFullUrl("restjobs/jobs");
        CredentialsProvider credentialsProvider = getCredentialProvider(username, password);
        HttpClient createIgnoreSSLClient = createPreemptiveHttpClientIgnoreSSL(credentialsProvider);

        HttpGet httpGet = new HttpGet(targetUrl);
        httpGet.setHeader("X-CSRF-ZOSMF-HEADER", "");
        HttpResponse response = createIgnoreSSLClient.execute(httpGet,
                createPreemptiveHttpClientContext(credentialsProvider, targetUrl));
        Header setCookieHeader = response.getFirstHeader("Set-Cookie");
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            return setCookieHeader;
        } else {
            throw new IOException("login failed");
        }
    }

    private CredentialsProvider getCredentialProvider(String userName, String password) {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, password));
        return credentialsProvider;
    }

    /**
     * Make a Preemptive Basic Authentication Context
     *
     * @param credentialsProvider
     * @param targetUrl
     * @return
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     */
    private HttpClientContext createPreemptiveHttpClientContext(CredentialsProvider credentialsProvider,
            URI targetUrl) {
        HttpHost targetHost = new HttpHost(targetUrl.getHost(), targetUrl.getPort(), targetUrl.getScheme());
        AuthCache authCache = new BasicAuthCache();
        authCache.put(targetHost, new BasicScheme());
        // Add AuthCache to the execution context
        final HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credentialsProvider);
        context.setAuthCache(authCache);
        return context;
    }

    /**
     * Make a Preemptive Basic Authentication HttpClient
     * 
     * @param credentialsProvider is the credential Provider
     * @return return the httpclient
     * @throws NoSuchAlgorithmException the exception
     * @throws KeyManagementException   the exception too
     */
    public static HttpClient createPreemptiveHttpClientIgnoreSSL(CredentialsProvider credentialsProvider)
            throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslcontext = SSLContext.getInstance("TLS");
        sslcontext.init(null, new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

        } }, new java.security.SecureRandom());
        return HttpClientBuilder.create().setSSLContext(sslcontext).setDefaultCredentialsProvider(credentialsProvider)
            .setSSLHostnameVerifier(new HostnameVerifier() {

                @Override
                public boolean verify(String s1, SSLSession s2) {
                    return true;
                }

            }).build();
    }

    public static HttpClient createIgnoreSSLClientWithPassword(String userName, String password)
            throws NoSuchAlgorithmException, KeyManagementException {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, password));

        SSLContext sslcontext = SSLContext.getInstance("TLS");
        sslcontext.init(null, new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

        } }, new java.security.SecureRandom());
        return HttpClientBuilder.create().setSSLContext(sslcontext).setDefaultCredentialsProvider(credentialsProvider)
            .setSSLHostnameVerifier(new HostnameVerifier() {

                @Override
                public boolean verify(String s1, SSLSession s2) {
                    return true;
                }

            }).build();
    }

    public static HttpClient createIgnoreSSLClient() throws KeyManagementException, NoSuchAlgorithmException {
        SSLContext sslcontext = SSLContext.getInstance("TLS");
        sslcontext.init(null, new TrustManager[] { new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

        } }, new java.security.SecureRandom());
        return HttpClientBuilder.create().setSSLContext(sslcontext).setSSLHostnameVerifier(new HostnameVerifier() {

            @Override
            public boolean verify(String s1, SSLSession s2) {
                return true;
            }

        }).build();
    }
}