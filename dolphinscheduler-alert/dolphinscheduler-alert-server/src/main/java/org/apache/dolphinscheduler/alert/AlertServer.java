/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.alert;

import org.apache.dolphinscheduler.alert.metrics.AlertServerMetrics;
import org.apache.dolphinscheduler.alert.plugin.AlertPluginManager;
import org.apache.dolphinscheduler.alert.registry.AlertRegistryClient;
import org.apache.dolphinscheduler.alert.rpc.AlertRpcServer;
import org.apache.dolphinscheduler.alert.service.AlertBootstrapService;
import org.apache.dolphinscheduler.alert.service.ListenerEventPostService;
import org.apache.dolphinscheduler.common.constants.Constants;
import org.apache.dolphinscheduler.common.lifecycle.ServerLifeCycleManager;
import org.apache.dolphinscheduler.common.thread.DefaultUncaughtExceptionHandler;
import org.apache.dolphinscheduler.common.thread.ThreadUtils;
import org.apache.dolphinscheduler.plugin.registry.jdbc.JdbcRegistryAutoConfiguration;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@Slf4j
@SpringBootApplication
@ComponentScan(value = "org.apache.dolphinscheduler", excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = JdbcRegistryAutoConfiguration.class)
})
public class AlertServer {

    @Autowired
    private AlertBootstrapService alertBootstrapService;
    @Autowired
    private ListenerEventPostService listenerEventPostService;
    @Autowired
    private AlertRpcServer alertRpcServer;
    @Autowired
    private AlertPluginManager alertPluginManager;
    @Autowired
    private AlertRegistryClient alertRegistryClient;

    public static void main(String[] args) {
        AlertServerMetrics.registerUncachedException(DefaultUncaughtExceptionHandler::getUncaughtExceptionCount);
        Thread.setDefaultUncaughtExceptionHandler(DefaultUncaughtExceptionHandler.getInstance());
        Thread.currentThread().setName(Constants.THREAD_NAME_ALERT_SERVER);
        SpringApplication.run(AlertServer.class, args);
    }

    @PostConstruct
    public void run() {
        log.info("Alert server is staring ...");
        alertPluginManager.start();
        alertRegistryClient.start();
        alertBootstrapService.start();
        listenerEventPostService.start();
        alertRpcServer.start();
        log.info("Alert server is started ...");
    }

    @PreDestroy
    public void close() {
        destroy("alert server destroy");
    }

    /**
     * gracefully stop
     *
     * @param cause stop cause
     */
    public void destroy(String cause) {

        try {
            // set stop signal is true
            // execute only once
            if (!ServerLifeCycleManager.toStopped()) {
                log.warn("AlterServer is already stopped");
                return;
            }
            log.info("Alert server is stopping, cause: {}", cause);
            try (
                    AlertRpcServer closedAlertRpcServer = alertRpcServer;
                    AlertBootstrapService closedAlertBootstrapService = alertBootstrapService;
                    ListenerEventPostService closedListenerEventPostService = listenerEventPostService;
                    AlertRegistryClient closedAlertRegistryClient = alertRegistryClient) {
                // close resource
            }
            // thread sleep 3 seconds for thread quietly stop
            ThreadUtils.sleep(Constants.SERVER_CLOSE_WAIT_TIME.toMillis());
            log.info("Alter server stopped, cause: {}", cause);
        } catch (Exception e) {
            log.error("Alert server stop failed, cause: {}", cause, e);
        }
    }
}
