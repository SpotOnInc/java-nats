// Copyright 2015-2018 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.nats.client.impl;

import java.io.IOException;

/**
 * A data port represents the connection to the network. This could have been called
 * transport but that seemed too big a concept. This interface just allows a wrapper around
 * the core communication code.
 */
public interface DataPort {
    public void connect(String serverURI, NatsConnection conn) throws IOException;

    /**
     * Upgrade the port to SSL. If it is already secured, this is a no-op.
     * If the data port type doesn't support SSL it should throw an exception.
     * 
     * @throws IOException if the data port is unable to upgrade.
     */
    public void upgradeToSecure() throws IOException;

    public int read(byte[] dst, int off, int len) throws IOException;

    public void write(byte[] src, int toWrite) throws IOException;

    public void close() throws IOException;
}