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

import io.stuart.enums.Access;
import io.stuart.enums.Authority;

public class MqttAuthority implements Serializable {

    private static final long serialVersionUID = -5135604764912692767L;

    private String topic;

    private int qos;

    private Access access;

    private Authority authority;

    
    public String getTopic() {
        return topic;
    }

    
    public void setTopic(String topic) {
        this.topic = topic;
    }

    
    public int getQos() {
        return qos;
    }

    
    public void setQos(int qos) {
        this.qos = qos;
    }

    
    public Access getAccess() {
        return access;
    }

    
    public void setAccess(Access access) {
        this.access = access;
    }

    
    public Authority getAuthority() {
        return authority;
    }

    
    public void setAuthority(Authority authority) {
        this.authority = authority;
    }
    
    

}
