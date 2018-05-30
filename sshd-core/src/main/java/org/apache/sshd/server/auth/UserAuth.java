/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sshd.server.auth;

import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.server.session.ServerSession;

/**
 * Server side authentication mechanism.
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public interface UserAuth {

    /**
     * @return the authorization method used by the instance
     */
    String getName();

    /**
     * @return the service used by the instance
     */
    String getService();

    /**
     * @return The attached username - may be {@code null}/empty if holder
     * not yet initialized
     */
    String getUsername();

    /**
     * @return The current session for which the authentication is being
     * tracked. <B>Note:</B> may be {@code null} if the instance has not
     * been initialized yet
     */
    ServerSession getSession();

    /**
     * Try to authenticate the user. This methods should return a non {@code null}
     * value indicating if the authentication succeeded. If the authentication is
     * still ongoing, a {@code null} value should be returned.
     *
     * @param session  the current {@link ServerSession} session
     * @param username the user trying to log in
     * @param service  the requested service name
     * @param buffer   the request buffer containing parameters specific to this request
     * @return <code>true</code> if the authentication succeeded, <code>false</code> if the authentication
     * failed and {@code null} if not finished yet
     * @throws AsyncAuthException if the service is willing to perform an asynchronous authentication
     * @throws Exception if the authentication fails
     */
    Boolean auth(ServerSession session, String username, String service, Buffer buffer) throws AsyncAuthException, Exception;

    /**
     * Handle another step in the authentication process.
     *
     * @param buffer the request buffer containing parameters specific to this request
     * @return <code>true</code> if the authentication succeeded, <code>false</code> if the authentication
     * failed and {@code null} if not finished yet
     * @throws AsyncAuthException if the service is willing to perform an asynchronous authentication
     * @throws Exception if the authentication fails
     */
    Boolean next(Buffer buffer) throws AsyncAuthException, Exception;

    /**
     * Free any system resources used by the module.
     */
    void destroy();
}
