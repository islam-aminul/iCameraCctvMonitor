package com.tcs.monitoring.beans;

/**
 * JMX MBean interface for the iCamera Proxy monitoring bean.
 *
 * ObjectName: com.tcs.monitoring.beans.proxy_monitoring_bean:type=monitoring
 */
public interface ProxyMonitoringMBean {

    long getProxyId();

    long getTcCode();

    long getOrgId();

    String getProxyName();

    CctvMetrics getCctvMetrics();

    SystemMetrics getSystemMetrics();

    boolean getProxyStatusFlag();

    boolean getConnectionAttemptedFlag();

    boolean getDbStatusFlag();

    boolean hasCctvAdapter();

    boolean hasRecordingAdapter();
}
