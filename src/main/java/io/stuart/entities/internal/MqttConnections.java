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

package io.stuart.entities.internal;

import java.io.Serializable;
import java.util.List;

import io.stuart.entities.cache.MqttConnection;

public class MqttConnections implements Serializable {

    private static final long serialVersionUID = -7744483425215150918L;

    private int total;

    private List<MqttConnection> connections;

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<MqttConnection> getConnections() {
        return connections;
    }

    public void setConnections(List<MqttConnection> connections) {
        this.connections = connections;
    }

    public static long getSerialversionuid() {
        return serialVersionUID;
    }

}
