
package io.stuart.context;

import io.stuart.config.Config;
import io.stuart.consts.ParamConst;
import io.stuart.services.auth.AuthService;
import io.stuart.services.auth.holder.AuthHolder;
import io.stuart.services.cache.CacheService;
import io.stuart.services.cache.impl.ClsCacheServiceImpl;
import io.stuart.services.cache.impl.StdCacheServiceImpl;
import io.stuart.services.metrics.MetricsService;
import io.stuart.services.session.SessionService;
import io.stuart.services.session.impl.ClsSessionServiceImpl;
import io.stuart.services.session.impl.StdSessionServiceImpl;
import io.vertx.core.Vertx;

public class ApplicationContext {

    private CacheService cacheService;

    private SessionService sessionService;

    private AuthService authService;

    private MetricsService metricsService;

    private String clusterMode;

    public ApplicationContext() {
        clusterMode = Config.getClusterMode();
        if (ParamConst.CLUSTER_MODE_STANDALONE.equalsIgnoreCase(clusterMode)) {
            // get standalone cache service
            cacheService = StdCacheServiceImpl.getInstance();
            // start standalone cache service
            cacheService.start();
        } else {
            // get clustered cache service
            cacheService = ClsCacheServiceImpl.getInstance();
            // start clustered cache service
            cacheService.start();
        }
        // create metrics service
        metricsService = MetricsService.i();
        // start metrics service
        metricsService.start();
    }

    public void start(Vertx vertx) {
        vertx.registerVerticleFactory(new StuartVerticleFactory(this));

        if (ParamConst.CLUSTER_MODE_STANDALONE.equalsIgnoreCase(clusterMode)) {
            standalone(vertx);
        } else {
            clustered(vertx);
        }
    }

    public void stop() {
        if (metricsService != null) {
            metricsService.stop();
        }

        if (authService != null) {
            authService.stop();
        }

        if (sessionService != null) {
            sessionService.stop();
        }

        if (cacheService != null) {
            cacheService.stop();
        }
    }

    private void clustered(Vertx vertx) {
        // get clustered session service
        sessionService = ClsSessionServiceImpl.getInstance(vertx, cacheService);
        // start clustered session service
        sessionService.start();

        // get authentication and authorization service
        authService = AuthHolder.getAuthService(vertx, cacheService);
        if (authService != null) {
            // start authentication and authorization service
            authService.start();
        }
    }

    private void standalone(Vertx vertx) {
        // get standalone session service
        sessionService = StdSessionServiceImpl.getInstance(vertx, cacheService);
        // start standalone session service
        sessionService.start();

        // get authentication and authorization service
        authService = AuthHolder.getAuthService(vertx, cacheService);
        if (authService != null) {
            // start authentication and authorization service
            authService.start();
        }
    }

    public CacheService getCacheService() {
        return cacheService;
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public MetricsService getMetricsService() {
        return metricsService;
    }

}
