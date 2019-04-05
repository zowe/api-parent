/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2018, 2018
 */
package org.zowe.api.common.security;

import lombok.extern.slf4j.Slf4j;

import org.apache.http.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.zowe.api.common.connectors.ConnectorProperties;
import org.zowe.api.common.connectors.zosmf.ZosmfConnector;
import org.zowe.api.common.connectors.zss.ZssConnector;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class CustomAuthenticationProvider implements AuthenticationProvider {

    @Autowired
    ZosmfConnector zosmfConnector;

    @Autowired
    ZssConnector zssConnector;

    // TODO - create enum?
    private String connectionType = "zosmf";

    // TODO - review exception/responses/logging https://github.com/zowe/explorer-api-common/issues/9

    @Autowired
    public CustomAuthenticationProvider(ConnectorProperties properties) {
        String connector = properties.getConnector();
        if ("zss".equals(connector)) {
            connectionType = "zss";
        } else {
//        } else if ("zosmf".equals(connector)) {
        }
//        } else {
//            // TODO - tidy up/default, make an enum?
//            throw new IllegalArgumentException("Connector: " + connector + " was not a recognised type");
//        }
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        if (!(authentication.getPrincipal() instanceof CustomUser)) {
            try {
                if ("zosmf".equals(connectionType)) {
                    Header ltpaHeader = zosmfConnector.getLtpaHeader(username, password);
                    final List<GrantedAuthority> grantedAuths = new ArrayList<>();
                    grantedAuths.add(new SimpleGrantedAuthority("ROLE_USER"));
                    final UserDetails principal = new CustomUser(ltpaHeader.getValue(), username, password,
                            grantedAuths);
                    final Authentication auth = new UsernamePasswordAuthenticationToken(principal, password,
                            grantedAuths);
                    return auth;
                } else if ("zss".equals(connectionType)) {
                    Header setCookie = zssConnector.login(username, password);
                    final List<GrantedAuthority> grantedAuths = new ArrayList<>();
                    grantedAuths.add(new SimpleGrantedAuthority("ROLE_USER"));
                    final UserDetails principal = new CustomUser(setCookie.getValue(), username, password,
                            grantedAuths);
                    final Authentication auth = new UsernamePasswordAuthenticationToken(principal, password,
                            grantedAuths);
                    return auth;
                }
            } catch (Exception e) {
                log.error("authenticate", e);
            }
            throw new UsernameNotFoundException(username);
        }
        return null;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(UsernamePasswordAuthenticationToken.class);
    }

}
