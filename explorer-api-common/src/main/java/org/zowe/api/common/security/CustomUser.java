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

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

public class CustomUser extends User {

    /**
     * 
     */
    private static final long serialVersionUID = -7244581406731606670L;

    // Rename to set cookie
    private final String ltpa;

    public CustomUser(String ltpa, String username, String password,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.ltpa = ltpa;
    }

    public String getLtpa() {
        return ltpa;
    }
}
