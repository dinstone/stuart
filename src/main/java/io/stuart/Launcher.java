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

package io.stuart;

import io.stuart.bootstrap.ClusteredModeBootstrap;
import io.stuart.bootstrap.StandaloneModeBootstrap;
import io.stuart.bootstrap.ApplicationBootstrap;
import io.stuart.config.Config;
import io.stuart.consts.ParamConst;
import io.stuart.consts.PropConst;
import io.stuart.hook.ShutdownHook;
import io.stuart.log.Logger;
import io.stuart.utils.CmdUtil;
import io.vertx.core.cli.CommandLine;

public class Launcher {

    public static void main(String[] args) {
        CommandLine commandLine = CmdUtil.cli(args);

        if (commandLine == null) {
            System.exit(-1);
        }

        // initialize server configurations
        Config.init(commandLine);
        // set server log directory system property
        System.setProperty(PropConst.LOG_DIR, Config.getLogDir());
        // set server log level system property
        System.setProperty(PropConst.LOG_LEVEL, Config.getLogLevel());

        // get cluster mode
        String clusterMode = Config.getClusterMode();
        // verticle service
        ApplicationBootstrap bootstrap = null;

        if (ParamConst.CLUSTER_MODE_STANDALONE.equalsIgnoreCase(clusterMode)) {
            // new standalone verticle service
            bootstrap = new StandaloneModeBootstrap();
        } else if (ParamConst.CLUSTER_MODE_VMIP.equalsIgnoreCase(clusterMode) || ParamConst.CLUSTER_MODE_ZK.equalsIgnoreCase(clusterMode)) {
            // new clustered verticle service
            bootstrap = new ClusteredModeBootstrap();
        }

        // check: verticle service is not null
        if (bootstrap != null) {
            Logger.log().info("Stuart server {} is starting...", Launcher.class.getPackage().getImplementationVersion());

            // start verticle service
            bootstrap.start();

            // add shutdown hook
            Runtime.getRuntime().addShutdownHook(new ShutdownHook(bootstrap));
        } else {
            Logger.log().error("Stuart's server cluster mode option is invalid.");
        }
    }

}
