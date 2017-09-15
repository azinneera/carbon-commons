/*
 * Copyright 2017 WSO2, Inc. (http://wso2.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.logging.service.appenders;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.apache.logging.log4j.util.StringMap;
import org.wso2.carbon.base.ServerConfiguration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * This class is used to store information regarding a LogEvent needed for CarbonMemoryAppender.
 */
public class TenantAwareLogEvent {
    private String tenantId;
    private String serviceName;
    private MutableLogEvent mutableLogEvent;
    private String serverName;
    private String ip;
    private String instance;

    /**
     * Constructor.
     *
     * @param logEvent the logEvent object
     */
    public TenantAwareLogEvent(LogEvent logEvent) {
        mutableLogEvent = new MutableLogEvent();
        mutableLogEvent.setLoggerName(logEvent.getLoggerName());
        mutableLogEvent.setMarker(logEvent.getMarker());
        mutableLogEvent.setLoggerFqcn(logEvent.getLoggerFqcn());
        mutableLogEvent.setLevel(logEvent.getLevel());
        mutableLogEvent.setMessage(logEvent.getMessage());
        mutableLogEvent.setContextData((StringMap) logEvent.getContextData());
        mutableLogEvent.setContextStack(logEvent.getContextStack());
        mutableLogEvent.setTimeMillis(logEvent.getTimeMillis());
        mutableLogEvent.setNanoTime(logEvent.getNanoTime());
        mutableLogEvent.setThreadId(logEvent.getThreadId());
        mutableLogEvent.setThreadName(logEvent.getThreadName());
        setMutableLogEvent(mutableLogEvent);
        String serverName = AccessController.doPrivileged(new PrivilegedAction<String>() {
            public String run() {
                return ServerConfiguration.getInstance().getFirstProperty("ServerKey");
            }
        });
        setServerName(serverName);

        String ip;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            ip = "127.0.0.1";
        }
        setIp(ip);
        setInstance(System.getProperty("carbon.instance.name"));
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public MutableLogEvent getMutableLogEvent() {
        return mutableLogEvent;
    }

    public void setMutableLogEvent(MutableLogEvent mutableLogEvent) {
        this.mutableLogEvent = mutableLogEvent;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }
}

