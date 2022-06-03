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

import java.util.Timer;

import io.stuart.config.Config;
import io.stuart.context.ApplicationContext;
import io.stuart.log.Logger;
import io.stuart.tasks.SysRuntimeInfoTask;
import io.stuart.utils.VertxUtil;
import io.stuart.verticles.admin.WebAdminVerticle;
import io.stuart.verticles.mqtt.StdSslMqttVerticle;
import io.stuart.verticles.mqtt.StdTcpMqttVerticle;
import io.stuart.verticles.mqtt.StdWspMqttVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystemOptions;

public class StandaloneModeBootstrap implements ApplicationBootstrap {

    private Vertx vertx;

    private Timer timer = new Timer();

    private ApplicationContext applicationContext;

    @Override
    public void start() {
        Logger.log().info("Stuart's standalone instance is starting...");

        // vert.x options
        VertxOptions vertxOptions = VertxUtil.vertxOptions()
            .setFileSystemOptions(new FileSystemOptions().setFileCachingEnabled(false));
        // start vert.x instance
        vertx = Vertx.vertx(vertxOptions);

        applicationContext = new ApplicationContext();
        applicationContext.start(vertx);

        // vertx. deployment options
        DeploymentOptions deploymentOptions = VertxUtil.vertxDeploymentOptions(vertxOptions, null);
        // deploy the web verticle
        vertx.deployVerticle(StuartVerticleFactory.verticleName(WebAdminVerticle.class),
            new DeploymentOptions().setInstances(2), ar -> {
                if (ar.succeeded()) {
                    Logger.log().info("Stuart's WEB management verticle deploy succeeded, listen at port {}.",
                        Config.getHttpPort());
                } else {
                    Logger.log().error("Stuart's WEB management verticle deploy failed, excpetion: {}.",
                        ar.cause().getMessage());
                }
            });

        // deploy the standalone tcp mqtt verticle
        vertx.deployVerticle(StuartVerticleFactory.verticleName(StdTcpMqttVerticle.class), deploymentOptions, ar -> {
            if (ar.succeeded()) {
                Logger.log().info("Stuart's MQTT TCP protocol verticle(s) deploy succeeded, listen at port {}.",
                    Config.getMqttPort());
            } else {
                Logger.log().error("Stuart's MQTT TCP protocol verticle(s) deploy failed, excpetion: {}.",
                    ar.cause().getMessage());
            }
        });

        // deploy the standalone tcp mqtt verticle
        vertx.deployVerticle(StuartVerticleFactory.verticleName(StdWspMqttVerticle.class), deploymentOptions, ar -> {
            if (ar.succeeded()) {
                Logger.log().info("Stuart's MQTT WSP protocol verticle(s) deploy succeeded, listen at port {}.",
                    Config.getWsPort());
            } else {
                Logger.log().error("Stuart's MQTT WSP protocol verticle(s) deploy failed, excpetion: {}.",
                    ar.cause().getMessage());
            }
        });

        // if enable mqtt ssl protocol
        if (Config.isMqttSslEnable()) {
            // deploy the standalone ssl mqtt verticle
            vertx.deployVerticle(StuartVerticleFactory.verticleName(StdSslMqttVerticle.class), deploymentOptions,
                ar -> {
                    if (ar.succeeded()) {
                        Logger.log().info("Stuart's MQTT SSL protocol verticle(s) deploy succeeded, listen at port {}.",
                            Config.getMqttSslPort());
                    } else {
                        Logger.log().error("Stuart's MQTT SSL protocol verticle(s) deploy failed, excpetion: {}.",
                            ar.cause().getMessage());
                    }
                });
        }

        // set scheduled task
        timer.schedule(new SysRuntimeInfoTask(applicationContext.getCacheService()), 0,
            Config.getInstanceMetricsPeriodMs());
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
