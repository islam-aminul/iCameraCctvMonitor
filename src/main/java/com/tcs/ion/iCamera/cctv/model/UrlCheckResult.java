package com.tcs.ion.iCamera.cctv.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Result of a single URL connectivity + SSL certificate check.
 */
public class UrlCheckResult {

    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter TIME_FMT  = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String    host;
    private final boolean   reachable;
    private final int       httpStatus;
    private final boolean   sslValid;
    private final String    sslIssuer;
    private final LocalDate sslExpiry;
    private final int       sslDaysLeft;
    private final String    errorMessage;
    private final Instant   checkedAt;

    public UrlCheckResult(String host, boolean reachable, int httpStatus,
                          boolean sslValid, String sslIssuer,
                          LocalDate sslExpiry, int sslDaysLeft,
                          String errorMessage, Instant checkedAt) {
        this.host         = host;
        this.reachable    = reachable;
        this.httpStatus   = httpStatus;
        this.sslValid     = sslValid;
        this.sslIssuer    = sslIssuer;
        this.sslExpiry    = sslExpiry;
        this.sslDaysLeft  = sslDaysLeft;
        this.errorMessage = errorMessage;
        this.checkedAt    = checkedAt;
    }

    public String    getHost()             { return host; }
    public boolean   isReachable()         { return reachable; }
    public int       getHttpStatus()       { return httpStatus; }
    public boolean   isSslValid()          { return sslValid; }
    public String    getSslIssuer()        { return sslIssuer != null ? sslIssuer : "-"; }
    public LocalDate getSslExpiry()        { return sslExpiry; }
    public int       getSslDaysLeft()      { return sslDaysLeft; }
    public String    getErrorMessage()     { return errorMessage != null ? errorMessage : ""; }
    public Instant   getCheckedAt()        { return checkedAt; }

    public String getSslExpiryDisplay() {
        return sslExpiry != null ? sslExpiry.format(DATE_FMT) : "-";
    }

    public String getCheckedAtDisplay() {
        return checkedAt.atZone(ZoneId.systemDefault()).format(TIME_FMT);
    }
}
