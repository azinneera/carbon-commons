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

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.core.util.Booleans;

import java.io.Serializable;
import java.security.AccessController;
import java.security.PrivilegedAction;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.utils.logging.CircularBuffer;
import org.wso2.carbon.utils.logging.handler.TenantDomainSetter;

/**
 * This appender will be used to capture the logs and later send to clients, if
 * requested via the logging web service.
 * <p>
 * This maintains a circular buffer of a fixed size of 200
 * No changes has been done to the variable values and method names.
 */
@Plugin(name = "CarbonMemoryAppender", category = "Core", elementType = "appender", printObject = true)
public final class CarbonMemoryAppender extends AbstractAppender {
    private CircularBuffer<TenantAwareLogEvent> circularBuffer;
    private int bufferSize = -1;

    /**
     * Constructor.
     *
     * @param name appender name
     * @param filter null if not specified
     * @param layout pattern of log messages
     * @param ignoreExceptions default is true
     *
     * Called by {@link #createAppender(String, Filter, Layout, String)}
     */
    private CarbonMemoryAppender(final String name, final Filter filter, final Layout<? extends Serializable> layout,
                                 final boolean ignoreExceptions) {
        super(name, filter, layout, ignoreExceptions);
        setCircularBuffer(new CircularBuffer<TenantAwareLogEvent>(200));
        activateOptions();
    }

    /**
     * Creates a CarbonMemoryAppender instance with
     * attributes configured in log4j2.properties.
     *
     * @param name appender name
     * @param filter null if not specified
     * @param layout pattern of log messages
     * @param ignore default is true
     * @return carbonMemoryAppender
     */
    @PluginFactory
    public static CarbonMemoryAppender createAppender(@PluginAttribute("name") final String name,
                                                      @PluginElement("Filters") final Filter filter,
                                                      @PluginElement("Layout") Layout<? extends Serializable> layout,
                                                      @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true) final String ignore) {
        if (name == null) {
            LOGGER.error("No name provided for CarbonMemoryAppender");
            return null;
        }
        if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
        }

        final boolean ignoreExceptions = Booleans.parseBoolean(ignore, true);

        CarbonMemoryAppender carbonMemoryAppender = new CarbonMemoryAppender(name, filter, layout, ignoreExceptions);
        BundleContext bundleContext = FrameworkUtil.getBundle(CarbonMemoryAppender.class).getBundleContext();
        bundleContext.registerService(CarbonMemoryAppender.class, carbonMemoryAppender, null);

        return carbonMemoryAppender;
    }

    /**
     * This is the overridden method from the Appender interface. {@link org.apache.logging.log4j.core.Appender}
     * This allows to write log events to preferred destination.
     * <p>
     * Converts the default log events to tenant aware log events and writes to a CircularBuffer
     *
     * @param logEvent the LogEvent object
     */
    @Override
    public void append(LogEvent logEvent) {
        if (circularBuffer != null) {
            int tenantId = AccessController.doPrivileged(new PrivilegedAction<Integer>() {
                public Integer run() {
                    return PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
                }
            });

            String appName = CarbonContext.getThreadLocalCarbonContext().getApplicationName();
            if (appName == null) {
                appName = TenantDomainSetter.getServiceName();
            }
            if (appName == null) {
            }
            TenantAwareLogEvent tenantAwareLogEvent = new TenantAwareLogEvent(logEvent);
            tenantAwareLogEvent.setTenantId(String.valueOf(tenantId));
            tenantAwareLogEvent.setServiceName(appName);
            circularBuffer.append(tenantAwareLogEvent);
        }
    }

    /**
     * Taken from the previous CarbonMemoryAppender
     */
    public void activateOptions() {
        if (bufferSize < 0) {
            if (circularBuffer == null) {
                this.circularBuffer = new CircularBuffer<TenantAwareLogEvent>();
            }
        } else {
            this.circularBuffer = new CircularBuffer<TenantAwareLogEvent>(bufferSize);
        }
    }

    /**
     * Taken from the previous CarbonMemoryAppender
     */
    public CircularBuffer<TenantAwareLogEvent> getCircularQueue() {
        return circularBuffer;
    }

    /**
     * Taken from the previous CarbonMemoryAppender
     */
    public void setCircularBuffer(CircularBuffer<TenantAwareLogEvent> circularBuffer) {
        this.circularBuffer = circularBuffer;
    }

    /**
     * Taken from the previous CarbonMemoryAppender
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * Taken from the previous CarbonMemoryAppender
     */
    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }
}
