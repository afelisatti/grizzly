/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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

import com.sun.grizzly.util.http.MimeHeaders;
import com.sun.grizzly.websockets.draft06.Draft06Handler;
import com.sun.grizzly.websockets.draft07.Draft07Handler;
import com.sun.grizzly.websockets.draft08.Draft08Handler;import com.sun.grizzly.websockets.draft76.Draft76Handler;
import com.sun.grizzly.websockets.draft76.HandShake76;

public enum Version {
    DRAFT17("13") {
            @Override
            public ProtocolHandler createHandler(boolean mask) {
                return new Draft08Handler(mask);
            }

            @Override
            public boolean validate(MimeHeaders headers) {
                return "13".equals(headers.getHeader(WebSocketEngine.SEC_WS_VERSION));
            }
        },
    DRAFT08("8") {
        @Override
        public ProtocolHandler createHandler(boolean mask) {
            return new Draft08Handler(mask);
        }

        @Override
        public boolean validate(MimeHeaders headers) {
            return "8".equals(headers.getHeader(WebSocketEngine.SEC_WS_VERSION));
        }
    },
    DRAFT07("7") {
        @Override
        public ProtocolHandler createHandler(boolean mask) {
            return new Draft07Handler(mask);
        }

        @Override
        public boolean validate(MimeHeaders headers) {
            return "7".equals(headers.getHeader(WebSocketEngine.SEC_WS_VERSION));
        }
    },
    DRAFT06("6") {
        @Override
        public ProtocolHandler createHandler(boolean mask) {
            return new Draft06Handler(mask);
        }

        @Override
        public boolean validate(MimeHeaders headers) {
            return "6".equals(headers.getHeader(WebSocketEngine.SEC_WS_VERSION));
        }
    },
    DRAFT76("") {
        @Override
        public ProtocolHandler createHandler(boolean mask) {
            return new Draft76Handler();
        }

        @Override
        public boolean validate(MimeHeaders headers) {
            return headers.getHeader(HandShake76.SEC_WS_KEY1_HEADER) != null;
        }

        @Override
        public boolean isFragmentationSupported() {
            return false;
        }
    };

    public abstract ProtocolHandler createHandler(boolean mask);

    public abstract boolean validate(MimeHeaders headers);

    private String wireProtocolVersion;
    
    private Version(final String wireProtocolVersion) {
        this.wireProtocolVersion = wireProtocolVersion;
    }

    @Override
    public String toString() {
        return name();
    }

    public boolean isFragmentationSupported() {
        return true;
    }
    
    public static String getSupportedWireProtocolVersions() {
        final StringBuilder sb = new StringBuilder();
        for (Version v : Version.values()) {
            if (v.wireProtocolVersion.length() > 0) {
                sb.append(v.wireProtocolVersion).append(", ");
            }
        }
        return sb.substring(0, sb.length() - 2);
            
    }
}