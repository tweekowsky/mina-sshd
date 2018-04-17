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

import org.apache.mina.api.ConfigurationException;
import org.apache.mina.api.IdleStatus;
import org.apache.mina.api.IoHandler;
import org.apache.mina.api.IoService;
import org.apache.mina.api.IoSession;
import org.apache.mina.codec.IoBuffer;
import org.apache.mina.session.AttributeKey;
import org.apache.mina.transport.nio.SelectorLoopPool;
import org.apache.mina.transport.tcp.TcpSessionConfig;
import org.apache.sshd.common.Closeable;
import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.PropertyResolverUtils;
import org.apache.sshd.common.util.GenericUtils;
import org.apache.sshd.common.util.closeable.AbstractCloseable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
public abstract class MinaService extends AbstractCloseable implements org.apache.sshd.common.io.IoService, IoHandler, Closeable {

    protected static final AttributeKey<org.apache.sshd.common.io.IoSession> SESSION_ATTRIBUTE
            = new AttributeKey<>(org.apache.sshd.common.io.IoSession.class, org.apache.sshd.common.io.IoSession.class.getName());

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final FactoryManager manager;
    protected final org.apache.sshd.common.io.IoHandler handler;
    protected final SelectorLoopPool pool;

    public MinaService(FactoryManager manager, org.apache.sshd.common.io.IoHandler handler, SelectorLoopPool pool) {
        this.manager = manager;
        this.handler = handler;
        this.pool = pool;
    }

    @Override
    public void sessionOpened(IoSession session) {
        org.apache.sshd.common.io.IoSession ioSession = new MinaSession(this, session);
//        System.err.println("sessionOpened:\n\tmina: " + session + "\n\tsshd: " + ioSession + "\n\tthread: " + Thread.currentThread().getName());
//        new Throwable().printStackTrace();
        session.setAttribute(SESSION_ATTRIBUTE, ioSession);
        try {
            handler.sessionCreated(ioSession);
        } catch (Exception e) {
            exceptionCaught(session, e);
        }
    }

    @Override
    public void sessionClosed(IoSession session) {
        try {
            handler.sessionClosed(getSession(session));
        } catch (Exception e) {
            exceptionCaught(session, e);
        }
    }

    @Override
    public void exceptionCaught(IoSession session, Exception cause) {
        try {
            handler.exceptionCaught(getSession(session), cause);
        } catch (Exception e) {
            log.debug("Caught exception", e);
        }
    }

    @Override
    public void messageReceived(IoSession session, Object message) {
        try {
            handler.messageReceived(getSession(session), MinaSupport.asReadable((IoBuffer) message));
        } catch (Exception e) {
            exceptionCaught(session, e);
        }
    }

    @Override
    public void serviceInactivated(IoService service) {
        // Do nothing
    }

    @Override
    public void serviceActivated(IoService service) {
        // Do nothing
    }

    @Override
    public void messageSent(IoSession session, Object message) {
        // Do nothing
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) {
        // Do nothing
    }

    protected org.apache.sshd.common.io.IoSession getSession(IoSession session) {
        return session.getAttribute(SESSION_ATTRIBUTE);
    }

    protected void configure(TcpSessionConfig config) {
        Boolean boolVal = getBoolean(FactoryManager.SOCKET_KEEPALIVE);
        if (boolVal != null) {
            try {
                config.setKeepAlive(boolVal);
            } catch (ConfigurationException t) {
                handleConfigurationError(config, FactoryManager.SOCKET_KEEPALIVE, boolVal, t);
            }
        }

        Integer intVal = getInteger(FactoryManager.SOCKET_SNDBUF);
        if (intVal != null) {
            try {
                config.setSendBufferSize(intVal);
            } catch (ConfigurationException t) {
                handleConfigurationError(config, FactoryManager.SOCKET_SNDBUF, intVal, t);
            }
        }

        intVal = getInteger(FactoryManager.SOCKET_RCVBUF);
        if (intVal != null) {
            try {
                config.setReadBufferSize(intVal);
            } catch (ConfigurationException t) {
                handleConfigurationError(config, FactoryManager.SOCKET_RCVBUF, intVal, t);
            }
        }

        intVal = getInteger(FactoryManager.SOCKET_LINGER);
        if (intVal != null) {
            try {
                config.setSoLinger(intVal);
            } catch (ConfigurationException t) {
                handleConfigurationError(config, FactoryManager.SOCKET_LINGER, intVal, t);
            }
        }

        boolVal = getBoolean(FactoryManager.TCP_NODELAY);
        if (boolVal != null) {
            try {
                config.setTcpNoDelay(boolVal);
            } catch (ConfigurationException t) {
                handleConfigurationError(config, FactoryManager.TCP_NODELAY, boolVal, t);
            }
        }

//        if (sessionConfig != null) {
//            config.setAll(sessionConfig);
//        }
    }

    protected void handleConfigurationError(TcpSessionConfig config, String propName, Object propValue, ConfigurationException t) {
        Throwable e = GenericUtils.resolveExceptionCause(t);
        log.warn("handleConfigurationError({}={}) failed ({}) to configure: {}",
                propName, propValue, e.getClass().getSimpleName(), e.getMessage());
    }

    protected Integer getInteger(String property) {
        return PropertyResolverUtils.getInteger(manager, property);
    }

    protected Boolean getBoolean(String property) {
        return PropertyResolverUtils.getBoolean(manager, property);
    }

}