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

import java.net.SocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.mina.api.IoFutureListener;
import org.apache.mina.codec.IoBuffer;
import org.apache.sshd.common.Closeable;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.future.DefaultCloseFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.io.AbstractIoWriteFuture;
import org.apache.sshd.common.io.IoService;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.io.IoWriteFuture;
import org.apache.sshd.common.util.NumberUtils;
import org.apache.sshd.common.util.ValidateUtils;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.closeable.AbstractInnerCloseable;
import org.apache.sshd.common.util.closeable.IoBaseCloseable;

/**
 */
public class MinaSession extends AbstractInnerCloseable implements IoSession {

    private final MinaService service;
    private final org.apache.mina.api.IoSession session;
    private final Object sessionWriteId;
    private final Map<Object, Object> attributes = new ConcurrentHashMap<>();

    public MinaSession(MinaService service, org.apache.mina.api.IoSession session) {
        this.service = service;
        this.session = session;
        this.sessionWriteId = Objects.toString(session);
    }

    public org.apache.mina.api.IoSession getSession() {
        return session;
    }

    public void suspend() {
        session.suspendRead();
        session.suspendWrite();
    }

    @Override
    public Object getAttribute(Object key) {
        return attributes.get(key);
    }

    @Override
    public Object setAttribute(Object key, Object value) {
        return attributes.put(key, value);
    }

    @Override
    public Object setAttributeIfAbsent(Object key, Object value) {
        return attributes.putIfAbsent(key, value);
    }

    @Override
    public Object removeAttribute(Object key) {
        return attributes.remove(key);
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return session.getRemoteAddress();
    }

    @Override
    public SocketAddress getLocalAddress() {
        return session.getLocalAddress();
    }

    @Override
    public long getId() {
        return session.getId();
    }

    @Override
    protected Closeable getInnerCloseable() {
        return new IoBaseCloseable() {
            @SuppressWarnings("synthetic-access")
            private final DefaultCloseFuture future = new DefaultCloseFuture(MinaSession.this.toString(), lock);

            @SuppressWarnings("synthetic-access")
            @Override
            public boolean isClosing() {
                return session.isClosing();
            }

            @SuppressWarnings("synthetic-access")
            @Override
            public boolean isClosed() {
                return !session.isConnected();
            }

            @Override
            public void addCloseFutureListener(SshFutureListener<CloseFuture> listener) {
                future.addListener(listener);
            }

            @Override
            public void removeCloseFutureListener(SshFutureListener<CloseFuture> listener) {
                future.removeListener(listener);
            }

            @SuppressWarnings("synthetic-access")
            @Override
            public CloseFuture close(boolean immediately) {
                session.close(immediately).register(new IoFutureListener<Void>() {
                    @Override
                    public void exception(Throwable t) {
                        future.setValue(t);
                    }

                    @Override
                    public void completed(Void result) {
                        future.setClosed();
                    }
                });
                return future;
            }
        };
    }

    // NOTE !!! data buffer may NOT be re-used when method returns - at least until IoWriteFuture is signalled
    public IoWriteFuture write(byte[] data) {
        return write(data, 0, NumberUtils.length(data));
    }

    // NOTE !!! data buffer may NOT be re-used when method returns - at least until IoWriteFuture is signalled
    public IoWriteFuture write(byte[] data, int offset, int len) {
        return write(IoBuffer.wrap(data, offset, len));
    }

    @Override // NOTE !!! data buffer may NOT be re-used when method returns - at least until IoWriteFuture is signalled
    public IoWriteFuture writePacket(Buffer buffer) {
        return write(MinaSupport.asIoBuffer(buffer));
    }

    // NOTE !!! data buffer may NOT be re-used when method returns - at least until IoWriteFuture is signalled
    public IoWriteFuture write(IoBuffer buffer) {
        final Future future = new Future(sessionWriteId, null);
        session.writeWithFuture(buffer).register(new IoFutureListener<Void>() {
            @Override
            public void exception(Throwable t) {
                future.setException(t);
            }

            @Override
            public void completed(Void result) {
                future.setWritten();
            }
        });
        return future;
    }

    public static class Future extends AbstractIoWriteFuture {
        public Future(Object id, Object lock) {
            super(id, lock);
        }

        public void setWritten() {
            setValue(Boolean.TRUE);
        }

        public void setException(Throwable exception) {
            setValue(ValidateUtils.checkNotNull(exception, "No exception specified"));
        }
    }

    @Override
    public IoService getService() {
        return service;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[local=" + session.getLocalAddress() + ", remote=" + session.getRemoteAddress() + "]";
    }
}
