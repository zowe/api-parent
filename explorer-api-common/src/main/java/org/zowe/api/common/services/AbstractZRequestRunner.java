/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2019
 */
package org.zowe.api.common.services;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import lombok.extern.slf4j.Slf4j;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.springframework.util.StringUtils;
import org.zowe.api.common.connectors.ZConnector;
import org.zowe.api.common.exceptions.HtmlEscapedZoweApiRestException;
import org.zowe.api.common.exceptions.NoZosmfResponseEntityException;
import org.zowe.api.common.exceptions.ServerErrorException;
import org.zowe.api.common.exceptions.ZoweApiRestException;
import org.zowe.api.common.utils.ResponseCache;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.IntStream;

@Slf4j
public abstract class AbstractZRequestRunner<T> {

    public <Z extends ZConnector> T run(Z zConnector) {
        try {
            RequestBuilder requestBuilder = prepareQuery(zConnector);
            URI uri = requestBuilder.getUri();
            HttpResponse response = zConnector.request(requestBuilder);
            ResponseCache responseCache = new ResponseCache(response);
            return processResponse(responseCache, uri);
        } catch (IOException | URISyntaxException e) {
            log.error("run", e);
            throw new ServerErrorException(e);
        }
    }

    protected abstract int[] getSuccessStatus();

    protected abstract <Z extends ZConnector> RequestBuilder prepareQuery(Z zConnector)
            throws URISyntaxException, IOException;

    T processResponse(ResponseCache responseCache, URI uri) throws IOException {
        int statusCode = responseCache.getStatus();
        boolean success = IntStream.of(getSuccessStatus()).anyMatch(x -> x == statusCode);
        if (success) {
            return getResult(responseCache);
        } else {
            throw createGeneralException(responseCache, uri);
        }
    }

    protected abstract T getResult(ResponseCache responseCache) throws IOException;

    protected ZoweApiRestException createException(JsonObject jsonResponse, int statusCode) throws IOException {
        return null;
    }

    protected ZoweApiRestException createGeneralException(ResponseCache responseCache, URI uri) throws IOException {
        String entityString = responseCache.getEntity();
        org.springframework.http.HttpStatus springStatus = responseCache.getSpringHttpStatus();
        if (StringUtils.hasText(entityString)) {
            ContentType contentType = responseCache.getContentType();
            String mimeType = contentType.getMimeType();
            if (mimeType.equals(ContentType.APPLICATION_JSON.getMimeType())) {
                JsonObject jsonResponse = responseCache.getEntityAsJsonObject();

                ZoweApiRestException exception = createException(jsonResponse, responseCache.getStatus());
                if (exception != null) {
                    return exception;
                }
                if (jsonResponse.has("message")) {
                    String zosmfMessage = jsonResponse.get("message").getAsString();
                    return new HtmlEscapedZoweApiRestException(springStatus, zosmfMessage);
                }
                return new HtmlEscapedZoweApiRestException(springStatus, jsonResponse.toString());
            } else {
                return new HtmlEscapedZoweApiRestException(springStatus, entityString);
            }
        } else {
            return new NoZosmfResponseEntityException(springStatus, uri.toString());
        }
    }

    protected String getStringOrNull(JsonObject json, String key) {
        String value = null;
        JsonElement jsonElement = json.get(key);
        if (shouldRead(jsonElement)) {
            value = jsonElement.getAsString();
        }
        return value;
    }

    protected Integer getIntegerOrNull(JsonObject json, String key) {
        Integer value = null;
        JsonElement jsonElement = json.get(key);
        if (shouldRead(jsonElement)) {
            value = jsonElement.getAsInt();
        }
        return value;
    }

    private boolean shouldRead(JsonElement jsonElement) {
        return !(jsonElement == null || jsonElement.isJsonNull() || jsonElement.getAsString().equals("?"));
    }
}
