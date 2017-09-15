/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.logging.service.util;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Unit tests for logging utils.
 */
public class  LoggingUtilTest {

    private static final String APPENDER_NAME = "SIMPLE_APPENDER";

    @Test(groups = {"org.wso2.carbon.logging.service.util"},
            description = "Tests whether the Appender with the specified name is returned from " +
                    "LoggingUtil.getAppenderFromSet method given that the Appender set contains an appender " +
                    "without a name.")
    public void testGettingAppenderFromAppenderSet() {
        Appender appenderWithName = ConsoleAppender.createDefaultAppenderForLayout(PatternLayout.createDefaultLayout());

        Set<Appender> appenderSet = new LinkedHashSet<>();
        appenderSet.add(appenderWithName);

        Assert.assertEquals(appenderWithName, LoggingUtil.getAppenderFromSet(appenderSet, appenderWithName.getName()),
                "Appender with the name " + APPENDER_NAME + " is not returned from getAppenderFromSet method.");
    }

}
