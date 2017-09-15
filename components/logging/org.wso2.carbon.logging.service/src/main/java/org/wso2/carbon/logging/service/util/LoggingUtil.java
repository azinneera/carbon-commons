/*
 * Copyright 2005-2007 WSO2, Inc. (http://wso2.com)
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

package org.wso2.carbon.logging.service.util;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.description.AxisService;
import org.apache.commons.collections.iterators.IteratorEnumeration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.SyslogAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.net.Facility;
import org.wso2.carbon.base.ServerConfiguration;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.core.util.SystemFilter;
import org.wso2.carbon.logging.service.LogViewerException;
import org.wso2.carbon.logging.service.appenders.CarbonMemoryAppender;
import org.wso2.carbon.logging.service.config.ServiceConfigManager;
import org.wso2.carbon.logging.service.config.SyslogConfigManager;
import org.wso2.carbon.logging.service.config.SyslogConfiguration;
import org.wso2.carbon.logging.service.data.SyslogData;
import org.wso2.carbon.logging.service.internal.DataHolder;
import org.wso2.carbon.logging.service.internal.LoggingServiceComponent;
import org.wso2.carbon.logging.service.registry.RegistryManager;
import org.wso2.carbon.registry.core.Collection;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.tenant.TenantManager;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.Pageable;
import org.wso2.carbon.utils.ServerConstants;
import org.wso2.carbon.utils.logging.CircularBuffer;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LoggingUtil {

    public static final String SYSTEM_LOG_PATTERN = "[%d] %5p - %x %m {%c}%n";
    private static final int MAX_LOG_MESSAGES = 200;
    private static final Log log = LogFactory.getLog(LoggingUtil.class);
    private static RegistryManager registryManager = new RegistryManager();
    private static CarbonMemoryAppender carbonMemoryAppender;

    public static boolean isStratosService() throws Exception {
        String serviceName = ServerConfiguration.getInstance().getFirstProperty("ServerKey");
        return ServiceConfigManager.isStratosService(serviceName);
    }

    public static void setSystemLoggingParameters(String logLevel, String logPattern)
            throws Exception {
        registryManager.updateConfigurationProperty(LoggingConstants.SYSTEM_LOG_LEVEL, logLevel);
        registryManager
                .updateConfigurationProperty(LoggingConstants.SYSTEM_LOG_PATTERN, logPattern);
    }

    public static SyslogData getSyslogData() throws Exception {
        return registryManager.getSyslogData();
    }

    private static String[] getAdminServiceNames() {
        ConfigurationContext configurationContext = DataHolder.getInstance()
                .getServerConfigContext();
        Map<String, AxisService> services = configurationContext.getAxisConfiguration()
                .getServices();
        List<String> adminServices = new ArrayList<String>();
        for (Map.Entry<String, AxisService> entry : services.entrySet()) {
            AxisService axisService = entry.getValue();
            if (SystemFilter.isAdminService(axisService)
                    || SystemFilter.isHiddenService(axisService)) {
                adminServices.add(axisService.getName());
            }
        }
        return adminServices.toArray(new String[adminServices.size()]);
    }

    public static int getTenantIdForDomain(String tenantDomain) throws LogViewerException {
        int tenantId;
        TenantManager tenantManager = LoggingServiceComponent.getTenantManager();
        if (tenantDomain == null || tenantDomain.equals("")) {
            tenantId = MultitenantConstants.SUPER_TENANT_ID;
        } else {

            try {
                tenantId = tenantManager.getTenantId(tenantDomain);
            } catch (UserStoreException e) {
                throw new LogViewerException("Cannot find tenant id for the given tenant domain.");
            }
        }
        return tenantId;
    }

    public static boolean isValidTenant(String domain) {
        int tenantId;
        if (domain == null || domain.equals("")) {
            tenantId = CarbonContext.getThreadLocalCarbonContext().getTenantId();
        } else {
            try {
                tenantId = LoggingUtil.getTenantIdForDomain(domain);
            } catch (LogViewerException e) {
                return false;
            }
        }

        if (tenantId == org.wso2.carbon.base.MultitenantConstants.INVALID_TENANT_ID) {
            return false;
        }
        return true;
    }

    public static boolean isFileAppenderConfiguredForST() {
        LoggerContext logContext = (LoggerContext) LogManager.getContext(false);
        RollingFileAppender logger = logContext.getConfiguration().getAppender("CARBON_LOGFILE");
        if (logger != null
                && CarbonContext.getThreadLocalCarbonContext().getTenantId() == org.wso2.carbon.base.MultitenantConstants.SUPER_TENANT_ID) {
            return true;
        } else {
            return false;
        }
    }


    public static boolean isAdmingService(String serviceName) {
        String[] adminServices = getAdminServiceNames();
        for (String adminService : adminServices) {
            if (adminService.equals(serviceName)) {
                return true;
            }
        }
        return false;
    }

    //TODO: find a way to get root logger level
    public static String getSystemLogLevel() throws Exception {
        String systemLogLevel = registryManager
                .getConfigurationProperty(LoggingConstants.SYSTEM_LOG_LEVEL);
        if (systemLogLevel == null) {
            Logger logger = (Logger) LogManager.getLogger(LogManager.getRootLogger());
            return logger.getLevel().toString();
        }
        return systemLogLevel;
    }

    public static String getSystemLogPattern() throws Exception {
        String systemLogPattern = registryManager
                .getConfigurationProperty(LoggingConstants.SYSTEM_LOG_PATTERN);
        if (systemLogPattern == null) {
            return LoggingUtil.SYSTEM_LOG_PATTERN;
        }
        return systemLogPattern;
    }

    public static boolean isValidTenantDomain(String tenantDomain) {
        try {
            getTenantIdForDomain(tenantDomain);
            return true;
        } catch (LogViewerException e) {
            return false;
        }
    }

    public static boolean isManager() {
        if (LoggingConstants.WSO2_STRATOS_MANAGER.equalsIgnoreCase(ServerConfiguration.getInstance()
                .getFirstProperty("ServerKey"))) {
            return true;
        } else {
            return false;
        }
    }

    public static void loadCustomConfiguration() throws Exception {
        // set the appender details

        // we have not provided a facility to add or remove appenders so all the
        // initial appender set should present in the system.
        // and all the initall logger should present in the system
        Set<Appender> appenderSet = new HashSet<Appender>();
        Logger rootLogger = (Logger) LogManager.getRootLogger();

        // set the root logger level, if the system log level is changed.
        String persistedSystemLoggerLevel = registryManager
                .getConfigurationProperty(LoggingConstants.SYSTEM_LOG_LEVEL);
        boolean systemLogLevelChanged = (persistedSystemLoggerLevel != null);
        if (systemLogLevelChanged) {
            rootLogger.setLevel(Level.toLevel(persistedSystemLoggerLevel));
        }

        String persistedSystemLogPattern = registryManager
                .getConfigurationProperty(LoggingConstants.SYSTEM_LOG_PATTERN);
        boolean systemLogPatternChanged = (persistedSystemLogPattern != null);
        setSystemLoggingParameters(persistedSystemLoggerLevel,
                (systemLogPatternChanged) ? persistedSystemLogPattern : SYSTEM_LOG_PATTERN);

        addAppendersToSet(new IteratorEnumeration(rootLogger.getAppenders().keySet().iterator()), appenderSet);

        // System log level has been changed, need to update all the loggers and
        // appenders
        if (systemLogLevelChanged) {
            Logger logger;
            LoggerConfig loggerConfig = new LoggerConfig(rootLogger.getName(), null, false);
            Enumeration loggersEnum = new IteratorEnumeration(loggerConfig.getAppenders().keySet().iterator());

            Level systemLevel = Level.toLevel(persistedSystemLoggerLevel);
            while (loggersEnum.hasMoreElements()) {
                logger = (Logger) loggersEnum.nextElement();
                // we ignore all class level defined loggers
                addAppendersToSet(new IteratorEnumeration(logger.getAppenders().keySet().iterator()), appenderSet);
                logger.setLevel(systemLevel);
            }

            for (Appender appender : appenderSet) {
                if (appender instanceof AbstractAppender) {
                    AbstractAppender abstractAppender = (AbstractAppender) appender;
                    loggerConfig = new LoggerConfig(abstractAppender.getName(), null, false);
                    loggerConfig.setLevel(systemLevel);
                }
            }
        }

        // Update the logger data according to the data stored in the registry.
        Collection loggerCollection = registryManager.getLoggers();
        if (loggerCollection != null) {
            String[] loggerResourcePaths = loggerCollection.getChildren();
            for (String loggerResourcePath : loggerResourcePaths) {
                String loggerName = loggerResourcePath.substring(LoggingConstants.LOGGERS.length());
                Logger logger = (Logger) LogManager.getLogger(loggerName);
                Resource loggerResource = registryManager.getLogger(loggerName);
                if (loggerResource != null && logger != null) {
                    logger.setLevel(Level.toLevel(loggerResource
                            .getProperty(LoggingConstants.LoggerProperties.LOG_LEVEL)));
                    logger.setAdditive(Boolean.parseBoolean(loggerResource
                            .getProperty(LoggingConstants.LoggerProperties.ADDITIVITY)));
                }
            }
        }

        // update the appender data according to data stored in database
        Collection appenderCollection = registryManager.getAppenders();
        if (appenderCollection != null) {
            String[] appenderResourcePaths = appenderCollection.getChildren();
            for (String appenderResourcePath : appenderResourcePaths) {
                String appenderName = appenderResourcePath.substring(LoggingConstants.APPENDERS
                        .length());
                Appender appender = getAppenderFromSet(appenderSet, appenderName);
                Resource appenderResource = registryManager.getAppender(appenderName);
                if (appenderResource != null && appender != null) {
                    if ((appender.getLayout() != null)
                            && (appender.getLayout() instanceof PatternLayout)) {
                        ((PatternLayout) appender.getLayout()).newSerializerBuilder().
                                setPattern(appenderResource.getProperty(LoggingConstants.AppenderProperties.PATTERN));
                    }
                    if (appender instanceof FileAppender) {
                        FileAppender fileAppender = ((FileAppender) appender);

                        // resolves the log file path, if not absolute path, calculate with CARBON_HOME as the base
                        Path logFilePath = Paths.get(appenderResource.getProperty(
                                LoggingConstants.AppenderProperties.LOG_FILE_NAME));
                        if (!logFilePath.isAbsolute()) {
                            logFilePath = Paths.get(System.getProperty(ServerConstants.CARBON_HOME)).resolve(logFilePath);
                        }
                        fileAppender.newBuilder().withFileName(logFilePath.normalize().toString()).build();
                    }

                    if (appender instanceof CarbonMemoryAppender) {
                        CarbonMemoryAppender memoryAppender = (CarbonMemoryAppender) appender;
                        memoryAppender.setCircularBuffer(new CircularBuffer(200));
                        memoryAppender.activateOptions();
                    }

                    if (appender instanceof SyslogAppender) {
                        SyslogAppender syslogAppender = (SyslogAppender) appender;
                        syslogAppender.newSyslogAppenderBuilder().withHost(appenderResource
                                .getProperty(LoggingConstants.AppenderProperties.SYS_LOG_HOST));
                        syslogAppender.newSyslogAppenderBuilder().setFacility(Facility.toFacility(appenderResource
                                .getProperty(LoggingConstants.AppenderProperties.FACILITY)));
                    }

                    if (appender instanceof AbstractAppender) {
                        AbstractAppender abstractAppender = (AbstractAppender) appender;
                        LoggerConfig loggerConfig = new LoggerConfig(abstractAppender.getName(), null, false);
                        loggerConfig = new LoggerConfig(abstractAppender.getName(), null, false);
                        loggerConfig.setLevel(Level.toLevel(appenderResource
                                .getProperty(LoggingConstants.AppenderProperties.THRESHOLD)));
                    }
                }
            }
        }
    }


    private static void addAppendersToSet(Enumeration appenders, Set<Appender> appenderSet) {
        while (appenders.hasMoreElements()) {
            Appender appender = (Appender) appenders.nextElement();
            appenderSet.add(appender);
        }
    }

    public static Appender getAppenderFromSet(Set<Appender> appenderSet, String name) {
        for (Appender appender : appenderSet) {
            if ((appender.getName() != null) && (appender.getName().equals(name))) {
                return appender;
            }
        }
        return null;
    }

    public static void updateConfigurationProperty(String key, String value)
            throws RegistryException {
        registryManager.updateConfigurationProperty(key, value);
    }

    public static String getConfigurationProperty(String key) throws RegistryException {
        return registryManager.getConfigurationProperty(key);
    }

    public static void removeAllLoggersAndAppenders() throws Exception {
        registryManager.removeAllRegistryEntries();
    }

    public static boolean isSysLogAppender(String tenantDomain) throws Exception {
        SyslogConfiguration syslogConfig = SyslogConfigManager.loadSyslogConfiguration();
        return syslogConfig.isSyslogOn();
    }

    public static boolean isSyslogConfigured() throws Exception {
        if (registryManager.getSyslogConfig() == null) {
            return false;
        } else {
            return true;
        }
    }

    public static int getLineNumbers(String logFile) throws Exception {
        InputStream logStream;

        try {
            logStream = getLocalInputStream(logFile);
        } catch (IOException e) {
            throw new LogViewerException("Cannot find the specified file location to the log file",
                    e);
        } catch (Exception e) {
            throw new LogViewerException("Cannot find the specified file location to the log file",
                    e);
        }
        try {
            byte[] c = new byte[1024];
            int count = 0;
            int readChars = 0;
            while ((readChars = logStream.read(c)) != -1) {
                for (int i = 0; i < readChars; ++i) {
                    if (c[i] == '\n') {
                        ++count;
                    }
                }
            }
            return count;
        } catch (IOException e) {
            throw new LogViewerException("Cannot read file size from the " + logFile, e);
        } finally {
            try {
                logStream.close();
            } catch (IOException e) {
                throw new LogViewerException("Cannot close the input stream " + logFile, e);
            }
        }
    }

    public static String[] getLogLinesFromFile(String logFile, int maxLogs, int start, int end)
            throws LogViewerException {
        ArrayList<String> logsList = new ArrayList<String>();
        InputStream logStream;
        if (end > maxLogs) {
            end = maxLogs;
        }
        try {
            logStream = getLocalInputStream(logFile);
        } catch (Exception e) {
            throw new LogViewerException("Cannot find the specified file location to the log file",
                    e);
        }
        BufferedReader dataInput = new BufferedReader(new InputStreamReader(logStream));
        int index = 1;
        String line;
        try {
            while ((line = dataInput.readLine()) != null) {
                if (index <= end && index > start) {
                    logsList.add(line);
                }
                index++;
            }
            dataInput.close();
        } catch (IOException e) {
            log.error("Cannot read the log file", e);
            throw new LogViewerException("Cannot read the log file", e);
        }
        return logsList.toArray(new String[logsList.size()]);
    }

    private static InputStream getLocalInputStream(String logFile) throws FileNotFoundException {
        String fileName = CarbonUtils.getCarbonLogsPath() + LoggingConstants.URL_SEPARATOR
                + logFile;
        InputStream is = new BufferedInputStream(new FileInputStream(fileName));
        return is;
    }

    /**
     * This method stream log messages and retrieve 100 log messages per page
     *
     * @param pageNumber The page required. Page number starts with 0.
     * @param sourceList The original list of items
     * @param pageable   The type of Pageable item
     * @return Returned page
     */
    public static <C> List<C> doPaging(int pageNumber, List<C> sourceList, int maxLines,
                                       Pageable pageable) {
        if (pageNumber < 0 || pageNumber == Integer.MAX_VALUE) {
            pageNumber = 0;
        }
        if (sourceList.size() == 0) {
            return sourceList;
        }
        if (pageNumber < 0) {
            throw new RuntimeException("Page number should be a positive integer. "
                    + "Page numbers begin at 0.");
        }
        int itemsPerPageInt = MAX_LOG_MESSAGES; // the default number of item
        // per page
        int numberOfPages = (int) Math.ceil((double) maxLines / itemsPerPageInt);
        if (pageNumber > numberOfPages - 1) {
            pageNumber = numberOfPages - 1;
        }
        List<C> returnList = new ArrayList<C>();
        for (int i = 0; i < sourceList.size(); i++) {
            returnList.add(sourceList.get(i));
        }
        int pages = calculatePageLevel(pageNumber + 1);
        if (pages > numberOfPages) {
            pages = numberOfPages;
        }
        pageable.setNumberOfPages(pages);
        pageable.set(returnList);
        return returnList;
    }

    /*
     * This is an equation to retrieve the visible number of pages ie p1-p5 -> 5
     * p6-p10 -> 10 p11-p15 -> 15
     */
    private static int calculatePageLevel(int x) {
        int p = x / 5;
        int q = x % 5;
        int t = (p + 1) * 5;
        int s = (p * 5) + 1;
        int y = q > 0 ? t : s;
        return y;
    }

}

