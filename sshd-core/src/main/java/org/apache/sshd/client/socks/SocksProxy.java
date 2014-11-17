/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sshd.client.socks;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.sshd.client.future.OpenFuture;
import org.apache.sshd.common.Closeable;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.SshdSocketAddress;
import org.apache.sshd.common.forward.TcpipClientChannel;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.IoHandler;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.util.*;

/**
 * Socks Proxy implementation.
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class SocksProxy extends CloseableUtils.AbstractInnerCloseable {

    private final ConnectionService service;
    private final Session session;
    private final SshdSocketAddress boundAddress;
    private final Map<IoSession, Proxy> proxies = new ConcurrentHashMap<IoSession, Proxy>();
    protected IoAcceptor acceptor;

    public SocksProxy(ConnectionService service, SshdSocketAddress local) throws IOException {
        if (service == null) {
            throw new IllegalArgumentException("Connection service is null");
        }
        if (local == null) {
            throw new IllegalArgumentException("Local address is null");
        }
        this.service = service;
        this.session = service.getSession();
        this.boundAddress = doBind(local);
    }

    public SshdSocketAddress getAddress() {
        return boundAddress;
    }

    public synchronized void close() {
        close(true);
    }

    @Override
    protected synchronized Closeable getInnerCloseable() {
        return builder().close(acceptor).build();
    }


    //
    // IoHandler implementation
    //
    private class SocksIoHandler implements IoHandler {

        public void sessionCreated(IoSession session) throws Exception {
        }

        public void sessionClosed(IoSession session) throws Exception {
            Proxy proxy = proxies.remove(session);
            if (proxy != null) {
                proxy.close();
            }
        }

        public void messageReceived(final IoSession session, org.apache.sshd.common.util.Readable message) throws Exception {
            Buffer buffer = new Buffer(message.available());
            buffer.putBuffer(message);
            Proxy proxy = proxies.get(session);
            if (proxy == null) {
                int version = buffer.getByte();
                if (version == 0x04) {
                    proxy = new Socks4(session);
                } else if (version == 0x05) {
                    proxy = new Socks5(session);
                } else {
                    throw new IllegalStateException("Unsupported version: " + version);
                }
                proxy.onMessage(buffer);
                proxies.put(session, proxy);
            } else {
                proxy.onMessage(buffer);
            }
        }

        public void exceptionCaught(IoSession ioSession, Throwable cause) throws Exception {
            log.warn("Exception caught, closing socks proxy", cause);
            session.close(false);
        }

    }

    //
    // Private methods
    //

    private SshdSocketAddress doBind(SshdSocketAddress address) throws IOException {
        if (acceptor == null) {
            acceptor = session.getFactoryManager().getIoServiceFactory().createAcceptor(new SocksIoHandler());
        }
        Set<SocketAddress> before = acceptor.getBoundAddresses();
        try {
            acceptor.bind(address.toInetSocketAddress());
            Set<SocketAddress> after = acceptor.getBoundAddresses();
            after.removeAll(before);
            if (after.isEmpty()) {
                throw new IOException("Error binding to " + address + ": no local addresses bound");
            }
            if (after.size() > 1) {
                throw new IOException("Multiple local addresses have been bound for " + address);
            }
            InetSocketAddress result = (InetSocketAddress) after.iterator().next();
            return new SshdSocketAddress(address.getHostName(), result.getPort());
        } catch (IOException bindErr) {
            if (acceptor.getBoundAddresses().isEmpty()) {
                close();
            }
            throw bindErr;
        }
    }

    public String toString() {
        return getClass().getSimpleName() + "[" + session + "]";
    }

    public abstract class Proxy {

        public static final int SOCKS_SUCCESS		     = 0;
        public static final int SOCKS_FAILURE		     = 1;
        public static final int SOCKS_BADCONNECT		 = 2;
        public static final int SOCKS_BADNETWORK		 = 3;
        public static final int SOCKS_HOST_UNREACHABLE	 = 4;
        public static final int SOCKS_CONNECTION_REFUSED = 5;
        public static final int SOCKS_TTL_EXPIRE		 = 6;
        public static final int SOCKS_CMD_NOT_SUPPORTED	 = 7;
        public static final int SOCKS_ADDR_NOT_SUPPORTED = 8;

        public static final int SOCKS_NO_PROXY		      = 1 << 16;
        public static final int SOCKS_PROXY_NO_CONNECT	  = 2 << 16;
        public static final int SOCKS_PROXY_IO_ERROR	  = 3 << 16;
        public static final int SOCKS_AUTH_NOT_SUPPORTED  = 4 << 16;
        public static final int SOCKS_AUTH_FAILURE		  = 5 << 16;
        public static final int SOCKS_JUST_ERROR		  = 6 << 16;
        public static final int SOCKS_DIRECT_FAILED		  = 7 << 16;
        public static final int SOCKS_METHOD_NOTSUPPORTED = 8 << 16;


        static final int SOCKS_CMD_CONNECT 		 = 0x1;
        static final int SOCKS_CMD_BIND		     = 0x2;
        static final int SOCKS_CMD_UDP_ASSOCIATE = 0x3;

        IoSession session;
        TcpipClientChannel channel;

        protected Proxy(IoSession session) {
            this.session = session;
        }

        protected abstract void onMessage(Buffer buffer) throws IOException;

        public void close() {
            if (channel != null) {
                channel.close(false);
            }
        }
    }

    public class Socks4 extends Proxy {
        public Socks4(IoSession session) {
            super(session);
        }

        @Override
        protected void onMessage(Buffer buffer) throws IOException {
            if (channel == null) {
                int cmd = buffer.getByte();
                if (cmd != SOCKS_CMD_CONNECT) {
                    throw new IllegalStateException("Unsupported socks command: " + cmd);
                }
                int port = (((int) buffer.getByte()) << 8) & 0xFF00 + ((int) buffer.getByte()) & 0x00FF;
                String host = Integer.toString(buffer.getByte() & 0xFF) + "."
                        + Integer.toString(buffer.getByte() & 0xFF) + "."
                        + Integer.toString(buffer.getByte() & 0xFF) + "."
                        + Integer.toString(buffer.getByte() & 0xFF);
                String userId = getNTString(buffer);
                // Socks4a
                if (host.startsWith("0.0.0.")) {
                    host = getNTString(buffer);
                }
                SshdSocketAddress remote = new SshdSocketAddress(host, port == 0 ? 80 : port);
                channel = new TcpipClientChannel(TcpipClientChannel.Type.Direct, session, remote);
                service.registerChannel(channel);
                channel.open().addListener(new SshFutureListener<OpenFuture>() {
                    public void operationComplete(OpenFuture future) {
                        Buffer buffer = new Buffer(8);
                        buffer.putByte((byte) 0x00);
                        Throwable t = future.getException();
                        if (t != null) {
                            service.unregisterChannel(channel);
                            channel.close(false);
                            buffer.putByte((byte) 0x5b);
                        } else {
                            buffer.putByte((byte) 0x5a);
                        }
                        buffer.putByte((byte) 0x00);
                        buffer.putByte((byte) 0x00);
                        buffer.putByte((byte) 0x00);
                        buffer.putByte((byte) 0x00);
                        buffer.putByte((byte) 0x00);
                        buffer.putByte((byte) 0x00);
                        session.write(buffer);
                    }
                });
            } else {
                channel.getInvertedIn().write(buffer.array(), buffer.rpos(), buffer.available());
                channel.getInvertedIn().flush();
            }
        }

        private String getNTString(Buffer buffer) {
            StringBuilder sb = new StringBuilder();
            char c;
            while ((c = (char) (buffer.getByte() & 0xFF)) != 0) {
                sb.append(c);
            }
            return sb.toString();
        }

    }

    public class Socks5 extends Proxy {

        byte[] authMethods;
        Buffer response;

        public Socks5(IoSession session) {
            super(session);
        }

        @Override
        protected void onMessage(Buffer buffer) throws IOException {
            if (authMethods == null) {
                int nbAuthMethods = buffer.getByte() & 0xFF;
                authMethods = new byte[nbAuthMethods];
                buffer.getRawBytes(authMethods);
                boolean foundNoAuth = false;
                for (int i = 0; i < nbAuthMethods; i++) {
                    foundNoAuth |= authMethods[i] == 0;
                }
                buffer = new Buffer(8);
                buffer.putByte((byte) 0x05);
                buffer.putByte((byte) (foundNoAuth ? 0x00 : 0xFF));
                session.write(buffer);
                if (!foundNoAuth) {
                    session.close(false);
                }
            } else if (channel == null) {
                response = buffer;
                int version = buffer.getByte() & 0xFF;
                if (version != 0x05) {
                    throw new IllegalStateException("Unexpected version: " + version);
                }
                int cmd = buffer.getByte();
                if (cmd != SOCKS_CMD_CONNECT) {
                    throw new IllegalStateException("Unsupported socks command: " + cmd);
                }
                final int res = buffer.getByte();
                int type = buffer.getByte();
                String host;
                if (type == 0x01) {
                    host = Integer.toString(buffer.getByte() & 0xFF) + "."
                            + Integer.toString(buffer.getByte() & 0xFF) + "."
                            + Integer.toString(buffer.getByte() & 0xFF) + "."
                            + Integer.toString(buffer.getByte() & 0xFF);
                } else if (type == 0x03) {
                    host = getBLString(buffer);
                } else if (type == 0x04) {
                    host = Integer.toHexString((((int) buffer.getByte()) << 8) & 0xFF00 + ((int) buffer.getByte()) & 0x00FF)
                            + ":" + Integer.toHexString((((int) buffer.getByte()) << 8) & 0xFF00 + ((int) buffer.getByte()) & 0x00FF)
                            + ":" + Integer.toHexString((((int) buffer.getByte()) << 8) & 0xFF00 + ((int) buffer.getByte()) & 0x00FF)
                            + ":" + Integer.toHexString((((int) buffer.getByte()) << 8) & 0xFF00 + ((int) buffer.getByte()) & 0x00FF)
                            + ":" + Integer.toHexString((((int) buffer.getByte()) << 8) & 0xFF00 + ((int) buffer.getByte()) & 0x00FF)
                            + ":" + Integer.toHexString((((int) buffer.getByte()) << 8) & 0xFF00 + ((int) buffer.getByte()) & 0x00FF)
                            + ":" + Integer.toHexString((((int) buffer.getByte()) << 8) & 0xFF00 + ((int) buffer.getByte()) & 0x00FF)
                            + ":" + Integer.toHexString((((int) buffer.getByte()) << 8) & 0xFF00 + ((int) buffer.getByte()) & 0x00FF);
                } else {
                    throw new IllegalStateException("Unsupported address type: " + type);
                }
                int port = (((int) buffer.getByte()) << 8) & 0xFF00 + ((int) buffer.getByte()) & 0x00FF;
                SshdSocketAddress remote = new SshdSocketAddress(host, port == 0 ? 80 : port);
                channel = new TcpipClientChannel(TcpipClientChannel.Type.Direct, session, remote);
                service.registerChannel(channel);
                channel.open().addListener(new SshFutureListener<OpenFuture>() {
                    public void operationComplete(OpenFuture future) {
                        int wpos = response.wpos();
                        response.rpos(0);
                        response.wpos(1);
                        Throwable t = future.getException();
                        if (t != null) {
                            service.unregisterChannel(channel);
                            channel.close(false);
                            response.putByte((byte) 0x01);
                        } else {
                            response.putByte((byte) 0x00);
                        }
                        response.wpos(wpos);
                        session.write(response);
                    }
                });
            } else {
                channel.getInvertedIn().write(buffer.array(), buffer.rpos(), buffer.available());
                channel.getInvertedIn().flush();
            }
        }

        private String getBLString(Buffer buffer) {
            int length = buffer.getByte() & 0xFF;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < length; i++) {
                sb.append((char) (buffer.getByte() & 0xFF));
            }
            return sb.toString();
        }

    }

}
