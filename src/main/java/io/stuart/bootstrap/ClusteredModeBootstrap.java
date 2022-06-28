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

package io.stuart.bootstrap;

import io.stuart.config.Config;
import io.stuart.context.ApplicationContext;
import io.stuart.context.StuartVerticleFactory;
import io.stuart.entities.internal.MqttMessageTuple;
import io.stuart.entities.internal.codec.MqttMessageTupleCodec;
import io.stuart.exceptions.StartException;
import io.stuart.log.Logger;
import io.stuart.services.cache.CacheService;
import io.stuart.utils.VertxUtil;
import io.stuart.verticles.admin.WebAdminVerticle;
import io.stuart.verticles.mqtt.ClsTcpMqttVerticle;
import io.stuart.verticles.mqtt.ClsWspMqttVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.ignite.IgniteClusterManager;

public class ClusteredModeBootstrap implements ApplicationBootstrap {

    private Vertx vertx;

    // private Timer timer = new Timer();

    private ApplicationContext applicationContext;

    @Override
    public void start() {
        Logger.log().info("Stuart's clustered instance is starting...");

        // init context
        applicationContext = new ApplicationContext();

        CacheService cacheService = applicationContext.getCacheService();
        // get vert.x cluster manager
        ClusterManager clusterManager = new IgniteClusterManager(cacheService.getIgnite());

        // get vert.x options
        VertxOptions vertxOptions = VertxUtil.vertxOptions();
        // set vert.x cluster manager
        vertxOptions.setClusterManager(clusterManager);

        Vertx.clusteredVertx(vertxOptions, result -> {
            if (result.succeeded()) {
                // set vert.x instance
                vertx = result.result();
                // set mqtt message tuple codec
                vertx.eventBus().registerDefaultCodec(MqttMessageTuple.class, new MqttMessageTupleCodec());

                applicationContext.start(vertx);

                // get vertx. deployment options
                DeploymentOptions deploymentOptions = VertxUtil.vertxDeploymentOptions(vertxOptions, null);

                // deploy the clustered tcp mqtt verticle
                vertx.deployVerticle(StuartVerticleFactory.verticleName(ClsTcpMqttVerticle.class), deploymentOptions,
                    ar -> {
                        if (ar.succeeded()) {
                            Logger.log().info(
                                "Stuart's MQTT TCP protocol verticle(s) deploy succeeded, listen at port {}.",
                                Config.getMqttPort());
                        } else {
                            Logger.log().error("Stuart's MQTT TCP protocol verticle(s) deploy failed, excpetion: {}.",
                                ar.cause().getMessage());
                        }
                    });

                // deploy the clustered wsp mqtt verticle
                vertx.deployVerticle(StuartVerticleFactory.verticleName(ClsWspMqttVerticle.class), deploymentOptions,
                    ar -> {
                        if (ar.succeeded()) {
                            Logger.log().info(
                                "Stuart's MQTT WSP protocol verticle(s) deploy succeeded, listen at port {}.",
                                Config.getWsPort());
                        } else {
                            Logger.log().error("Stuart's MQTT WSP protocol verticle(s) deploy failed, excpetion: {}.",
                                ar.cause().getMessage());
                        }
                    });

                // deploy the web verticle
                vertx.deployVerticle(StuartVerticleFactory.verticleName(WebAdminVerticle.class), ar -> {
                    if (ar.succeeded()) {
                        Logger.log().info("Stuart's WEB management verticle deploy succeeded, listen at port {}.",
                            Config.getHttpPort());
                    } else {
                        Logger.log().error("Stuart's WEB management verticle deploy failed, excpetion: {}.",
                            ar.cause().getMessage());
                    }
                });

                vertx.setPeriodic(Config.getInstanceMetricsPeriodMs(), v -> {
                    cacheService.updateNodeSysRuntimeInfo();
                });

                // set scheduled task
                // timer.schedule(new SysRuntimeInfoTask(cacheService), 0, Config.getInstanceMetricsPeriodMs());
            } else {
                Logger.log().error("Stuart's clustered vert.x instance start failed, exception: {}.", result.cause());

                throw new StartException(result.cause());
            }
        });
    }

    @Override
    public void stop() {
        if (vertx != null) {
            vertx.close().onComplete(v -> {
                if (applicationContext != null) {
                    applicationContext.stop();
                }
            });
        }
    }
}
