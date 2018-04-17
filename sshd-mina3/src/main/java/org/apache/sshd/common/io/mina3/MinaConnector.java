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
package org.apache.sshd.common.io.mina3;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.apache.mina.api.IoFutureListener;
import org.apache.mina.api.IoHandler;
import org.apache.mina.api.IoSession;
import org.apache.mina.transport.nio.NioSelectorLoop;
import org.apache.mina.transport.nio.NioTcpClient;
import org.apache.mina.transport.nio.SelectorLoopPool;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.future.DefaultSshFuture;
import org.apache.sshd.common.io.IoConnectFuture;

/**
 */
public class MinaConnector extends MinaService implements org.apache.sshd.common.io.IoConnector, IoHandler {

    protected volatile NioTcpClient connector;

    public MinaConnector(FactoryManager manager, org.apache.sshd.common.io.IoHandler handler, SelectorLoopPool pool) {
        super(manager, handler, pool);
    }

    protected NioTcpClient createConnector() {
        NioTcpClient connector = new NioTcpClient(new NioSelectorLoop("connect", 0), pool, null);
        configure(connector.getSessionConfig());
        return connector;
    }

    protected NioTcpClient getConnector() {
        if (connector == null) {
            synchronized (this) {
                if (connector == null) {
                    connector = createConnector();
                    connector.setIoHandler(this);
                }
            }
        }
        return connector;
    }

    public Map<Long, org.apache.sshd.common.io.IoSession> getManagedSessions() {
        Map<Long, IoSession> mina = new HashMap<>(getConnector().getManagedSessions());
        Map<Long, org.apache.sshd.common.io.IoSession> sessions = new HashMap<Long, org.apache.sshd.common.io.IoSession>();
        for (Long id : mina.keySet()) {
            // Avoid possible NPE if the MinaSession hasn't been created yet
            org.apache.sshd.common.io.IoSession session = getSession(mina.get(id));
            if (session != null) {
                sessions.put(id, session);
            }
        }
        return sessions;
    }

    public IoConnectFuture connect(SocketAddress address) {
        class Future extends DefaultSshFuture<IoConnectFuture> implements IoConnectFuture {
            Future(Object lock) {
                super(address, lock);
            }

            public org.apache.sshd.common.io.IoSession getSession() {
                Object v = getValue();
                return v instanceof org.apache.sshd.common.io.IoSession ? (org.apache.sshd.common.io.IoSession) v : null;
            }

            public Throwable getException() {
                Object v = getValue();
                return v instanceof Throwable ? (Throwable) v : null;
            }

            public boolean isConnected() {
                return getValue() instanceof org.apache.sshd.common.io.IoSession;
            }

            public void setSession(org.apache.sshd.common.io.IoSession session) {
                setValue(session);
            }

            public void setException(Throwable exception) {
                setValue(exception);
            }
        }

        final IoConnectFuture future = new Future(null);
        getConnector().connect(address).register(new IoFutureListener<IoSession>() {
            @Override
            public void completed(IoSession ioSession) {
                org.apache.sshd.common.io.IoSession session = getSession(ioSession);
//                System.err.println("connected:\n\tmina: " + ioSession + "\n\tsshd: " + session + "\n\tthread: " + Thread.currentThread().getName());
//                new Throwable().printStackTrace();
                future.setSession(session);
            }

            @Override
            public void exception(Throwable t) {
                future.setException(t);
            }
        });
        return future;
    }

    @Override
    protected void doCloseImmediately() {
        try {
            getConnector().disconnect();
        } catch (IOException e) {
            log.warn("Exception caught while closing connector: {}",
                    e.getMessage(), e);
        }
        super.doCloseImmediately();
    }
}