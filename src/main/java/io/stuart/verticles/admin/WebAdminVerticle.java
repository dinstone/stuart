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

package io.stuart.verticles.admin;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.lang.IgniteCallable;

import io.stuart.Launcher;
import io.stuart.closures.QueryConnectionsClosure;
import io.stuart.closures.QueryListenersClosure;
import io.stuart.config.Config;
import io.stuart.consts.AclConst;
import io.stuart.consts.HttpConst;
import io.stuart.consts.SysConst;
import io.stuart.context.ApplicationContext;
import io.stuart.entities.auth.MqttAcl;
import io.stuart.entities.auth.MqttAdmin;
import io.stuart.entities.auth.MqttUser;
import io.stuart.entities.cache.MqttListener;
import io.stuart.entities.cache.MqttNode;
import io.stuart.entities.cache.MqttRouter;
import io.stuart.entities.internal.MqttConnections;
import io.stuart.entities.internal.MqttSystemInfo;
import io.stuart.entities.metrics.MqttMetrics;
import io.stuart.entities.metrics.NodeMetrics;
import io.stuart.entities.param.QueryConnections;
import io.stuart.enums.Status;
import io.stuart.enums.Target;
import io.stuart.ext.auth.local.LocalAuth;
import io.stuart.log.Logger;
import io.stuart.services.cache.CacheService;
import io.stuart.services.metrics.MetricsService;
import io.stuart.utils.IdUtil;
import io.stuart.utils.SysUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.AuthenticationProvider;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.AuthenticationHandler;
import io.vertx.ext.web.handler.BasicAuthHandler;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.ResponseTimeHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;

public class WebAdminVerticle extends AbstractVerticle {

    private static final String sessionMapName = "stuart.http.sessions";

    private static final String sessionAccount = "__stuart.sessionAccount";

    private CacheService cacheService;

    private AuthenticationProvider authProvider;

    private IgniteCallable<MqttSystemInfo> systemInfoCallable;

    private IgniteCallable<NodeMetrics> nodeMetricsCallable;

    private IgniteCallable<MqttMetrics> mqttMetricsCallable;

    private QueryConnectionsClosure queryConnectionsClosure;

    private QueryListenersClosure queryListenersClosure;

    public WebAdminVerticle(ApplicationContext context) {
        this.cacheService = context.getCacheService();
        this.systemInfoCallable = SysUtil::getSystemInfo;
        this.nodeMetricsCallable = NodeMetrics::getInstance;
        this.mqttMetricsCallable = MqttMetrics::getInstance;
        this.queryConnectionsClosure = new QueryConnectionsClosure(cacheService);
        this.queryListenersClosure = new QueryListenersClosure(cacheService);
    }

    public void start() throws Exception {
        Logger.log().debug("Stuart's web verticle start...");

        // authentication provider
        authProvider = LocalAuth.create(cacheService);

        // router
        Router router = Router.router(vertx);
        router.errorHandler(401, rc -> {
            rc.response().setStatusCode(401).sendFile("webroot/401.html");
        });
        router.errorHandler(404, rc -> {
            rc.response().setStatusCode(404).sendFile("webroot/404.html");
        });

        router.route().handler(LoggerHandler.create()).handler(ResponseTimeHandler.create());
        // router.route().handler(BodyHandler.create());

        // set session handler
        SessionHandler sessionHandler = SessionHandler.create(LocalSessionStore.create(vertx, sessionMapName, 60000))
            .setSessionTimeout(Config.getVertxHttpSessionTimeoutMs()).setNagHttps(false);
        // router.route().handler(sessionHandler);

        // redirect authentication handler
        AuthenticationHandler redirectAuthHandler = CheckAuthenHandler.create();
        // basic authentication handler
        AuthenticationHandler basicAuthHandler = BasicAuthHandler.create(authProvider);

        // set body handler
        // set system url use redirect authentication handler
        router.route("/ui/*").handler(sessionHandler).handler(redirectAuthHandler);
        // set manage url use redirect authentication handler
        router.route("/sys/*").handler(sessionHandler).handler(redirectAuthHandler);
        // set api url use basic authentication handler
        router.route("/api/*").handler(basicAuthHandler);

        // mkRoute(router, HttpMethod.POST, "/login", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
        // .handler(sessionHandler).handler(this::login);
        router.post("/login").handler(sessionHandler).handler(BodyHandler.create()).handler(this::login);
        router.route("/logout").handler(sessionHandler).handler(this::logout);

        mkRoute(router, HttpMethod.POST, "/sys/index/init", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::initIndex);

        mkRoute(router, HttpMethod.POST, "/sys/console/info", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::getSystemInfo);

        mkRoute(router, HttpMethod.POST, "/sys/console/nodes", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::getNodeMetrics);

        mkRoute(router, HttpMethod.POST, "/sys/console/mqtt", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::getMqttMetrics);

        mkRoute(router, HttpMethod.POST, "/sys/connect/get", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::getConnections);

        mkRoute(router, HttpMethod.POST, "/sys/session/get", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::getSessions);

        mkRoute(router, HttpMethod.POST, "/sys/topic/get", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::getTopics);

        mkRoute(router, HttpMethod.POST, "/sys/sub/get", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::getSubscribes);

        mkRoute(router, HttpMethod.POST, "/sys/user/add", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::addUser);

        mkRoute(router, HttpMethod.POST, "/sys/user/delete", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::deleteUser);

        mkRoute(router, HttpMethod.POST, "/sys/user/update", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::updateUser);

        mkRoute(router, HttpMethod.POST, "/sys/user/get", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::getUsers);

        mkRoute(router, HttpMethod.POST, "/sys/acl/add", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::addAcl);

        mkRoute(router, HttpMethod.POST, "/sys/acl/delete", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::deleteAcl);

        mkRoute(router, HttpMethod.POST, "/sys/acl/update", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::updateAcl);

        mkRoute(router, HttpMethod.POST, "/sys/acl/reorder", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::reorderAcls);

        mkRoute(router, HttpMethod.POST, "/sys/acl/get", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::getAcls);

        mkRoute(router, HttpMethod.POST, "/sys/listener/get", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::getListeners);

        mkRoute(router, HttpMethod.POST, "/sys/admin/add", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::addAdmin);

        mkRoute(router, HttpMethod.POST, "/sys/admin/delete", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::deleteAdmin);

        mkRoute(router, HttpMethod.POST, "/sys/admin/update", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::updateAdmin);

        mkRoute(router, HttpMethod.POST, "/sys/admin/get", HttpConst.APPLICATION_JSON, HttpConst.APPLICATION_JSON)
            .handler(this::getAdmins);

        // set static handler
        router.route().handler(StaticHandler.create().setCachingEnabled(true).setIndexPage("/login.html"));

        // initialize http options
        HttpServerOptions options = new HttpServerOptions();
        // set http options
        options.setHost(Config.getInstanceListenAddr());
        options.setPort(Config.getHttpPort());
        // http server
        HttpServer server = vertx.createHttpServer(options);
        server.requestHandler(router).listen(ar -> {
            if (ar.succeeded()) {
                Logger.log().debug("Stuart's web verticle start succeeded, the verticle listen at port {}.",
                    Config.getHttpPort());
            } else {
                Logger.log().error("Stuart's web verticle start failed, excpetion: {}.", ar.cause().getMessage());
            }
        });
    }

    public void stop() throws Exception {
        // do nothing...
    }

    public void login(RoutingContext rc) {
        // get session
        Session session = rc.session();

        // result json
        JsonObject json = new JsonObject();
        // set code
        json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);

        try {
            // get request body
            JsonObject body = rc.body().asJsonObject();

            String username = body.getString("username");
            String password = body.getString("password");

            JsonObject authInfo = new JsonObject();
            authInfo.put("username", username);
            authInfo.put("password", password);

            authProvider.authenticate(authInfo, ar -> {
                if (ar.succeeded()) {
                    // get result
                    User user = ar.result();
                    // set session user
                    rc.setUser(user);

                    if (session != null) {
                        // regenerate id
                        session.regenerateId();
                        // session account
                        session.data().put(sessionAccount, username);
                    }

                    json.put(HttpConst.RESULT, true);
                    json.put(HttpConst.LOCATION, "/ui/page/index.html");
                } else {
                    json.put(HttpConst.RESULT, false);
                }

                writeJsonResponse(rc, json);
            });
        } catch (DecodeException e) {
            json.put(HttpConst.RESULT, false);
            json.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, json);
        }
    }

    public void logout(RoutingContext rc) {
        // remove session account
        rc.session().data().remove(sessionAccount);
        // clear session user
        rc.clearUser();
        // go to the login page
        rc.response().putHeader(HttpConst.LOCATION, "/").setStatusCode(302).end();
    }

    public void initIndex(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);

            if (nodeId == null) {
                // get local node id
                nodeId = cacheService.localNodeId();
            }

            // result
            JsonObject result = new JsonObject();

            // node json object
            JsonObject node = null;
            // node json array
            JsonArray nodes = new JsonArray();
            // get mqtt nodes
            List<MqttNode> list = cacheService.getNodes(Status.Running);

            for (MqttNode item : list) {
                node = new JsonObject();

                node.put("id", item.getNodeId().toString());
                node.put("text", item.getInstanceId());

                nodes.add(node);
            }

            // set node id
            result.put("nodeId", nodeId.toString());
            // set is local auth mode
            result.put("localAuth", cacheService.isLocalAuth(nodeId));
            // set nodes
            result.put("nodes", nodes);

            // return json
            JsonObject json = new JsonObject();

            json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            json.put(HttpConst.RESULT, result);

            writeJsonResponse(rc, json);
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void getSystemInfo(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);

            // return json
            JsonObject json = new JsonObject();
            // set code
            json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);

            // system information
            MqttSystemInfo systemInfo = null;

            if (nodeId == null || cacheService.localNodeId().equals(nodeId)) {
                // initialize local system information
                systemInfo = new MqttSystemInfo();
                // set version
                systemInfo.setVersion(Launcher.class.getPackage().getImplementationVersion());
                // set uptime
                systemInfo.setUptime(SysUtil.getUptime());
                // set system time
                systemInfo.setSystime(SysUtil.getSystime());
            } else {
                // get node
                MqttNode node = cacheService.getNode(nodeId);

                if (node != null && Status.Running.value() == node.getStatus()) {
                    // get remote system information
                    systemInfo = remoteSystemInfo(nodeId);
                }
            }

            if (systemInfo != null) {
                // set node information
                json.put(HttpConst.RESULT, JsonObject.mapFrom(systemInfo));
            }

            writeJsonResponse(rc, json);
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void getNodeMetrics(RoutingContext rc) {
        // return json
        JsonObject json = new JsonObject();
        // set code
        json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);

        // get nodes
        List<MqttNode> nodes = cacheService.getNodes(null);

        // result json
        JsonObject result = new JsonObject();
        // node json array
        JsonArray array = new JsonArray();
        // get retain message count
        long retainCount = MetricsService.i().getRetainCount();
        // get retain message max
        long retainMax = MetricsService.i().getRetainMax();

        nodes.forEach(node -> {
            // initialize node json
            JsonObject nodeJson = JsonObject.mapFrom(node);
            // set retain message count
            nodeJson.put("retainCount", retainCount);
            // set retain message max
            nodeJson.put("retainMax", retainMax);

            // node metrics
            NodeMetrics nodeMetrics = null;

            if (cacheService.localNodeId().equals(node.getNodeId())) {
                nodeMetrics = NodeMetrics.getInstance();
            } else if (Status.Running.value() == node.getStatus()) {
                nodeMetrics = remoteNodeMetrics(node.getNodeId());
            }

            if (nodeMetrics != null) {
                nodeJson.mergeIn(JsonObject.mapFrom(nodeMetrics));
            }

            array.add(nodeJson);
        });

        result.put(HttpConst.TOTAL, nodes.size());
        result.put(HttpConst.ITEMS, array);

        // set result json
        json.put(HttpConst.RESULT, result);

        writeJsonResponse(rc, json);
    }

    public void getMqttMetrics(RoutingContext rc) {
        // get node id
        UUID nodeId = getRequestNodeId(rc);

        // return json
        JsonObject json = new JsonObject();
        // set code
        json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);

        // get mqtt metrics json
        JsonObject result = null;
        // mqtt metrics
        MqttMetrics remoteMqttMetrics = null;

        if (nodeId == null || cacheService.localNodeId().equals(nodeId)) {
            // get local mqtt metrics
            remoteMqttMetrics = MqttMetrics.getInstance();
        } else {
            // get node
            MqttNode node = cacheService.getNode(nodeId);

            if (node != null && Status.Running.value() == node.getStatus()) {
                // get remote mqtt metrics
                remoteMqttMetrics = remoteMqttMetrics(nodeId);
            }
        }

        if (remoteMqttMetrics != null) {
            // convert to json object
            result = JsonObject.mapFrom(remoteMqttMetrics);
            // put retain message count
            result.put("messageRetained", MetricsService.i().getRetainCount());
        }

        // set node information
        json.put(HttpConst.RESULT, result);

        writeJsonResponse(rc, json);
    }

    public void getConnections(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);
            // get request body
            JsonObject body = rc.body().asJsonObject();

            String clientId = body.getString("clientId");
            Integer pageNum = body.getInteger("pageNum");
            Integer pageSize = body.getInteger("pageSize");

            // return json
            JsonObject json = new JsonObject();
            // set code
            json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);

            // result json
            JsonObject result = new JsonObject();

            if (nodeId == null || cacheService.localNodeId().equals(nodeId)) {
                result.put(HttpConst.TOTAL, cacheService.countConnections(clientId));
                result.put(HttpConst.ITEMS, cacheService.getConnections(clientId, pageNum, pageSize));
            } else {
                MqttConnections connections = remoteConnections(nodeId, clientId, pageNum, pageSize);

                if (connections != null) {
                    result.put(HttpConst.TOTAL, connections.getTotal());
                    result.put(HttpConst.ITEMS, connections.getConnections());
                }
            }

            json.put(HttpConst.RESULT, result);

            writeJsonResponse(rc, json);
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void getSessions(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);
            // get request body
            JsonObject body = rc.body().asJsonObject();

            String clientId = body.getString("clientId");
            Integer pageNum = body.getInteger("pageNum");
            Integer pageSize = body.getInteger("pageSize");

            // return json
            JsonObject json = new JsonObject();
            // set code
            json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);

            // result json
            JsonObject result = new JsonObject();
            // get session count
            result.put(HttpConst.TOTAL, cacheService.countSessions(nodeId, clientId));
            // get session items
            result.put(HttpConst.ITEMS, cacheService.getSessions(nodeId, clientId, pageNum, pageSize));

            // set result
            json.put(HttpConst.RESULT, result);

            writeJsonResponse(rc, json);
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void getTopics(RoutingContext rc) {
        try {
            // get request body
            JsonObject body = rc.body().asJsonObject();

            String topic = body.getString("topic");
            Integer pageNum = body.getInteger("pageNum");
            Integer pageSize = body.getInteger("pageSize");

            // get mqtt routers
            List<MqttRouter> routers = cacheService.getTopics(null, topic, pageNum, pageSize);

            MqttNode node = null;
            JsonObject item = null;
            JsonArray items = new JsonArray();

            if (routers != null && !routers.isEmpty()) {
                for (MqttRouter router : routers) {
                    // get mqtt node
                    node = cacheService.getNode(router.getNodeId());

                    item = new JsonObject();

                    item.put("topic", router.getTopic());

                    if (node != null) {
                        item.put("node", node.getInstanceId());
                    } else {
                        item.put("node", SysConst.NOT_APPLICABLE);
                    }

                    items.add(item);
                }
            }

            JsonObject result = new JsonObject();
            // get total
            result.put(HttpConst.TOTAL, cacheService.countTopics(null, topic));
            // get items
            result.put(HttpConst.ITEMS, items);

            // return json
            JsonObject json = new JsonObject();

            json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            json.put(HttpConst.RESULT, result);

            writeJsonResponse(rc, json);
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void getSubscribes(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);
            // get request body
            JsonObject body = rc.body().asJsonObject();

            String clientId = body.getString("clientId");
            Integer pageNum = body.getInteger("pageNum");
            Integer pageSize = body.getInteger("pageSize");

            JsonObject result = new JsonObject();
            // get total
            result.put(HttpConst.TOTAL, cacheService.countSubscribes(nodeId, clientId));
            // get items
            result.put(HttpConst.ITEMS, cacheService.getSubscribes(nodeId, clientId, pageNum, pageSize));

            // return json
            JsonObject json = new JsonObject();

            json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            json.put(HttpConst.RESULT, result);

            writeJsonResponse(rc, json);
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void addUser(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);
            // get request body
            JsonObject body = rc.body().asJsonObject();

            if (cacheService.isLocalAuth(nodeId)) {
                // get session account
                Object account = rc.session().data().get(sessionAccount);
                // get system time
                long now = Calendar.getInstance().getTimeInMillis();

                MqttUser user = new MqttUser();
                user.setUsername(body.getString("username"));
                user.setPassword(body.getString("password"));
                user.setDesc(body.getString("desc"));
                user.setCreateAccount(account == null ? null : account.toString());
                user.setCreateTime(now);

                // return json
                JsonObject json = new JsonObject();

                json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
                json.put(HttpConst.RESULT, cacheService.addUser(user));

                writeJsonResponse(rc, json);
            } else {
                writeNotLocalAuthMode(rc);
            }
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void deleteUser(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);
            // get request body
            JsonObject body = rc.body().asJsonObject();

            if (cacheService.isLocalAuth(nodeId)) {
                // get username
                String username = body.getString("username");

                // return json
                JsonObject json = new JsonObject();

                json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
                json.put(HttpConst.RESULT, cacheService.deleteUser(username));

                writeJsonResponse(rc, json);
            } else {
                writeNotLocalAuthMode(rc);
            }
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void updateUser(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);
            // get request body
            JsonObject body = rc.body().asJsonObject();

            if (cacheService.isLocalAuth(nodeId)) {
                // get session account
                Object account = rc.session().data().get(sessionAccount);
                // get system time
                long now = Calendar.getInstance().getTimeInMillis();

                // get admin password
                String adminPasswd = body.getString("adminPasswd");

                MqttUser user = new MqttUser();
                user.setUsername(body.getString("username"));
                user.setDesc(body.getString("desc"));
                user.setPassword(body.getString("userPasswd"));
                user.setUpdateAccount(account == null ? null : account.toString());
                user.setUpdateTime(now);

                // return json
                JsonObject json = new JsonObject();

                json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
                json.put(HttpConst.RESULT,
                    cacheService.updateUser(user, account == null ? "" : account.toString(), adminPasswd));

                writeJsonResponse(rc, json);
            } else {
                writeNotLocalAuthMode(rc);
            }
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void getUsers(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);
            // get request body
            JsonObject body = rc.body().asJsonObject();

            if (cacheService.isLocalAuth(nodeId)) {
                String username = body.getString("username");
                Integer pageNum = body.getInteger("pageNum");
                Integer pageSize = body.getInteger("pageSize");

                JsonObject result = new JsonObject();
                // get total
                result.put(HttpConst.TOTAL, cacheService.countUsers(username));
                // get items
                result.put(HttpConst.ITEMS, cacheService.getUsers(username, pageNum, pageSize));

                // return json
                JsonObject json = new JsonObject();

                json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
                json.put(HttpConst.RESULT, result);

                writeJsonResponse(rc, json);
            } else {
                writeNotLocalAuthMode(rc);
            }
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void addAcl(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);
            // get request body
            JsonObject body = rc.body().asJsonObject();

            if (cacheService.isLocalAuth(nodeId)) {
                // get session account
                Object account = rc.session().data().get(sessionAccount);
                // get system time
                long now = Calendar.getInstance().getTimeInMillis();

                String target = body.getString("target");
                Integer type = Integer.parseInt(body.getString("type"));
                String topic = body.getString("topic");
                Integer access = Integer.parseInt(body.getString("access"));
                Integer authority = Integer.parseInt(body.getString("authority"));

                // target type is all
                if (Target.All.value() == type) {
                    // set target = $all
                    target = AclConst.ALL;
                }

                MqttAcl acl = new MqttAcl();
                acl.setTarget(target);
                acl.setType(type);
                acl.setTopic(topic);
                acl.setAccess(access);
                acl.setAuthority(authority);
                acl.setCreateAccount(account == null ? null : account.toString());
                acl.setCreateTime(now);

                // return json
                JsonObject json = new JsonObject();

                json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
                json.put(HttpConst.RESULT, cacheService.addAcl(acl));

                writeJsonResponse(rc, json);
            } else {
                writeNotLocalAuthMode(rc);
            }
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void deleteAcl(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);
            // get request body
            JsonObject body = rc.body().asJsonObject();

            if (cacheService.isLocalAuth(nodeId)) {
                Long seq = body.getLong("seq");

                // return json
                JsonObject json = new JsonObject();

                json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
                json.put(HttpConst.RESULT, cacheService.deleteAcl(seq));

                writeJsonResponse(rc, json);
            } else {
                writeNotLocalAuthMode(rc);
            }
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void updateAcl(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);
            // get request body
            JsonObject body = rc.body().asJsonObject();

            if (cacheService.isLocalAuth(nodeId)) {
                // get session account
                Object account = rc.session().data().get(sessionAccount);
                // get system time
                long now = Calendar.getInstance().getTimeInMillis();

                Long seq = body.getLong("seq");
                String target = body.getString("target");
                Integer type = Integer.parseInt(body.getString("type"));
                String topic = body.getString("topic");
                Integer access = Integer.parseInt(body.getString("access"));
                Integer authority = Integer.parseInt(body.getString("authority"));

                // target type is all
                if (Target.All.value() == type) {
                    // set target = $all
                    target = AclConst.ALL;
                }

                MqttAcl acl = new MqttAcl();
                acl.setSeq(seq);
                acl.setTarget(target);
                acl.setType(type);
                acl.setTopic(topic);
                acl.setAccess(access);
                acl.setAuthority(authority);
                acl.setUpdateAccount(account == null ? null : account.toString());
                acl.setUpdateTime(now);

                // return json
                JsonObject json = new JsonObject();

                json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
                json.put(HttpConst.RESULT, cacheService.updateAcl(acl));

                writeJsonResponse(rc, json);
            } else {
                writeNotLocalAuthMode(rc);
            }
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void reorderAcls(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);
            // get request body
            JsonObject body = rc.body().asJsonObject();

            if (cacheService.isLocalAuth(nodeId)) {
                // get session account object
                Object accountObject = rc.session().data().get(sessionAccount);
                // get session account
                String account = accountObject == null ? null : accountObject.toString();
                // get system time
                long now = Calendar.getInstance().getTimeInMillis();

                // get reorders
                JsonArray reorders = body.getJsonArray("reorders");

                // mqtt acl list
                List<MqttAcl> acls = new ArrayList<>();
                // reorders' size
                int size = reorders.size();

                // json array
                JsonArray array = null;
                // mqtt acl
                MqttAcl acl = null;

                for (int i = 0; i < size; ++i) {
                    // get json array
                    array = reorders.getJsonArray(i);
                    // initialize mqtt acl
                    acl = new MqttAcl();

                    acl.setSeq(array.getLong(0));
                    acl.setTarget(array.getString(1));
                    acl.setType(array.getInteger(2));
                    acl.setTopic(array.getString(3));
                    acl.setAccess(array.getInteger(4));
                    acl.setAuthority(array.getInteger(5));
                    acl.setUpdateAccount(account);
                    acl.setUpdateTime(now);

                    acls.add(acl);
                }

                // result json
                JsonObject json = new JsonObject();

                json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
                json.put(HttpConst.RESULT, cacheService.reorderAcls(acls));

                writeJsonResponse(rc, json);
            } else {
                writeNotLocalAuthMode(rc);
            }
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void getAcls(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);

            if (cacheService.isLocalAuth(nodeId)) {
                // get acls
                List<Object[]> acls = cacheService.getAcls();

                // result
                JsonObject result = new JsonObject();

                // set total
                result.put(HttpConst.TOTAL, acls.size());
                // get items
                result.put(HttpConst.ITEMS, acls);

                // return json
                JsonObject json = new JsonObject();

                json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
                json.put(HttpConst.RESULT, result);

                writeJsonResponse(rc, json);
            } else {
                writeNotLocalAuthMode(rc);
            }
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void getListeners(RoutingContext rc) {
        try {
            // get node id
            UUID nodeId = getRequestNodeId(rc);

            // return json
            JsonObject json = new JsonObject();
            // set code
            json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);

            // result json
            JsonObject result = new JsonObject();

            List<MqttListener> listeners = null;

            if (nodeId == null || cacheService.localNodeId().equals(nodeId)) {
                listeners = cacheService.getListeners();
            } else {
                listeners = remoteListeners(nodeId);
            }

            if (listeners != null) {
                result.put(HttpConst.TOTAL, listeners.size());
                result.put(HttpConst.ITEMS, listeners);
            }

            json.put(HttpConst.RESULT, result);

            writeJsonResponse(rc, json);
        } catch (DecodeException e) {
            // error json
            JsonObject error = new JsonObject();

            error.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);
            error.put(HttpConst.RESULT, false);
            error.put(HttpConst.CAUSE, e.getMessage());

            writeJsonResponse(rc, error);
        }
    }

    public void addAdmin(RoutingContext rc) {
        // return json
        JsonObject json = new JsonObject();
        // set code
        json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);

        try {
            // get request body
            JsonObject body = rc.body().asJsonObject();

            // get session account
            Object account = rc.session().data().get(sessionAccount);
            // get system time
            long now = Calendar.getInstance().getTimeInMillis();

            MqttAdmin admin = new MqttAdmin();
            admin.setAccount(body.getString("account"));
            admin.setPassword(body.getString("password"));
            admin.setDesc(body.getString("desc"));
            admin.setCreateAccount(account == null ? null : account.toString());
            admin.setCreateTime(now);

            json.put(HttpConst.RESULT, cacheService.addAdmin(admin));
        } catch (DecodeException e) {
            json.put(HttpConst.RESULT, false);
            json.put(HttpConst.CAUSE, e.getMessage());
        }

        writeJsonResponse(rc, json);
    }

    public void deleteAdmin(RoutingContext rc) {
        // return json
        JsonObject json = new JsonObject();
        // set code
        json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);

        try {
            // get request body
            JsonObject body = rc.body().asJsonObject();

            String account = body.getString("account");

            json.put(HttpConst.RESULT, cacheService.deleteAdmin(account));
        } catch (DecodeException e) {
            json.put(HttpConst.RESULT, false);
            json.put(HttpConst.CAUSE, e.getMessage());
        }

        writeJsonResponse(rc, json);
    }

    public void updateAdmin(RoutingContext rc) {
        // return json
        JsonObject json = new JsonObject();
        // set code
        json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);

        try {
            // get request body
            JsonObject body = rc.body().asJsonObject();

            String oldPasswd = body.getString("oldPasswd");
            String newPasswd = body.getString("newPasswd");

            // get session account
            Object account = rc.session().data().get(sessionAccount);
            // get system time
            long now = Calendar.getInstance().getTimeInMillis();

            MqttAdmin admin = new MqttAdmin();
            admin.setAccount(body.getString("account"));
            admin.setDesc(body.getString("desc"));
            admin.setUpdateAccount(account == null ? null : account.toString());
            admin.setUpdateTime(now);

            json.put(HttpConst.RESULT, cacheService.updateAdmin(admin, oldPasswd, newPasswd));
        } catch (DecodeException e) {
            json.put(HttpConst.RESULT, false);
            json.put(HttpConst.CAUSE, e.getMessage());
        }

        writeJsonResponse(rc, json);
    }

    public void getAdmins(RoutingContext rc) {
        // return json
        JsonObject json = new JsonObject();
        // set code
        json.put(HttpConst.CODE, HttpConst.PROCESSED_CODE);

        try {
            // get request body
            JsonObject body = rc.body().asJsonObject();

            String account = body.getString("account");
            Integer pageNum = body.getInteger("pageNum");
            Integer pageSize = body.getInteger("pageSize");

            JsonObject result = new JsonObject();
            // get total
            result.put(HttpConst.TOTAL, cacheService.countAdmins(account));
            // get items
            result.put(HttpConst.ITEMS, cacheService.getAdmins(account, pageNum, pageSize));

            json.put(HttpConst.RESULT, result);
        } catch (DecodeException e) {
            json.put(HttpConst.RESULT, false);
            json.put(HttpConst.CAUSE, e.getMessage());
        }

        writeJsonResponse(rc, json);
    }

    private Route mkRoute(Router router, HttpMethod method, String url, String consumes, String produces) {
        Route route = router.route(method, url);
        route.consumes(consumes);
        route.produces(produces);

        if (hasBody(method)) {
            route.handler(BodyHandler.create());
        }

        return route;
    }

    private boolean hasBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.DELETE
                || method == HttpMethod.PATCH || method == HttpMethod.TRACE;
    }

    private UUID getRequestNodeId(RoutingContext rc) {
        String nodeId = rc.request().headers().get(HttpConst.PARAM_NODE_ID);

        Logger.log().debug("node : {} - get request headers, and 'paramNodeId' in headers is {}.",
            cacheService.localNodeId(), nodeId);

        if (StringUtils.isBlank(nodeId)) {
            return null;
        } else {
            return IdUtil.uuid(nodeId);
        }
    }

    private MqttSystemInfo remoteSystemInfo(UUID remoteNodeId) {
        // get node system information
        return cacheService.computeCall(remoteNodeId, systemInfoCallable);
    }

    private NodeMetrics remoteNodeMetrics(UUID remoteNodeId) {
        // get node metrics
        return cacheService.computeCall(remoteNodeId, nodeMetricsCallable);
    }

    private MqttMetrics remoteMqttMetrics(UUID remoteNodeId) {
        // get mqtt metrics
        return cacheService.computeCall(remoteNodeId, mqttMetricsCallable);
    }

    private MqttConnections remoteConnections(UUID remoteNodeId, String clientId, Integer pageNum, Integer pageSize) {
        // initialize query connections parameters
        QueryConnections params = new QueryConnections();

        // set query connections parameters
        params.setClientId(clientId);
        params.setPageNum(pageNum);
        params.setPageSize(pageSize);

        // get query connections result
        return cacheService.computeApply(remoteNodeId, queryConnectionsClosure, params);
    }

    private List<MqttListener> remoteListeners(UUID remoteNodeId) {
        // get query listeners result
        return cacheService.computeApply(remoteNodeId, queryListenersClosure, remoteNodeId);
    }

    private void writeNotLocalAuthMode(RoutingContext rc) {
        JsonObject json = new JsonObject();
        json.put(HttpConst.CODE, HttpConst.UNPROCESSED_CODE);
        json.put(HttpConst.RESULT, false);
        json.put(HttpConst.CAUSE, HttpConst.NOT_LOCAL_AUTH_MODE);

        String result = json.toString();

        HttpServerResponse response = rc.response();
        response.putHeader(HttpConst.CONTENT_TYPE, HttpConst.APPLICATION_JSON);
        response.putHeader(HttpConst.CONTENT_LENGTH, String.valueOf(result.length()));
        response.end(result);
    }

    private void writeJsonResponse(RoutingContext rc, JsonObject json) {
        // get bytes
        byte[] bytes = json.encode().getBytes(StandardCharsets.UTF_8);
        // get result
        String result = new String(bytes, StandardCharsets.UTF_8);

        HttpServerResponse response = rc.response();
        response.putHeader(HttpConst.CONTENT_TYPE, HttpConst.APPLICATION_JSON);
        response.putHeader(HttpConst.CONTENT_LENGTH, String.valueOf(bytes.length));
        response.end(result);
    }

}
