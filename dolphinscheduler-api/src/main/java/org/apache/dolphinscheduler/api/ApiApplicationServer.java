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

package org.apache.dolphinscheduler.api;

import org.apache.dolphinscheduler.api.metrics.ApiServerMetrics;
import org.apache.dolphinscheduler.common.enums.PluginType;
import org.apache.dolphinscheduler.common.thread.DefaultUncaughtExceptionHandler;
import org.apache.dolphinscheduler.dao.PluginDao;
import org.apache.dolphinscheduler.dao.entity.PluginDefine;
import org.apache.dolphinscheduler.plugin.registry.jdbc.JdbcRegistryAutoConfiguration;
import org.apache.dolphinscheduler.plugin.task.api.TaskChannelFactory;
import org.apache.dolphinscheduler.plugin.task.api.TaskPluginManager;
import org.apache.dolphinscheduler.spi.params.PluginParamsTransfer;
import org.apache.dolphinscheduler.spi.params.base.PluginParams;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.event.EventListener;

@ServletComponentScan
@SpringBootApplication
@ComponentScan(value = "org.apache.dolphinscheduler", excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = JdbcRegistryAutoConfiguration.class)
})
@Slf4j
public class ApiApplicationServer {

    @Autowired
    private TaskPluginManager taskPluginManager;

    @Autowired
    private PluginDao pluginDao;

    public static void main(String[] args) {
        ApiServerMetrics.registerUncachedException(DefaultUncaughtExceptionHandler::getUncaughtExceptionCount);
        Thread.setDefaultUncaughtExceptionHandler(DefaultUncaughtExceptionHandler.getInstance());
        SpringApplication.run(ApiApplicationServer.class);
    }

    @EventListener
    public void run(ApplicationReadyEvent readyEvent) {
        log.info("Received spring application context ready event will load taskPlugin and write to DB");
        // install task plugin
        taskPluginManager.loadPlugin();
        for (Map.Entry<String, TaskChannelFactory> entry : taskPluginManager.getTaskChannelFactoryMap().entrySet()) {
            String taskPluginName = entry.getKey();
            TaskChannelFactory taskChannelFactory = entry.getValue();
            List<PluginParams> params = taskChannelFactory.getParams();
            String paramsJson = PluginParamsTransfer.transferParamsToJson(params);

            PluginDefine pluginDefine = new PluginDefine(taskPluginName, PluginType.TASK.getDesc(), paramsJson);
            pluginDao.addOrUpdatePluginDefine(pluginDefine);
        }
    }
}
