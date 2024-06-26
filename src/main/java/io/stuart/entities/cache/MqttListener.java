/*
 * Copyright 2019 Yang Wang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.stuart.entities.cache;

import java.io.Serializable;

import org.apache.ignite.cache.query.annotations.QuerySqlField;

/**
 * protocol => AbstractMqttVerticle.protocol <BR/>
 * addressAndPort => AbstractMqttVerticle.listener
 */
public class MqttListener implements Serializable {

    private static final long serialVersionUID = 8907305770392418501L;

    @QuerySqlField
    private String protocol;

    @QuerySqlField
    private String addressAndPort;

    private int connMaxLimit;

    private int connCount;

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getAddressAndPort() {
        return addressAndPort;
    }

    public void setAddressAndPort(String addressAndPort) {
        this.addressAndPort = addressAndPort;
    }

    public int getConnMaxLimit() {
        return connMaxLimit;
    }

    public void setConnMaxLimit(int connMaxLimit) {
        this.connMaxLimit = connMaxLimit;
    }

    public int getConnCount() {
        return connCount;
    }

    public void setConnCount(int connCount) {
        this.connCount = connCount;
    }

}
