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
package org.apache.sshd;

import java.io.IOError;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.sshd.common.SshdSocketAddress;
import org.apache.sshd.util.BaseTest;
import org.apache.sshd.util.BogusForwardingFilter;
import org.apache.sshd.util.BogusPasswordAuthenticator;
import org.apache.sshd.util.EchoShellFactory;
import org.apache.sshd.util.Utils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.apache.sshd.util.Utils.getFreePort;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Port forwarding tests
 */
public class ProxyTest extends BaseTest {

    private final org.slf4j.Logger log = LoggerFactory.getLogger(getClass());

    private SshServer sshd;
    private int sshPort;
    private int echoPort;
    private IoAcceptor acceptor;
    private SshClient client;

    @Before
    public void setUp() throws Exception {
        sshPort = getFreePort();
        echoPort = getFreePort();

        sshd = SshServer.setUpDefaultServer();
        sshd.getProperties().put(SshServer.WINDOW_SIZE, "2048");
        sshd.getProperties().put(SshServer.MAX_PACKET_SIZE, "256");
        sshd.setPort(sshPort);
        sshd.setKeyPairProvider(Utils.createTestHostKeyProvider());
        sshd.setShellFactory(new EchoShellFactory());
        sshd.setPasswordAuthenticator(new BogusPasswordAuthenticator());
        sshd.setTcpipForwardingFilter(new BogusForwardingFilter());
        sshd.start();

        NioSocketAcceptor acceptor = new NioSocketAcceptor();
        acceptor.setHandler(new IoHandlerAdapter() {
            @Override
            public void messageReceived(IoSession session, Object message) throws Exception {
                IoBuffer recv = (IoBuffer) message;
                IoBuffer sent = IoBuffer.allocate(recv.remaining());
                sent.put(recv);
                sent.flip();
                session.write(sent);
            }
        });
        acceptor.setReuseAddress(true);
        acceptor.bind(new InetSocketAddress(echoPort));
        this.acceptor = acceptor;

    }

    @After
    public void tearDown() throws Exception {
        if (sshd != null) {
            sshd.stop(true);
        }
        if (acceptor != null) {
            acceptor.dispose(true);
        }
        if (client != null) {
            client.stop();
        }
    }

    @Test
    public void testSocksProxy() throws Exception {
        ClientSession session = createNativeSession();

        SshdSocketAddress dynamic = session.startDynamicPortForwarding(new SshdSocketAddress("localhost", 0));

        for (int i = 0; i < 10; i++) {
            Socket s = new Socket(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", dynamic.getPort())));
            s.connect(new InetSocketAddress("localhost", echoPort));
            s.getOutputStream().write("foo".getBytes());
            s.getOutputStream().flush();
            byte[] buf = new byte[1024];
            int l = s.getInputStream().read(buf);
            s.close();
            assertEquals("foo", new String(buf, 0, l));
        }

        session.stopDynamicPortForwarding(dynamic);

        try {
            Socket s = new Socket(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", dynamic.getPort())));
            s.connect(new InetSocketAddress("localhost", echoPort));
            s.getOutputStream().write("foo".getBytes());
            fail("Expected IOException");
        } catch (IOException e) {
            // expected
        }

        session.close(false).await();
    }

    protected ClientSession createNativeSession() throws Exception {
        client = SshClient.setUpDefaultClient();
        client.getProperties().put(SshServer.WINDOW_SIZE, "2048");
        client.getProperties().put(SshServer.MAX_PACKET_SIZE, "256");
        client.setTcpipForwardingFilter(new BogusForwardingFilter());
        client.start();

        ClientSession session = client.connect("sshd", "localhost", sshPort).await().getSession();
        session.addPasswordIdentity("sshd");
        session.auth().verify();
        return session;
    }


}


