/*
*  Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.logging.service.provider;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.wso2.carbon.base.ServerConfiguration;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.logging.service.LogViewerException;
import org.wso2.carbon.logging.service.appenders.CarbonMemoryAppender;
import org.wso2.carbon.logging.service.appenders.TenantAwareLogEvent;
import org.wso2.carbon.logging.service.data.LogEvent;
import org.wso2.carbon.logging.service.data.LoggingConfig;
import org.wso2.carbon.logging.service.provider.api.LogProvider;
import org.wso2.carbon.logging.service.util.LoggingUtil;
import org.wso2.carbon.utils.logging.CircularBuffer;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * @scr.component name="org.wso2.carbon.logging.service.provider.InMemoryLogProvider" immediate="true"
 * @scr.reference name="org.wso2.carbon.logging.service.appenders"
 * interface="org.wso2.carbon.logging.service.appenders.CarbonMemoryAppender"
 * cardinality="1..1" policy="dynamic" bind="setCarbonMemoryAppender"
 * unbind="unsetCarbonMemoryAppender"
 */
public class InMemoryLogProvider implements LogProvider {

    private static final Log log = LogFactory.getLog(InMemoryLogProvider.class);
    private static final int DEFAULT_NO_OF_LOGS = 100;
    private static final String SERVER_KEY = "ServerKey";
    private ServiceRegistration serviceRegistration;
    private CarbonMemoryAppender carbonMemoryAppender;

    protected void activate(ComponentContext componentContext) {
        try {
            serviceRegistration = componentContext.getBundleContext().registerService(LogProvider.class.getName(),
                    new InMemoryLogProvider(), null);
            log.info("InMemoryLogProvider is activated");
        } catch (Exception e) {
            log.error("Cannot start InMemoryLogProvider component", e);
        }
    }

    protected void setCarbonMemoryAppender(CarbonMemoryAppender memoryAppender) {
    }

    protected void unsetCarbonMemoryAppender(CarbonMemoryAppender memoryAppender) {
    }

    protected void deactivate(ComponentContext componentContext) {
        log.info("InMemoryLogProvider Component is deactivated");
        serviceRegistration.unregister();
    }

    /**
     * Initialize the log provider by reading the property comes with logging configuration file
     * This will be called immediate after create new instance of ILogProvider
     *
     * @param loggingConfig - configuration class which read and keep configurations
     */
    @Override
    public void init(LoggingConfig loggingConfig) {
        BundleContext bundleContext = FrameworkUtil.getBundle(InMemoryLogProvider.class).getBundleContext();
        try {
            serviceRegistration = bundleContext.registerService(LogProvider.class.getName(),
                    new InMemoryLogProvider(), null);
            ServiceReference serviceReference = bundleContext.getServiceReference(CarbonMemoryAppender.class);
            if(serviceReference != null) {
                carbonMemoryAppender = (CarbonMemoryAppender) bundleContext.getService(serviceReference);
            } else {
                log.error("Service Reference is null");
            }
        } catch (Exception e) {
            log.error(e);
        }
    }


    @Override
    public List<String> getApplicationNames(String tenantDomain, String serverKey) throws LogViewerException {
        List<String> appList = new ArrayList<String>();
        List<LogEvent> allLogs = getLogsByAppName("", tenantDomain, serverKey);
        for (LogEvent event : allLogs) {
            if (event.getAppName() != null && !"".equals(event.getAppName())
                    && !"NA".equals(event.getAppName())
                    && !LoggingUtil.isAdmingService(event.getAppName())
                    && !appList.contains(event.getAppName())
                    && !"STRATOS_ROOT".equals(event.getAppName())) {
                appList.add(event.getAppName());
            }
        }
        return getSortedApplicationNames(appList);
    }

    @Override
    public List<LogEvent> getSystemLogs() throws LogViewerException {
        List<LogEvent> resultList = new ArrayList<LogEvent>();
        CircularBuffer<TenantAwareLogEvent> circularBuffer = carbonMemoryAppender.getCircularQueue();
        if (circularBuffer != null) {
            List<TenantAwareLogEvent> tenantAwareLogEventList = getTenantAwareLoggingEventList(carbonMemoryAppender);
            for (TenantAwareLogEvent tenantAwareLogEvent : tenantAwareLogEventList) {
                if (tenantAwareLogEvent != null) {
                    resultList.add(createLogEvent(tenantAwareLogEvent));
                }
            }
            return reverseLogList(resultList);
        } else {
            return getDefaultLogEvents();
        }
    }

    private List<LogEvent> getDefaultLogEvents() {
        List<LogEvent> defaultLogEvents = new ArrayList<LogEvent>();
        defaultLogEvents.add(new LogEvent(
                "The log must be configured to use the "
                        + "org.wso2.carbon.logging.service.appender.CarbonMemoryAppender to view entries through the admin console",
                "NA"));
        return defaultLogEvents;
    }

    private List<TenantAwareLogEvent> getTenantAwareLoggingEventList(CarbonMemoryAppender memoryAppender) {
        int definedAmount;
        if (memoryAppender.getCircularQueue() != null) {
            definedAmount = memoryAppender.getBufferSize();
            if (definedAmount < 1) {
                return memoryAppender.getCircularQueue().get(DEFAULT_NO_OF_LOGS);
            } else {
                return memoryAppender.getCircularQueue().get(definedAmount);
            }
        } else {
            return new ArrayList<TenantAwareLogEvent>();
        }
    }

    @Override
    public List<LogEvent> getAllLogs(String tenantDomain, String serverKey) throws LogViewerException {
        return getLogs("ALL", tenantDomain, serverKey);
    }

    @Override
    public List<LogEvent> getLogsByAppName(String appName, String tenantDomain, String serverKey) throws LogViewerException {
        List<LogEvent> resultList = new ArrayList<LogEvent>();
        CircularBuffer<TenantAwareLogEvent> circularBuffer = carbonMemoryAppender.getCircularQueue();
        if (circularBuffer != null) {
            List<TenantAwareLogEvent> tenantAwareLogEventList = getTenantAwareLoggingEventList(carbonMemoryAppender);
            for (TenantAwareLogEvent tenantAwareLogEvent : tenantAwareLogEventList) {
                if (tenantAwareLogEvent != null) {
                    String productName = tenantAwareLogEvent.getServerName();
                    String tenantId = String.valueOf(tenantAwareLogEvent.getTenantId());
                    if (isCurrentTenantId(tenantId, tenantDomain) && isCurrentProduct(productName, serverKey)) {
                        if (appName == null || "".equals(appName)) {
                            resultList.add(createLogEvent(tenantAwareLogEvent));
                        } else {
                            String currAppName = tenantAwareLogEvent.getServiceName();
                            if (appName.equals(currAppName)) {
                                resultList.add(createLogEvent(tenantAwareLogEvent));
                            }
                        }
                    }
                }
            }
            return reverseLogList(resultList);
        } else {
            return getDefaultLogEvents();
        }
    }

    @Override
    public List<LogEvent> getLogs(String type, String keyword, String appName, String tenantDomain, String serverKey) throws LogViewerException {
        if (keyword == null || "".equals(keyword)) {
            // invalid keyword
            if (type == null || "".equals(type) || "ALL".equals(type)) {
                return this.getLogs(appName, tenantDomain, serverKey);
            } else {
                // type is NOT null and NOT equal to ALL Application Name is not needed
                return this.getLogsForType(type, appName, tenantDomain, serverKey);
            }
        } else {
            // valid keyword
            if (type == null || "".equals(type)) {
                // invalid type
                return this.getLogsForKey(keyword, appName, tenantDomain, serverKey);
            } else {
                // both type and keyword are valid, but type can be equal to ALL
                if ("ALL".equalsIgnoreCase(type)) {
                    return getLogsForKey(keyword, appName, tenantDomain, serverKey);
                } else {
                    List<LogEvent> filerByType = getLogsForType(type, appName, tenantDomain, serverKey);
                    List<LogEvent> resultList = new ArrayList<LogEvent>();
                    if (filerByType != null) {
                        for (LogEvent aFilerByType : filerByType) {
                            String logMessage = aFilerByType.getMessage();
                            String logger = aFilerByType.getLogger();
                            if (logMessage != null
                                    && logMessage.toLowerCase().contains(keyword.toLowerCase())) {
                                resultList.add(aFilerByType);
                            } else if (logger != null
                                    && logger.toLowerCase().contains(keyword.toLowerCase())) {
                                resultList.add(aFilerByType);
                            }
                        }
                    }
                    return reverseLogList(resultList);
                }
            }
        }

    }

    private List<LogEvent> getLogs(String appName, String tenantDomain, String serverKey) {
        List<LogEvent> resultList = new ArrayList<LogEvent>();
        CircularBuffer<TenantAwareLogEvent> circularBuffer = carbonMemoryAppender.getCircularQueue();
        if (circularBuffer != null) {
            List<TenantAwareLogEvent> tenantAwareLogEventList = getTenantAwareLoggingEventList(carbonMemoryAppender);
            for (TenantAwareLogEvent tenantAwareLogEvent : tenantAwareLogEventList) {
                if (tenantAwareLogEvent != null) {
                    String productName = tenantAwareLogEvent.getServerName();
                    String tenantId = String.valueOf(tenantAwareLogEvent.getTenantId());
                    if (isCurrentTenantId(tenantId, tenantDomain) && isCurrentProduct(productName, serverKey)) {
                        if (appName == null || "".equals(appName)) {
                            resultList.add(createLogEvent(tenantAwareLogEvent));
                        } else {
                            String currAppName = tenantAwareLogEvent.getServiceName();
                            if (appName.equals(currAppName)) {
                                if (appName.equals(currAppName)) {
                                    resultList.add(createLogEvent(tenantAwareLogEvent));
                                }
                            }
                        }
                    }
                }
            }
            return reverseLogList(resultList);
        } else {
            return getDefaultLogEvents();
        }
    }

    @Override
    public int logsCount(String tenantDomain, String serverKey) throws LogViewerException {
        return 0;
    }

    @Override
    public boolean clearLogs() {
        return false;
    }

    private boolean isCurrentTenantId(String tenantId, String domain) {
        String currTenantId;
        if ("".equals(domain)) {
            currTenantId = String.valueOf(PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId());
            return currTenantId.equals(tenantId);
        } else {
            try {
                currTenantId = String.valueOf(LoggingUtil.getTenantIdForDomain(domain));
                return currTenantId.equals(tenantId);
            } catch (LogViewerException e) {
                log.error("Error while getting current tenantId for domain " + domain, e);
                return false;
            }
        }
    }

    private boolean isCurrentProduct(String productName, String serverKey) {
        if ("".equals(serverKey)) {
            String currProductName = ServerConfiguration.getInstance().getFirstProperty(SERVER_KEY);
            return currProductName.equals(productName);
        } else {
            return productName.equals(serverKey);
        }

    }

    private LogEvent createLogEvent(TenantAwareLogEvent logEvt) {
        LogEvent event = new LogEvent();
        event.setTenantId(logEvt.getTenantId());
        event.setServerName(logEvt.getServerName());
        event.setAppName(logEvt.getServiceName());

        Date date = new Date(logEvt.getMutableLogEvent().getTimeMillis());
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
        String timeFormatted = formatter.format(date);
        event.setLogTime(timeFormatted);

        event.setLogger(logEvt.getMutableLogEvent().getLoggerName());
        event.setPriority(logEvt.getMutableLogEvent().getLevel().toString());
        event.setMessage(logEvt.getMutableLogEvent().getFormattedMessage());
        event.setInstance(logEvt.getInstance());
        if (logEvt.getMutableLogEvent().getThrowable() != null) {
            event.setStacktrace(getStacktrace(logEvt.getMutableLogEvent().getThrowable()));
        } else {
            event.setStacktrace("");
        }
        event.setIp(logEvt.getIp());

        return event;
    }

    private String getStacktrace(Throwable e) {
        StringBuilder stackTrace = new StringBuilder();
        StackTraceElement[] stackTraceElements = e.getStackTrace();
        for (StackTraceElement ele : stackTraceElements) {
            stackTrace.append(ele.toString()).append("\n");
        }
        return stackTrace.toString();
    }

    private List<LogEvent> reverseLogList(List<LogEvent> resultList) {
        Collections.reverse(resultList);
        return resultList;
    }

    private List<LogEvent> getLogsForKey(String keyword, String appName, String tenantDomain, String serverKey) {
        List<LogEvent> resultList = new ArrayList<LogEvent>();
        CircularBuffer<TenantAwareLogEvent> circularBuffer = carbonMemoryAppender.getCircularQueue();
        if (circularBuffer != null) {
            List<TenantAwareLogEvent> tenantAwareLogEventList = getTenantAwareLoggingEventList(carbonMemoryAppender);
            for (TenantAwareLogEvent tenantAwareLogEvent : tenantAwareLogEventList) {
                if (tenantAwareLogEvent != null) {
                    String productName = tenantAwareLogEvent.getServerName();
                    String tenantId = String.valueOf(tenantAwareLogEvent.getTenantId());
                    String result = carbonMemoryAppender.getLayout().toSerializable(tenantAwareLogEvent.getMutableLogEvent()).toString();
                    String logger = tenantAwareLogEvent.getMutableLogEvent().getLoggerName();
                    boolean isInLogMessage = result != null
                            && (result.toLowerCase().contains(keyword.toLowerCase()));
                    boolean isInLogger = logger != null
                            && (logger.toLowerCase().contains(keyword.toLowerCase()));
                    if (isCurrentTenantId(tenantId, tenantDomain) && isCurrentProduct(productName, serverKey)
                            && (isInLogMessage || isInLogger)) {
                        if (appName == null || "".equals(appName)) {
                            resultList.add(createLogEvent(tenantAwareLogEvent));
                        } else {
                            String currAppName = tenantAwareLogEvent.getServiceName();
                            if (appName.equals(currAppName)) {
                                if (appName.equals(currAppName)) {
                                    resultList.add(createLogEvent(tenantAwareLogEvent));
                                }
                            }
                        }
                    }
                }
            }
            return reverseLogList(resultList);
        } else {
            return getDefaultLogEvents();
        }
    }

    private List<LogEvent> getLogsForType(String type, String appName, String tenantDomain, String serverKey) {
        List<LogEvent> resultList = new ArrayList<LogEvent>();
        CircularBuffer<TenantAwareLogEvent> circularBuffer = carbonMemoryAppender.getCircularQueue();
        if (circularBuffer != null) {
            List<TenantAwareLogEvent> tenantAwareLogEventList = getTenantAwareLoggingEventList(carbonMemoryAppender);
            for (TenantAwareLogEvent tenantAwareLogEvent : tenantAwareLogEventList) {
                if (tenantAwareLogEvent != null) {
                    String priority = tenantAwareLogEvent.getMutableLogEvent().getLevel().toString();
                    String productName = tenantAwareLogEvent.getServerName();
                    String tenantId = String.valueOf(tenantAwareLogEvent.getTenantId());
                    if ((priority.equals(type) && isCurrentTenantId(tenantId, tenantDomain) && isCurrentProduct(productName, serverKey))) {
                        if (appName == null || "".equals(appName)) {
                            resultList.add(createLogEvent(tenantAwareLogEvent));
                        } else {
                            String currAppName = tenantAwareLogEvent.getServiceName();
                            if (appName.equals(currAppName)) {
                                if (appName.equals(currAppName)) {
                                    resultList.add(createLogEvent(tenantAwareLogEvent));
                                }
                            }
                        }
                    }
                }
            }
            return reverseLogList(resultList);
        } else {
            return getDefaultLogEvents();
        }
    }

    private List<String> getSortedApplicationNames(List<String> applicationNames) {
        Collections.sort(applicationNames, new Comparator<String>() {
            public int compare(String s1, String s2) {
                return s1.toLowerCase().compareTo(s2.toLowerCase());
            }

        });
        return applicationNames;
    }

}
