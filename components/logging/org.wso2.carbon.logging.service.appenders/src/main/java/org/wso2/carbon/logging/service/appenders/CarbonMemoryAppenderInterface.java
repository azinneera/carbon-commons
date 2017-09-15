package org.wso2.carbon.logging.service.appenders;

import org.wso2.carbon.utils.logging.CircularBuffer;

public interface CarbonMemoryAppenderInterface {
    void activateOptions();

    CircularBuffer<TenantAwareLogEvent> getCircularQueue();

    void setCircularBuffer(CircularBuffer<TenantAwareLogEvent> circularBuffer);

    int getBufferSize();

    void setBufferSize(int bufferSize);
}
