/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2018, 2018
 */
package org.zowe.api.common.connectors;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.RequestBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public interface ZConnector {

    public URI getFullUrl(String relativePath) throws URISyntaxException;

    public URI getFullUrl(String relativePath, String query) throws URISyntaxException;

    public HttpResponse request(RequestBuilder requestBuilder) throws IOException;
}