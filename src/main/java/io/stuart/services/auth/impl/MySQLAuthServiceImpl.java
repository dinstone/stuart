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

package io.stuart.services.auth.impl;

import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.stuart.config.Config;
import io.stuart.consts.AclConst;
import io.stuart.entities.internal.MqttAuthority;
import io.stuart.enums.Target;
import io.stuart.log.Logger;
import io.stuart.services.auth.AuthService;
import io.stuart.utils.AuthUtil;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

public class MySQLAuthServiceImpl implements AuthService {

    private static final String queryAcl;

    private Vertx vertx;

    private SqlClient mysql;

    static {
        queryAcl = queryAcl();
    }

    private static String queryAcl() {
        StringBuilder sql = new StringBuilder();

        sql.append(" select acl.topic, acl.authority from stuart_acl acl where ");
        sql.append(" (acl.target = ? and acl.type = ?) or (acl.target = ? and acl.type = ?) or ");
        sql.append(" (acl.target = ? and acl.type = ?) or (acl.target = ? and acl.type = ?) ");
        sql.append(" order by acl.seq asc ");

        return sql.toString();
    }

    public MySQLAuthServiceImpl(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public void start() {
        Logger.log().info("Stuart's mysql authentication service is starting...");

        JsonObject config = new JsonObject();

        config.put("host", Config.getAuthRdbHost());
        config.put("port", Config.getAuthRdbPort());
        config.put("username", Config.getAuthRdbUsername());
        config.put("password", Config.getAuthRdbPassword());
        config.put("database", Config.getAuthRdbDatabase());
        config.put("charset", Config.getAuthRdbCharset());
        config.put("maxPoolSize", Config.getAuthRdbMaxPoolSize());
        config.put("queryTimeout", Config.getAuthRdbQueryTimeoutMs());

        MySQLConnectOptions connectOptions = new MySQLConnectOptions().setPort(Config.getAuthRdbPort())
            .setHost(Config.getAuthRdbHost()).setDatabase(Config.getAuthRdbDatabase())
            .setCharset(Config.getAuthRdbCharset()).setUser(Config.getAuthRdbUsername())
            .setPassword(Config.getAuthRdbPassword()).setConnectTimeout(1000);

        // Pool options
        PoolOptions poolOptions = new PoolOptions().setMaxSize(Config.getAuthRdbMaxPoolSize());

        mysql = MySQLPool.client(vertx, connectOptions, poolOptions);

        Logger.log().info("Stuart's mysql authentication service start succeeded.");
    }

    @Override
    public void stop() {
        if (mysql == null) {
            return;
        }

        mysql.close(ar -> {
            if (ar.succeeded()) {
                Logger.log().info("Stuart's mysql authentication service close succeeded.");
            } else {
                Logger.log().error("Stuart's mysql authentication service close failed, exception: {}.",
                    ar.cause().getMessage());
            }
        });
    }

    @Override
    public void auth(String username, String password, Function<Boolean, Void> handler) {
        if (StringUtils.isBlank(username) || StringUtils.isBlank(password)) {
            handler.apply(false);
        } else {
            String sql = "select password from stuart_user where username = ?";
            mysql.preparedQuery(sql).execute(Tuple.of(username)).onSuccess(rs -> {
                if (passwdEquals(password, rs)) {
                    handler.apply(true);
                } else {
                    handler.apply(false);
                }
            }).onFailure(e -> handler.apply(false));
        }
    }

    @Override
    public void access(String username, String ipAddr, String clientId, final List<MqttAuthority> auths,
            Function<List<MqttAuthority>, Void> handler) {
        Tuple t = Tuple
            .of(username, Target.Username.value(), ipAddr, Target.IpAddr.value(), clientId, Target.ClientId.value())
            .addValue(AclConst.ALL).addValue(Target.All.value());

        mysql.preparedQuery(queryAcl).execute(t).onComplete(ar -> {
            if (ar.succeeded()) {
                setAuthority(auths, ar.result());
            }

            handler.apply(auths);
        });
    }

    @Override
    public void access(String username, String ipAddr, String clientId, final MqttAuthority auth,
            Function<MqttAuthority, Void> handler) {
        Tuple t = Tuple
            .of(username, Target.Username.value(), ipAddr, Target.IpAddr.value(), clientId, Target.ClientId.value())
            .addValue(AclConst.ALL).addValue(Target.All.value());

        mysql.preparedQuery(queryAcl).execute(t).onComplete(ar -> {
            if (ar.succeeded()) {
                setAuthority(auth, ar.result());
            }

            handler.apply(auth);
        });
    }

    private boolean passwdEquals(String password, RowSet<Row> rs) {
        if (rs == null || rs.rowCount() == 0) {
            return false;
        }

        String enPasswd = Config.getAes().encryptBase64(password);
        for (Row row : rs) {
            String qyPasswd = row.getString(0);
            if (qyPasswd != null && enPasswd.equals(qyPasswd)) {
                return true;
            } else {
                return false;
            }
        }

        return false;
    }

    private void setAuthority(final List<MqttAuthority> auths, RowSet<Row> rowSet) {
        if (rowSet == null || rowSet.rowCount() == 0) {
            return;
        }

        for (MqttAuthority auth : auths) {
            // qos is 0x80
            if (MqttQoS.FAILURE.value() == auth.getQos()) {
                // next one
                continue;
            }

            // set access and authority
            setAuthority(auth, rowSet);
        }
    }

    private void setAuthority(final MqttAuthority auth, RowSet<Row> rs) {
        // get topic
        String topic = auth.getTopic();
        // transformed authority from MySQL
        MqttAuthority transformed = null;

        for (Row acl : rs) {
            // is match
            if (AuthUtil.isMatch(topic, acl.getString(0))) {
                // get transformed authority
                transformed = AuthUtil.transform2Authority(acl.getString(1));

                // set access
                auth.setAccess(transformed.getAccess());
                // set authority
                auth.setAuthority(transformed.getAuthority());

                // break
                break;
            }
        }
    }

}
