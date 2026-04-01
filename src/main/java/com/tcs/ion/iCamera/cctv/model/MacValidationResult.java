package com.tcs.ion.iCamera.cctv.model;

import java.util.Collections;
import java.util.List;

/**
 * Immutable result of a single MAC validation run against the remote cloud API.
 *
 * <p>Carries all the data required for consistent display across UI and alert log:
 * <ul>
 *   <li>Network context: destination host, outbound interface name, local MAC.</li>
 *   <li>Cloud context: cloud-registered MAC (from {@code existing proxy details.mac_address}),
 *       list of conflicting proxy records (from {@code mac-details} / {@code Mac-details}).</li>
 *   <li>Evaluated outcome: {@link Scenario}, alert severity, and human-readable message.</li>
 * </ul>
 *
 * <p>Use {@link Builder} to construct instances.
 */
public final class MacValidationResult {

    // ---- Scenario enum -----------------------------------------------------

    /** All possible outcomes of a MAC validation run. */
    public enum Scenario {
        /**
         * Local MAC equals the cloud-registered MAC for this proxy, and no
         * other proxy uses the same MAC.  No action required.
         */
        MAC_MATCH_NO_CONFLICT,

        /**
         * Local MAC differs from the cloud-registered MAC, but the local MAC is
         * not used by any other proxy.  A MAC update may be needed.
         */
        MAC_MISMATCH_NO_CONFLICT,

        /**
         * Local MAC differs from the cloud-registered MAC, and the local MAC is
         * already mapped to one or more other proxies – potential conflict.
         */
        MAC_MISMATCH_WITH_CONFLICT,

        /**
         * The cloud has no MAC mapping for this proxy, but the local MAC is
         * already registered to one or more other proxies.
         */
        NO_MAPPING_MAC_REGISTERED_ELSEWHERE,

        /**
         * The cloud has no MAC mapping for this proxy, and the local MAC is also
         * not registered anywhere remotely.
         */
        NO_MAPPING_MAC_FREE,

        /**
         * Interface detection, API invocation, or response parsing failed.
         * {@link MacValidationResult#getErrorDetail()} provides the root cause.
         */
        VALIDATION_FAILED
    }

    // ---- Fields ------------------------------------------------------------

    /** Destination host from config (e.g. {@code g01.tcsion.com}). */
    private final String destinationHost;

    /** Name of the local network interface used for outbound traffic (e.g. {@code eth0}). */
    private final String outboundInterface;

    /** MAC address read from the outbound interface (colon-separated upper-case hex). */
    private final String localMac;

    /**
     * MAC address currently registered in the cloud for this proxy, from
     * {@code data."existing proxy details".mac_address}.  Null / empty when no
     * mapping exists.
     */
    private final String cloudMac;

    /**
     * Proxy names / identifiers returned in {@code data.mac-details} or
     * {@code data.Mac-details} that already use the same local MAC address.
     * Empty list when there are no conflicts.
     */
    private final List<String> conflictingProxies;

    /** Evaluated outcome of this validation run. */
    private final Scenario scenario;

    /** Human-readable alert message appropriate for the scenario. */
    private final String alertMessage;

    /** Alert severity appropriate for the scenario. */
    private final AlertData.Severity alertSeverity;

    /**
     * Root cause detail when {@link Scenario#VALIDATION_FAILED}; null otherwise.
     */
    private final String errorDetail;

    // ---- Constructor -------------------------------------------------------

    private MacValidationResult(Builder b) {
        this.destinationHost    = b.destinationHost;
        this.outboundInterface  = b.outboundInterface;
        this.localMac           = b.localMac;
        this.cloudMac           = b.cloudMac;
        this.conflictingProxies = (b.conflictingProxies == null)
                ? Collections.emptyList()
                : Collections.unmodifiableList(b.conflictingProxies);
        this.scenario           = b.scenario;
        this.alertMessage       = b.alertMessage;
        this.alertSeverity      = b.alertSeverity;
        this.errorDetail        = b.errorDetail;
    }

    // ---- Getters -----------------------------------------------------------

    public String getDestinationHost()           { return destinationHost; }
    public String getOutboundInterface()         { return outboundInterface; }
    public String getLocalMac()                  { return localMac; }
    public String getCloudMac()                  { return cloudMac; }
    public List<String> getConflictingProxies()  { return conflictingProxies; }
    public Scenario getScenario()                { return scenario; }
    public String getAlertMessage()              { return alertMessage; }
    public AlertData.Severity getAlertSeverity() { return alertSeverity; }
    public String getErrorDetail()               { return errorDetail; }

    // ---- Builder -----------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String destinationHost;
        private String outboundInterface;
        private String localMac;
        private String cloudMac;
        private List<String> conflictingProxies;
        private Scenario scenario;
        private String alertMessage;
        private AlertData.Severity alertSeverity;
        private String errorDetail;

        private Builder() {}

        public Builder destinationHost(String v)          { destinationHost    = v; return this; }
        public Builder outboundInterface(String v)        { outboundInterface  = v; return this; }
        public Builder localMac(String v)                 { localMac           = v; return this; }
        public Builder cloudMac(String v)                 { cloudMac           = v; return this; }
        public Builder conflictingProxies(List<String> v) { conflictingProxies = v; return this; }
        public Builder scenario(Scenario v)               { scenario           = v; return this; }
        public Builder alertMessage(String v)             { alertMessage       = v; return this; }
        public Builder alertSeverity(AlertData.Severity v){ alertSeverity      = v; return this; }
        public Builder errorDetail(String v)              { errorDetail        = v; return this; }

        public MacValidationResult build() { return new MacValidationResult(this); }
    }
}
