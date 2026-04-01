# iCameraCctvMonitor — Cloud Data Center API Specification

This document defines the complete API contract for periodically pushing monitoring data from an on-premise iCameraCctvMonitor instance to the Cloud Data Center.

---

## Table of Contents

1. [Overview & Push Strategy](#1-overview--push-strategy)
2. [Authentication Scheme](#2-authentication-scheme)
3. [Consolidated JSON Payload Schema](#3-consolidated-json-payload-schema)
4. [API Endpoint Specification](#4-api-endpoint-specification)
5. [Sample Request](#5-sample-request)
6. [Sample Response](#6-sample-response)
7. [Error Codes & Responses](#7-error-codes--responses)
8. [Data Push Intervals](#8-data-push-intervals)
9. [Implementation Notes](#9-implementation-notes)

---

## 1. Overview & Push Strategy

The iCameraCctvMonitor agent pushes a **consolidated snapshot** containing all monitoring categories in a single HTTP POST at a configurable interval (recommended: **every 60 seconds**). This avoids multiple scattered API calls and reduces connection overhead.

```mermaid
sequenceDiagram
    participant Monitor as iCameraCctvMonitor<br/>(on-premise)
    participant Cloud as Cloud Data Center<br/>(g01.tcsion.com)

    loop Every 60 seconds
        Monitor->>Monitor: Collect snapshot from DataStore
        Monitor->>Cloud: POST /iCAMERAStreamingFW/api/v1/monitor/heartbeat
        Cloud-->>Monitor: 200 OK + ack payload
    end

    alt On critical alert (immediate push)
        Monitor->>Cloud: POST /iCAMERAStreamingFW/api/v1/monitor/alert
        Cloud-->>Monitor: 200 OK
    end
```

**Two endpoints are defined:**

| Endpoint | Purpose | Frequency |
|---|---|---|
| `POST .../heartbeat` | Periodic full-snapshot push | Every 60s (configurable) |
| `POST .../alert` | Immediate critical-alert escalation | On-demand (severity = CRITICAL) |

---

## 2. Authentication Scheme

| Aspect | Value |
|---|---|
| **Scheme** | Bearer Token (fixed static) |
| **Header** | `Authorization: Bearer iCam-Monitor-Static-Auth-Token-2026` |
| **Token** | Fixed static token shared between all monitor agents and the Cloud Data Centre |
| **Configuration** | Stored in `application.properties` as `cloud.auth.token` |

> [!NOTE]
> A fixed static token is used for simplicity. The server must validate that the `Authorization` header matches the expected token value. The same token is used by all monitor agent instances.

> [!TIP]
> This can be upgraded to a per-proxy dynamic token scheme in the future without changing the payload format — only the token acquisition mechanism would change.

---

## 3. Consolidated JSON Payload Schema

### 3.1 Top-Level Envelope

```json
{
  "schemaVersion": "1.0",
  "proxyId": 12345,
  "orgId": 999,
  "tcCode": "1001",
  "proxyName": "DummyProxy-01",
  "agentVersion": "2.1.0",
  "timestamp": "2026-04-01T01:44:00.000Z",
  "proxy": { ... },
  "systemMetrics": { ... },
  "cameras": [ ... ],
  "networkHistory": [ ... ],
  "alerts": [ ... ],
  "connectivity": [ ... ],
  "vmsDetected": true,
  "vmsDetection": [ ... ],
  "macValidation": { ... }
}
```

### 3.2 Field-by-Field Schema

#### Root Envelope

| Field | Type | Required | Description |
|---|---|---|---|
| `schemaVersion` | string | ✅ | Payload schema version (e.g. `"1.0"`) |
| `proxyId` | integer | ✅ | Registered proxy identifier |
| `orgId` | long | ✅ | Organisation identifier |
| `tcCode` | string | ✅ | Test Centre code |
| `proxyName` | string | ✅ | Human-readable proxy name |
| `agentVersion` | string | ✅ | Monitor utility version |
| `timestamp` | string (ISO-8601) | ✅ | Snapshot generation time (UTC) |
| `proxy` | object | ✅ | Proxy health data |
| `systemMetrics` | object | ✅ | Hardware/OS metrics |
| `cameras` | array of objects | ✅ | Per-camera CCTV data |
| `networkHistory` | array of objects | ✅ | Recent upload speed readings |
| `alerts` | array of objects | ✅ | Unresolved alert events |
| `connectivity` | array of objects | ⚠️ | URL/SSL health checks (optional) |
| `vmsDetected` | boolean | ✅ | Whether any VMS software was detected on the machine |
| `vmsDetection` | array of objects | ⚠️ | Detected VMS info — **only present when `vmsDetected` is true** |
| `macValidation` | object or null | ⚠️ | Last MAC validation result (optional) |

---

#### `proxy` Object

```json
{
  "proxyId": 12345,
  "orgId": 999,
  "proxyName": "DummyProxy-01",
  "tcCode": "1001",
  "status": "UP",
  "downReason": null,
  "startTimeUtc": "2026-03-30T10:00:00.000Z",
  "uptimeSeconds": 142800,
  "serviceStatus": "RUNNING",
  "processCpuPercent": 3.2,
  "processMemoryMb": 512.0,
  "currentMacAddress": "AA:BB:CC:DD:EE:FF",
  "macMismatch": false,
  "hsqldb": {
    "serviceStatus": "UP",
    "jmxStatus": "UP",
    "port": 9001,
    "directlyReachable": true,
    "startTimeUtc": "2026-03-30T10:00:05.000Z"
  },
  "snapshotTimeUtc": "2026-04-01T01:44:00.000Z",
  "stale": false
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `status` | string | ✅ | `"UP"` / `"DEGRADED"` / `"DOWN"` / `"UNKNOWN"` |
| `downReason` | string | ❌ | Null when UP |
| `startTimeUtc` | ISO-8601 | ✅ | Service start time |
| `uptimeSeconds` | long | ✅ | Uptime in seconds |
| `serviceStatus` | string | ✅ | Windows service state |
| `processCpuPercent` | double | ✅ | Proxy process CPU % |
| `processMemoryMb` | double | ✅ | Proxy process memory MB |
| `currentMacAddress` | string | ✅ | Active NIC MAC address |
| `macMismatch` | boolean | ✅ | Local vs cloud MAC differs |
| `hsqldb` | object | ✅ | Embedded database health |
| `hsqldb.serviceStatus` | string | ✅ | `"UP"` / `"DOWN"` / `"UNKNOWN"` |
| `hsqldb.jmxStatus` | string | ❌ | JMX-reported DB flag |
| `hsqldb.port` | int | ❌ | Parsed from server.properties |
| `hsqldb.directlyReachable` | boolean | ✅ | TCP connectivity to HSQLDB port |

> [!NOTE]
> Fields `installPath`, `servicePid`, `serviceExitCode` are intentionally excluded — they are local implementation details with no cloud value.

---

#### `systemMetrics` Object

```json
{
  "cpuPercent": 42.5,
  "memoryTotalMb": 16384.0,
  "memoryFreeMb": 5120.0,
  "memoryUsedPercent": 68.75,
  "networkUploadMbps": 8.4,
  "healthy": true,
  "drives": [
    {
      "name": "C:\\",
      "totalMb": 228949,
      "freeMb": 47862,
      "usedPercent": 79.1,
      "type": "SSD"
    }
  ],
  "hardwareProfile": {
    "cpuName": "Intel Core i7-10750H",
    "physicalCores": 6,
    "logicalCores": 12,
    "cpuMaxFreqGhz": 2.6,
    "totalRamBytes": 17179869184,
    "ramType": "DDR4"
  },
  "snapshotTimeUtc": "2026-04-01T01:44:00.000Z",
  "stale": false
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `cpuPercent` | double | ✅ | System CPU utilisation % |
| `memoryTotalMb` | double | ✅ | Total physical RAM (MB) |
| `memoryFreeMb` | double | ✅ | Available RAM (MB) |
| `memoryUsedPercent` | double | ✅ | Computed: `(total-free)/total × 100` |
| `networkUploadMbps` | double | ✅ | Current upload speed (MB/s) |
| `healthy` | boolean | ✅ | `false` if any metric ≥ 85% |
| `drives` | array | ✅ | Per-partition disk info (proxy drive only) |
| `hardwareProfile` | object | ✅ | Static hardware info (changes rarely) |

> [!TIP]
> The `hardwareProfile` sub-object contains static data. An optimized implementation could send it only on first heartbeat or when values change, by tracking a `hardwareProfileHash` field.

> [!NOTE]
> **Top-N process lists are excluded** — they are high-volume (updated every 15s), contain local user information (privacy concern), and have limited value for cloud-side dashboards.

---

#### `cameras` Array

```json
[
  {
    "cctvId": 101,
    "cctvName": "Camera-Front-Gate",
    "active": true,
    "inactiveReason": null,
    "reachable": true,
    "lastFileGeneratedUtc": "2026-04-01T01:43:30.000Z",
    "lastFileUploadedUtc": "2026-04-01T01:43:15.000Z",
    "streamAnalytics": {
      "encoding": "H264",
      "profile": "high",
      "fps": 25.0,
      "bitrateKbps": 4096,
      "resolution": "1920x1080",
      "probeSuccess": true
    },
    "snapshotTimeUtc": "2026-04-01T01:44:00.000Z",
    "stale": false
  }
]
```

| Field | Type | Required | Description |
|---|---|---|---|
| `cctvId` | int | ✅ | Camera registration ID |
| `cctvName` | string | ✅ | Display name |
| `active` | boolean | ✅ | Computed active status |
| `inactiveReason` | string | ❌ | Null when active |
| `reachable` | boolean | ✅ | RTSP reachability |
| `lastFileGeneratedUtc` | ISO-8601 | ✅ | Last file-generation time |
| `lastFileUploadedUtc` | ISO-8601 | ✅ | Last file-upload time |
| `streamAnalytics` | object | ✅ | ffprobe results |
| `streamAnalytics.encoding` | string | ✅ | Video codec (e.g. `"H264"`) |
| `streamAnalytics.profile` | string | ❌ | Stream profile |
| `streamAnalytics.fps` | double | ✅ | Frame rate |
| `streamAnalytics.bitrateKbps` | int | ✅ | Bitrate |
| `streamAnalytics.resolution` | string | ✅ | Width × Height |
| `streamAnalytics.probeSuccess` | boolean | ✅ | Probe succeeded |

> [!NOTE]
> Fields `rtspUrl` and `ipAddress` are intentionally excluded — RTSP URLs may contain credentials and IP addresses expose internal network topology.

---

#### `networkHistory` Array

```json
[
  { "timestampUtc": "2026-04-01T01:42:00.000Z", "uploadMbps": 8.2 },
  { "timestampUtc": "2026-04-01T01:42:30.000Z", "uploadMbps": 8.5 },
  { "timestampUtc": "2026-04-01T01:43:00.000Z", "uploadMbps": 7.9 }
]
```

| Field | Type | Required | Description |
|---|---|---|---|
| `timestampUtc` | ISO-8601 | ✅ | Measurement time |
| `uploadMbps` | double | ✅ | Upload speed (MB/s) |

---

#### `alerts` Array

```json
[
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "severity": "WARNING",
    "category": "SYSTEM",
    "source": "System",
    "parameter": "CPU_USAGE",
    "message": "System CPU 87.3% > 85%",
    "currentValue": 87.3,
    "threshold": 85.0,
    "timestampUtc": "2026-04-01T01:43:55.000Z",
    "acknowledged": false,
    "resolved": false
  }
]
```

| Field | Type | Required | Description |
|---|---|---|---|
| `id` | UUID string | ✅ | Unique alert identifier |
| `severity` | string | ✅ | `"INFO"` / `"WARNING"` / `"CRITICAL"` |
| `category` | string | ✅ | `"PROXY"` / `"CCTV"` / `"SYSTEM"` / `"NETWORK"` / `"HSQLDB"` / `"MAC"` |
| `source` | string | ✅ | Component that raised it |
| `parameter` | string | ✅ | Metric identifier |
| `message` | string | ✅ | Human-readable description |
| `currentValue` | double | ❌ | Triggering value (0 if N/A) |
| `threshold` | double | ❌ | Configured threshold (0 if N/A) |
| `timestampUtc` | ISO-8601 | ✅ | When alert was raised |
| `acknowledged` | boolean | ✅ | Operator-acknowledged flag |
| `resolved` | boolean | ✅ | Condition-cleared flag |

---

#### `connectivity` Array (Optional)

```json
[
  {
    "host": "g01.tcsion.com",
    "reachable": true,
    "httpStatus": 200,
    "ssl": {
      "valid": true,
      "issuer": "DigiCert Global G2",
      "expiryDate": "2027-01-15",
      "daysLeft": 289
    },
    "checkedAtUtc": "2026-04-01T01:43:50.000Z"
  }
]
```

---

#### `vmsDetection` Array (Optional)

```json
[
  {
    "vendor": "MILESTONE",
    "vendorDisplayName": "Milestone XProtect",
    "processName": "RecordingServer.exe",
    "status": "RUNNING",
    "running": true,
    "cpuPercent": 12.4,
    "memoryMb": 1024,
    "detectedAtUtc": "2026-04-01T01:43:00.000Z"
  }
]
```

---

#### `macValidation` Object (Optional, null if not run)

```json
{
  "localMac": "AA:BB:CC:DD:EE:FF",
  "cloudMac": "AA:BB:CC:DD:EE:FF",
  "scenario": "MAC_MATCH_NO_CONFLICT",
  "severity": "INFO",
  "message": "MAC address matches cloud registration. No conflict detected.",
  "conflictingProxies": [],
  "validatedAtUtc": "2026-04-01T01:40:00.000Z"
}
```

---

## 4. API Endpoint Specification

### 4.1 Periodic Heartbeat

| Attribute | Value |
|---|---|
| **Endpoint** | `POST https://{cloud.dc.host}/iCAMERAStreamingFW/api/v1/monitor/heartbeat` |
| **Production URL** | `https://g01.tcsion.com/iCAMERAStreamingFW/api/v1/monitor/heartbeat` |
| **HTTP Method** | `POST` |
| **Content-Type** | `application/json; charset=UTF-8` |
| **Accept** | `application/json` |

#### Required Headers

| Header | Value | Purpose |
|---|---|---|
| `Content-Type` | `application/json; charset=UTF-8` | Payload format |
| `Accept` | `application/json` | Expected response format |
| `Authorization` | `Bearer <api_token>` | Authentication |
| `X-Proxy-Id` | `12345` | Proxy identifier (redundant with body, for routing/load-balancing) |
| `X-Timestamp` | `2026-04-01T01:44:00.000Z` | Request time for replay protection |
| `X-Requested-With` | `XMLHttpRequest` | Consistent with existing TCS ION APIs |
| `X-Agent-Version` | `2.1.0` | Monitor utility version |
| `User-Agent` | `iCameraCctvMonitor/2.1.0` | Client identification |

### 4.2 Immediate Alert Push

| Attribute | Value |
|---|---|
| **Endpoint** | `POST https://{cloud.dc.host}/iCAMERAStreamingFW/api/v1/monitor/alert` |
| **HTTP Method** | `POST` |
| **Content-Type** | `application/json; charset=UTF-8` |

Same headers as heartbeat. Body is the same envelope but with:
- Only the `alerts` array is populated (containing the new critical alert)
- Other arrays may be empty `[]` or omitted

---

## 5. Sample Request

### Heartbeat — Full Example

```http
POST /iCAMERAStreamingFW/api/v1/monitor/heartbeat HTTP/1.1
Host: g01.tcsion.com
Content-Type: application/json; charset=UTF-8
Accept: application/json
Authorization: Bearer iCam-Monitor-Static-Auth-Token-2026
X-Proxy-Id: 12345
X-Timestamp: 2026-04-01T01:44:00.000Z
X-Requested-With: XMLHttpRequest
X-Agent-Version: 2.1.0
User-Agent: iCameraCctvMonitor/2.1.0
```

```json
{
  "schemaVersion": "1.0",
  "proxyId": 12345,
  "orgId": 999,
  "tcCode": "1001",
  "proxyName": "DummyProxy-01",
  "agentVersion": "2.1.0",
  "timestamp": "2026-04-01T01:44:00.000Z",

  "proxy": {
    "proxyId": 12345,
    "orgId": 999,
    "proxyName": "DummyProxy-01",
    "tcCode": "1001",
    "status": "UP",
    "downReason": null,
    "startTimeUtc": "2026-03-30T10:00:00.000Z",
    "uptimeSeconds": 142800,
    "serviceStatus": "RUNNING",
    "processCpuPercent": 3.2,
    "processMemoryMb": 512.0,
    "currentMacAddress": "AA:BB:CC:DD:EE:FF",
    "macMismatch": false,
    "hsqldb": {
      "serviceStatus": "UP",
      "jmxStatus": "UP",
      "port": 9001,
      "directlyReachable": true,
      "startTimeUtc": "2026-03-30T10:00:05.000Z"
    },
    "snapshotTimeUtc": "2026-04-01T01:44:00.000Z",
    "stale": false
  },

  "systemMetrics": {
    "cpuPercent": 42.5,
    "memoryTotalMb": 16384.0,
    "memoryFreeMb": 5120.0,
    "memoryUsedPercent": 68.75,
    "networkUploadMbps": 8.4,
    "healthy": true,
    "drives": [
      {
        "name": "C:\\",
        "totalMb": 228949,
        "freeMb": 47862,
        "usedPercent": 79.1,
        "type": "SSD"
      },
      {
        "name": "D:\\",
        "totalMb": 512000,
        "freeMb": 210000,
        "usedPercent": 58.98,
        "type": "HDD"
      }
    ],
    "hardwareProfile": {
      "cpuName": "Intel Core i7-10750H",
      "physicalCores": 6,
      "logicalCores": 12,
      "cpuMaxFreqGhz": 2.6,
      "totalRamBytes": 17179869184,
      "ramType": "DDR4"
    },
    "snapshotTimeUtc": "2026-04-01T01:44:00.000Z",
    "stale": false
  },

  "cameras": [
    {
      "cctvId": 101,
      "cctvName": "Camera-Front-Gate",
      "active": true,
      "inactiveReason": null,
      "reachable": true,
      "lastFileGeneratedUtc": "2026-04-01T01:43:30.000Z",
      "lastFileUploadedUtc": "2026-04-01T01:43:15.000Z",
      "streamAnalytics": {
        "encoding": "H264",
        "profile": "high",
        "fps": 25.0,
        "bitrateKbps": 4096,
        "resolution": "1920x1080",
        "probeSuccess": true
      },
      "snapshotTimeUtc": "2026-04-01T01:44:00.000Z",
      "stale": false
    },
    {
      "cctvId": 102,
      "cctvName": "Camera-Parking-A",
      "active": true,
      "inactiveReason": null,
      "reachable": true,
      "lastFileGeneratedUtc": "2026-04-01T01:43:00.000Z",
      "lastFileUploadedUtc": "2026-04-01T01:42:45.000Z",
      "streamAnalytics": {
        "encoding": "H264",
        "profile": "main",
        "fps": 15.0,
        "bitrateKbps": 2048,
        "resolution": "1280x720",
        "probeSuccess": true
      },
      "snapshotTimeUtc": "2026-04-01T01:44:00.000Z",
      "stale": false
    },
    {
      "cctvId": 103,
      "cctvName": "Camera-Lobby",
      "active": false,
      "inactiveReason": "RTSP Unreachable",
      "reachable": false,
      "lastFileGeneratedUtc": "2026-04-01T01:34:00.000Z",
      "lastFileUploadedUtc": "2026-04-01T01:33:50.000Z",
      "streamAnalytics": {
        "encoding": null,
        "profile": null,
        "fps": 0.0,
        "bitrateKbps": 0,
        "resolution": null,
        "probeSuccess": false
      },
      "snapshotTimeUtc": "2026-04-01T01:44:00.000Z",
      "stale": false
    }
  ],

  "networkHistory": [
    { "timestampUtc": "2026-04-01T01:40:00.000Z", "uploadMbps": 8.2 },
    { "timestampUtc": "2026-04-01T01:40:30.000Z", "uploadMbps": 8.5 },
    { "timestampUtc": "2026-04-01T01:41:00.000Z", "uploadMbps": 7.9 },
    { "timestampUtc": "2026-04-01T01:41:30.000Z", "uploadMbps": 8.1 },
    { "timestampUtc": "2026-04-01T01:42:00.000Z", "uploadMbps": 8.4 },
    { "timestampUtc": "2026-04-01T01:42:30.000Z", "uploadMbps": 8.3 },
    { "timestampUtc": "2026-04-01T01:43:00.000Z", "uploadMbps": 8.6 },
    { "timestampUtc": "2026-04-01T01:43:30.000Z", "uploadMbps": 8.4 }
  ],

  "alerts": [
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "severity": "WARNING",
      "category": "CCTV",
      "source": "CCTV-103-Camera-Lobby",
      "parameter": "CCTV_STATUS",
      "message": "CCTV Camera-Lobby is INACTIVE: RTSP Unreachable",
      "currentValue": 0,
      "threshold": 0,
      "timestampUtc": "2026-04-01T01:35:10.000Z",
      "acknowledged": false,
      "resolved": false
    }
  ],

  "connectivity": [
    {
      "host": "g01.tcsion.com",
      "reachable": true,
      "httpStatus": 200,
      "ssl": {
        "valid": true,
        "issuer": "DigiCert Global G2",
        "expiryDate": "2027-01-15",
        "daysLeft": 289
      },
      "checkedAtUtc": "2026-04-01T01:43:50.000Z"
    },
    {
      "host": "cctv4.tcsion.com",
      "reachable": true,
      "httpStatus": 200,
      "ssl": {
        "valid": true,
        "issuer": "DigiCert Global G2",
        "expiryDate": "2027-01-15",
        "daysLeft": 289
      },
      "checkedAtUtc": "2026-04-01T01:43:51.000Z"
    },
    {
      "host": "cctv8.tcsion.com",
      "reachable": false,
      "httpStatus": 0,
      "ssl": null,
      "checkedAtUtc": "2026-04-01T01:43:52.000Z"
    }
  ],

  "vmsDetected": false,

  "macValidation": null
}
```

---

## 6. Sample Response

### 6.1 Success (HTTP 200)

```json
{
  "status": "OK",
  "serverTimestampUtc": "2026-04-01T01:44:00.512Z",
  "heartbeatId": "hb-20260401-014400-12345",
  "nextPushIntervalSeconds": 60,
  "directives": {
    "runMacValidation": false,
    "forceVmsScan": false,
    "updatePollInterval": null
  }
}
```

| Field | Type | Description |
|---|---|---|
| `status` | string | `"OK"` on success |
| `serverTimestampUtc` | ISO-8601 | Server receive time |
| `heartbeatId` | string | Unique ID for this heartbeat (for tracing) |
| `nextPushIntervalSeconds` | int | Server-suggested push interval (allows dynamic throttling) |
| `directives` | object | Server-to-agent commands |
| `directives.runMacValidation` | boolean | Server requests an on-demand MAC validation |
| `directives.forceVmsScan` | boolean | Server requests an immediate VMS rescan |
| `directives.updatePollInterval` | int / null | Override push interval (null = keep current) |

> [!TIP]
> The `directives` object allows the cloud to remotely trigger on-demand operations without a separate command channel.

---

## 7. Error Codes & Responses

### HTTP Status Codes

| HTTP Code | Meaning | Client Action |
|---|---|---|
| `200 OK` | Successfully accepted | Continue normal push cycle |
| `202 Accepted` | Queued for processing | Continue normally |
| `400 Bad Request` | Malformed JSON / missing required fields | Log error, review payload, retry next cycle |
| `401 Unauthorized` | Invalid/expired auth token | Re-authenticate; alert operator |
| `403 Forbidden` | Proxy not authorized for this org | Alert operator; stop pushing |
| `404 Not Found` | Endpoint URL incorrect | Alert operator; check configuration |
| `409 Conflict` | Duplicate heartbeat (replay detected) | Ignore; continue next cycle |
| `413 Payload Too Large` | Body exceeds server limit (>1 MB) | Reduce payload (fewer network history points) |
| `422 Unprocessable Entity` | Schema validation failed | Log the `validationErrors` array; fix payload |
| `429 Too Many Requests` | Rate limit exceeded | Respect `Retry-After` header; back off |
| `500 Internal Server Error` | Server-side failure | Retry with exponential backoff |
| `502 Bad Gateway` | Upstream server unreachable | Retry after 30s |
| `503 Service Unavailable` | Server under maintenance | Respect `Retry-After` header |

### Error Response Body (HTTP 4xx / 5xx)

```json
{
  "status": "ERROR",
  "errorCode": "SCHEMA_VALIDATION_FAILED",
  "errorMessage": "Field 'proxyId' is required and must be a positive integer",
  "validationErrors": [
    {
      "field": "proxyId",
      "constraint": "required",
      "message": "proxyId must be a positive integer"
    },
    {
      "field": "cameras[2].streamAnalytics.fps",
      "constraint": "min",
      "message": "fps must be >= 0"
    }
  ],
  "serverTimestampUtc": "2026-04-01T01:44:00.512Z",
  "traceId": "tr-20260401-014400-err-001"
}
```

### Application Error Codes

| Error Code | HTTP Status | Description |
|---|---|---|
| `INVALID_SCHEMA_VERSION` | 400 | Unsupported `schemaVersion` value |
| `MISSING_REQUIRED_FIELD` | 400 | A required field is missing |
| `SCHEMA_VALIDATION_FAILED` | 422 | One or more field-level validations failed |
| `INVALID_AUTH_TOKEN` | 401 | Token is invalid or expired |
| `PROXY_NOT_REGISTERED` | 403 | `proxyId` is not registered in the cloud |
| `ORG_MISMATCH` | 403 | `orgId` doesn't match the authenticated proxy |
| `DUPLICATE_HEARTBEAT` | 409 | Identical `timestamp` already received |
| `PAYLOAD_TOO_LARGE` | 413 | Request body exceeds 1 MB |
| `RATE_LIMITED` | 429 | More than 2 requests per minute from this proxy |
| `INTERNAL_ERROR` | 500 | Unhandled server exception |

---

## 8. Data Push Intervals

| Scenario | Interval | Rationale |
|---|---|---|
| **Normal heartbeat** | 300 seconds (5 min) | Balances freshness vs. bandwidth for steady-state monitoring |
| **Critical alert** | Immediate (0 seconds) | CRITICAL alerts bypassed to `/alert` endpoint instantly |
| **Degraded network** | 120 seconds | Auto-backoff when upload speed < 1 MB/s |
| **Server-directed override** | `nextPushIntervalSeconds` from response | Cloud can dynamically throttle |
| **Connection failure** | Exponential backoff: 30s → 60s → 120s → 300s (max) | Avoids flooding during outage |
| **Startup** | 5-second delay, then immediate first heartbeat | Allows services to initialize |

---

## 9. Implementation Notes

### 9.1 Client-Side (iCameraCctvMonitor)

1. **New Service:** Create `CloudPushService.java` in `com.tcs.ion.iCamera.cctv.service` package
2. **Scheduler Integration:** Add to `SchedulerService.java` as a new scheduled task (60s interval)
3. **Payload Builder:** Assemble from `DataStore.getInstance()` — all data is already aggregated there
4. **HTTP Transport:** Use existing `HttpService.postJson()` method
5. **Config:** Add to `application.properties`:
   ```properties
   cloud.dc.host=g01.tcsion.com
   cloud.push.enabled=true
   cloud.push.interval.seconds=300
   cloud.auth.token=iCam-Monitor-Static-Auth-Token-2026
   ```
6. **Retry Logic:** Implement exponential backoff with jitter on failure
7. **Payload Size Guard:** If serialized JSON > 500 KB, truncate `networkHistory` to last 3 entries

### 9.2 Server-Side (Cloud Data Center)

1. **Controller:** `POST /iCAMERAStreamingFW/api/v1/monitor/heartbeat`
2. **Validation:** JSON Schema validation with descriptive error responses
3. **Storage:** Time-series DB (e.g. InfluxDB, TimescaleDB) for metrics; relational DB for alerts/status
4. **Idempotency:** Deduplicate by `(proxyId, timestamp)` pair
5. **Alert Routing:** Forward CRITICAL alerts to incident management / notification system
6. **Dashboard:** Aggregate across all proxies for fleet-wide monitoring

### 9.3 Payload Size Estimates

| Cameras | Est. Payload Size | Notes |
|---|---|---|
| 5 cameras | ~3 KB | Typical small site |
| 15 cameras | ~7 KB | Average deployment |
| 25 cameras | ~12 KB | Maximum recommended |
| 25 cameras + 10 alerts | ~15 KB | Worst case with active alerts |

All payloads are well under the 1 MB server limit. Compression (`Content-Encoding: gzip`) can reduce by ~70%.
