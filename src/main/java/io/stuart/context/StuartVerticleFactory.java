/*
 * Copyright (C) 2020~2022 dinstone<dinstone@163.com>
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

package io.stuart.context;

import java.util.concurrent.Callable;

import io.stuart.verticles.admin.WebAdminVerticle;
import io.stuart.verticles.mqtt.ClsTcpMqttVerticle;
import io.stuart.verticles.mqtt.ClsWspMqttVerticle;
import io.stuart.verticles.mqtt.StdTcpMqttVerticle;
import io.stuart.verticles.mqtt.StdWspMqttVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Verticle;
import io.vertx.core.spi.VerticleFactory;

public class StuartVerticleFactory implements VerticleFactory {

    private static final String AGATE_PREFIX = "stuart";

    private ApplicationContext context;

    public static String verticleName(Class<?> clazz) {
        return AGATE_PREFIX + ":" + clazz.getName();
    }

    public StuartVerticleFactory(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public String prefix() {
        return AGATE_PREFIX;
    }

    @Override
    public void createVerticle(String verticleName, ClassLoader classLoader, Promise<Callable<Verticle>> promise) {
        promise.complete(new Callable<Verticle>() {

            @Override
            public Verticle call() throws Exception {
                return createVerticle(verticleName);
            }
        });

    }

    private Verticle createVerticle(String verticleName) {
        String clazz = VerticleFactory.removePrefix(verticleName);
        if (WebAdminVerticle.class.getName().equals(clazz)) {
            return new WebAdminVerticle(context);
        } else if (StdTcpMqttVerticle.class.getName().equals(clazz)) {
            return new StdTcpMqttVerticle(context);
        } else if (StdWspMqttVerticle.class.getName().equals(clazz)) {
            return new StdWspMqttVerticle(context);
        } else if (ClsTcpMqttVerticle.class.getName().equals(clazz)) {
            return new ClsTcpMqttVerticle(context);
        } else if (ClsWspMqttVerticle.class.getName().equals(clazz)) {
            return new ClsWspMqttVerticle(context);
        }

        throw new IllegalArgumentException("unsupported verticle type: " + clazz);
    }

}
