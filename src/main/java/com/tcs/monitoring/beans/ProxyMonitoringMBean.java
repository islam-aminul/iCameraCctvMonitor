package com.tcs.monitoring.beans;

/**
 * JMX MBean interface for the iCamera Proxy monitoring bean.
 *
 * ObjectName: com.tcs.monitoring.beans.proxy_monitoring_bean:type=monitoring
 *
 * The proxy application registers an implementation of this interface
 * (com.tcs.monitoring.beans.ProxyMonitoring) on the platform MBean server.
 * This client-side interface declaration enables JMX.newMBeanProxy() to
 * produce a fully typed proxy, eliminating all reflection-based attribute
 * extraction.
 *
 * The bean types referenced here (CctvMetrics, SystemMetrics) are provided
 * at compile time by the Proxy-Common-Utils:2.9-SNAPSHOT dependency.
 */
public interface ProxyMonitoringMBean {

    long getProxyId();

    long getTcCode();

    long getOrgId();

    String getProxyName();

    CctvMetrics getCctvMetrics();

    /** Use the fully qualified name com.tcs.monitoring.beans.SystemMetrics at all
     *  call sites to avoid a name clash with com.tcs.ion.iCamera.cctv.model.SystemMetrics. */
    SystemMetrics getSystemMetrics();

    boolean getProxyStatusFlag();

    boolean getConnectionAttemptedFlag();

    boolean getDbStatusFlag();

    boolean hasCctvAdapter();

    boolean hasRecordingAdapter();
}
