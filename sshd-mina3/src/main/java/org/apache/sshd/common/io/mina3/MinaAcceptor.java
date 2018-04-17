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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.api.IoHandler;
import org.apache.mina.api.IoSession;
import org.apache.mina.transport.nio.NioSelectorLoop;
import org.apache.mina.transport.nio.NioTcpServer;
import org.apache.mina.transport.nio.SelectorLoopPool;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.PropertyResolverUtils;

/**
 */
public class MinaAcceptor extends MinaService implements org.apache.sshd.common.io.IoAcceptor, IoHandler {
    public static final int DEFAULT_BACKLOG = 0;
    public static final boolean DEFAULT_REUSE_ADDRESS = true;

    protected final Map<SocketAddress, NioTcpServer> acceptors = new ConcurrentHashMap<>();
    // Acceptor
    protected int backlog = DEFAULT_BACKLOG;
    protected boolean reuseAddress = DEFAULT_REUSE_ADDRESS;

    public MinaAcceptor(FactoryManager manager, org.apache.sshd.common.io.IoHandler handler, SelectorLoopPool pool) {
        super(manager, handler, pool);

        backlog = PropertyResolverUtils.getIntProperty(manager, FactoryManager.SOCKET_BACKLOG, DEFAULT_BACKLOG);
        reuseAddress = PropertyResolverUtils.getBooleanProperty(manager, FactoryManager.SOCKET_REUSEADDR, DEFAULT_REUSE_ADDRESS);
    }

    protected NioTcpServer createAcceptor() {
        NioTcpServer acceptor = new NioTcpServer(
                new NioSelectorLoop("accept", 0),
                pool,
                null);
        configure(acceptor.getSessionConfig());
        acceptor.setReuseAddress(reuseAddress);
        acceptor.setIoHandler(this);
        return acceptor;
    }

    @Override
    public Map<Long, org.apache.sshd.common.io.IoSession> getManagedSessions() {
        Map<Long, org.apache.sshd.common.io.IoSession> sessions = new HashMap<Long, org.apache.sshd.common.io.IoSession>();
        for (NioTcpServer acceptor : acceptors.values()) {
            Map<Long, IoSession> mina = new HashMap<>(acceptor.getManagedSessions());
            for (Long id : mina.keySet()) {
                // Avoid possible NPE if the MinaSession hasn't been created yet
                org.apache.sshd.common.io.IoSession session = getSession(mina.get(id));
                if (session != null) {
                    sessions.put(id, session);
                }
            }
        }
        return sessions;
    }

    @Override
    public void bind(Collection<? extends SocketAddress> addresses) throws IOException {
        for (SocketAddress address : addresses) {
            bind(address);
        }
    }

    @Override
    public void bind(SocketAddress address) throws IOException {
        NioTcpServer acceptor = createAcceptor();
        acceptor.bind(address);
        acceptors.put(address, acceptor);
    }

    @Override
    public void unbind() {
        unbind(new HashSet<>(acceptors.keySet()));
    }

    @Override
    public void unbind(Collection<? extends SocketAddress> addresses) {
        for (SocketAddress address : addresses) {
            unbind(address);
        }
    }

    @Override
    public void unbind(SocketAddress address) {
        NioTcpServer acceptor = acceptors.remove(address);
        if (acceptor != null) {
            acceptor.unbind();
        }
    }

    @Override
    public Set<SocketAddress> getBoundAddresses() {
        Set<SocketAddress> bound = new HashSet<>();
        for (NioTcpServer acceptor : acceptors.values()) {
            try {
                bound.add(acceptor.getServerSocketChannel().getLocalAddress());
            } catch (IOException e) {
                log.debug("Error retrieving bound address", e);
            }
        }
        return bound;
    }

    @Override
    protected void doCloseImmediately() {
        unbind();
        super.doCloseImmediately();
    }
}