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
package org.apache.sshd.server.command;

import java.util.concurrent.ExecutorService;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;

/**
 * This <code>CommandFactory</code> can be used as a standalone command factory
 * or can be used to augment another <code>CommandFactory</code> and provides
 * <code>SCP</code> support.
 *
 * @see ScpCommand
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class ScpCommandFactory implements CommandFactory {
	/**
	 * Command prefix used to identify SCP commands
	 */
    public static final String SCP_COMMAND_PREFIX = "scp";

    private CommandFactory delegate;
    private ExecutorService executors;
    private boolean shutdownExecutor;

    public ScpCommandFactory() {
        this(null, null);
    }

    /**
     * @param executorService An {@link ExecutorService} to be used when
     *        starting {@link ScpCommand} execution. If {@code null} an ad-hoc
     *        single-threaded service is created and used.
     */
    public ScpCommandFactory(ExecutorService executorService) {
        this(null, executorService);
    }

    /**
     * @param delegateFactory A {@link CommandFactory} to be used if the
     *        command is not an SCP one. If {@code null} then an {@link IllegalArgumentException}
     *        will be thrown when attempting to invoke {@link #createCommand(String)}
     *        with a non-SCP command
     * @see #SCP_COMMAND_PREFIX
     */
    public ScpCommandFactory(CommandFactory delegateFactory) {
        this(delegateFactory, null);
    }

    /**
     * @param delegateFactory A {@link CommandFactory} to be used if the
     *        command is not an SCP one. If {@code null} then an {@link IllegalArgumentException}
     *        will be thrown when attempting to invoke {@link #createCommand(String)}
     *        with a non-SCP command
     * @param executorService An {@link ExecutorService} to be used when
     *        starting {@link ScpCommand} execution. If {@code null} then a single-threaded
     *        ad-hoc service is used. <B>Note:</B> the service will <U>not</U> be shutdown
     *        when the command is terminated - unless it is the ad-hoc service, which will be
     *        shutdown regardless
     * @see #ScpCommandFactory(CommandFactory, ExecutorService, boolean)
     */
    public ScpCommandFactory(CommandFactory delegateFactory, ExecutorService executorService) {
        this(delegateFactory, executorService, false);
    }

    /**
     * @param delegateFactory A {@link CommandFactory} to be used if the
     *        command is not an SCP one. If {@code null} then an {@link IllegalArgumentException}
     *        will be thrown when attempting to invoke {@link #createCommand(String)}
     *        with a non-SCP command
     * @param executorService An {@link ExecutorService} to be used when
     *        starting {@link ScpCommand} execution. If {@code null} then a single-threaded
     *        ad-hoc service is used. <B>Note:</B> the service will <U>not</U> be shutdown
     *        when the command is terminated - unless it is the ad-hoc service, which will be
     *        shutdown regardless
     * @param shutdownOnExit If {@code true} the {@link ExecutorService#shutdownNow()}
     *        will be called when command terminates - unless it is the ad-hoc
     *        service, which will be shutdown regardless
     */
    public ScpCommandFactory(CommandFactory delegateFactory, ExecutorService executorService, boolean shutdownOnExit) {
        delegate = delegateFactory;
        executors = executorService;
        shutdownExecutor = shutdownOnExit;
    }

    public CommandFactory getDelegateCommandFactory() {
        return delegate;
    }

    public ExecutorService getExecutorService() {
        return executors;
    }

    public boolean isShutdownOnExit() {
        return shutdownExecutor;
    }

    /**
     * Parses a command string and verifies that the basic syntax is
     * correct. If parsing fails the responsibility is delegated to
     * the configured {@link CommandFactory} instance; if one exist.
     *
     * @param command command to parse 
     * @return configured {@link Command} instance
     * @throws IllegalArgumentException if not an SCP command and no
     *         delegate command factory is available
     * @see #SCP_COMMAND_PREFIX
     */
    public Command createCommand(String command) {
        try {
            if (!command.startsWith(SCP_COMMAND_PREFIX)) {
                throw new IllegalArgumentException("Unknown command, does not begin with '" + SCP_COMMAND_PREFIX + "': " + command);
            }

            return new ScpCommand(command, getExecutorService(), isShutdownOnExit());
        } catch (IllegalArgumentException iae) {
            if (delegate != null) {
                return delegate.createCommand(command);
            }
            throw iae;
        }
    }
}
