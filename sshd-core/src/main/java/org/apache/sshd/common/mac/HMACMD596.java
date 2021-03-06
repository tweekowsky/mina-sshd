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
package org.apache.sshd.common.mac;

import org.apache.sshd.common.Mac;
import org.apache.sshd.common.NamedFactory;

/**
 * HMAC-MD5-96 <code>Mac</code>
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class HMACMD596 extends BaseMac {

    /**
     * Named factory for the HMAC-MD5-96 <code>Mac</code>
     */
    public static class Factory implements NamedFactory<Mac> {

        public String getName() {
            return "hmac-md5-96";
        }

        public Mac create() {
            return new HMACMD596();
        }
    }

    public HMACMD596() {
        super("HmacMD5", 12, 16);
    }

}
