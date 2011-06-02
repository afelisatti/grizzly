/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.grizzly.websockets;

import com.sun.grizzly.BaseSelectionKeyHandler;
import com.sun.grizzly.arp.AsyncExecutor;
import com.sun.grizzly.arp.AsyncProcessorTask;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.LogMessages;
import com.sun.grizzly.util.Utils;
import com.sun.grizzly.util.http.MimeHeaders;

import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketEngine {
    public static final String SEC_WS_ACCEPT = "Sec-WebSocket-Accept";
    public static final String SEC_WS_KEY_HEADER = "Sec-WebSocket-Key";
    public static final String SEC_WS_ORIGIN_HEADER = "Sec-WebSocket-Origin";
    public static final String SEC_WS_PROTOCOL_HEADER = "Sec-WebSocket-Protocol";
    public static final String SEC_WS_EXTENSIONS_HEADER = "Sec-WebSocket-Extensions";
    public static final String SEC_WS_VERSION = "Sec-WebSocket-Version";
    public static final String WEBSOCKET = "websocket";
    public static final String RESPONSE_CODE_HEADER = "Response Code";
    public static final String RESPONSE_CODE_MESSAGE = "Switching Protocols";
    public static final String RESPONSE_CODE_VALUE = "101";
    public static final String UPGRADE = "upgrade";
    public static final String CONNECTION = "connection";
    public static final Version DEFAULT_VERSION = Version.DRAFT06;
    public static final int WS_VERSION = 6;
    public static final int INITIAL_BUFFER_SIZE = 8192;
    public static final int DEFAULT_TIMEOUT;

    // Draft 76 headers
    public static final String SEC_WS_KEY1_HEADER = "Sec-WebSocket-Key1";
    public static final String SEC_WS_KEY2_HEADER = "Sec-WebSocket-Key2";
    public static final String CLIENT_WS_ORIGIN_HEADER = "Origin";
    public static final String SERVER_SEC_WS_ORIGIN_HEADER = "Sec-WebSocket-Origin";
    public static final String SERVER_SEC_WS_LOCATION_HEADER = "Sec-WebSocket-Location";


    private static final WebSocketEngine engine = new WebSocketEngine();
    private static volatile boolean isWebSocketEnabled = true;
    static final Logger logger = Logger.getLogger(WebSocketEngine.WEBSOCKET);
    public static final String SERVER_KEY_HASH = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    public static final int MASK_SIZE = 4;
    private final List<WebSocketApplication> applications = new ArrayList<WebSocketApplication>();
    private final WebSocketCloseHandler closeHandler = new WebSocketCloseHandler();

    static {
        if (Utils.isDebugVM()) {
            DEFAULT_TIMEOUT = 900;
        } else {
            DEFAULT_TIMEOUT = 30;
        }
    }

    private WebSocketEngine() {
    }

    /**
     * @return true is WebSockets are enabled.
     */
    public static boolean isWebSocketEnabled() {
        return isWebSocketEnabled;
    }

    public static void setWebSocketEnabled(boolean webSocketEnabled) {
        isWebSocketEnabled = webSocketEnabled;
    }

    public static WebSocketEngine getEngine() {
        return engine;
    }

    public static byte[] toArray(long length) {
        long value = length;
        byte[] b = new byte[8];
        for (int i = 7; i >= 0 && value > 0; i--) {
            b[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return b;
    }

    public static long toLong(byte[] bytes, int start, int end) {
        long value = 0;
        for (int i = start; i < end; i++) {
            value <<= 8;
            value ^= (long) bytes[i] & 0xFF;
        }
        return value;
    }

    public WebSocketApplication getApplication(Request request) {
        for (WebSocketApplication application : applications) {
            if (application.isApplicationRequest(request)) {
                return application;
            }
        }
        return null;
    }

    public boolean upgrade(AsyncExecutor asyncExecutor) {
        Request request = asyncExecutor.getProcessorTask().getRequest();
        final MimeHeaders mimeHeaders = request.getMimeHeaders();
        if (isUpgradable(request)) {
            try {
                final WebSocketApplication app = WebSocketEngine.getEngine().getApplication(request);
                WebSocket socket = null;
                try {
                    if (app != null) {
                        final WebSocketHandler webSocketHandler = WebSocketFactory.loadHandler(mimeHeaders);

                        ProcessorTask task = asyncExecutor.getProcessorTask();
                        final SelectionKey key = task.getSelectionKey();

                        final ServerNetworkHandler handler = new ServerNetworkHandler(request, request.getResponse());
                        webSocketHandler.setNetworkHandler(handler);

                        final HandShake handshake = webSocketHandler.handshake(app, request);

                        socket = new BaseServerWebSocket(webSocketHandler, handler, app, new KeyWebSocketListener(key));
                        socket.onConnect();

                        ((BaseSelectionKeyHandler) task.getSelectorHandler().getSelectionKeyHandler())
                                .setConnectionCloseHandler(closeHandler);

                        key.attach(new WebSocketSelectionKeyAttachment(webSocketHandler, handler, task,
                                (AsyncProcessorTask) asyncExecutor.getAsyncTask()));
                        
                        enableRead(task, key);
                        return true;
                    }
                } catch (HandshakeException e) {
                    logger.log(Level.SEVERE, e.getMessage(), e);
                    socket.close();
                }
            } catch (WebSocketException e) {
                return false;
            }
        }

        return false;

    }

    private boolean isUpgradable(Request request) {
        final String s = request.getHeader("Upgrade");
        return "WebSocket".equalsIgnoreCase(s);
    }

    final void enableRead(ProcessorTask task, SelectionKey key) {
        task.getSelectorHandler().register(key, SelectionKey.OP_READ);
    }

    @Deprecated
    public void register(String name, WebSocketApplication app) {
        register(app);
    }

    public void register(WebSocketApplication app) {
        if (!isWebSocketEnabled()) {
            throw new IllegalStateException(LogMessages.SEVERE_GRIZZLY_WS_NOT_ENABLED());
        }

        applications.add(app);
    }

    public void unregister(WebSocketApplication app) {
        applications.remove(app);
    }

}
