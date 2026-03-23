# iCamera CCTV Monitor – Build Instructions

**Organisation:** Tata Consultancy Services Ltd.
**Version:** 1.0.0
**Java:** 8+ (bundled JRE recommended)
**Target:** Windows x64

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 11+ | For building (sources target Java 8) |
| Maven | 3.8+ | Build tool |
| ffprobe.exe | 6.0 | Must be placed in app directory or path configured in `application.properties` |
| jre8-win64/ | Java 8 JRE | Place in project root for bundled distribution |

> **Note:** Launch4j requires a Windows host (or Wine on Linux) to generate the `.exe`.
> On Linux CI, you can build the uber-JAR only using `-pl -launch4j`.

---

## Quick Build

```bash
mvn clean package
```

This runs in sequence:
1. `maven-compiler-plugin` – compiles Java 8 sources
2. `maven-shade-plugin` – produces the uber-JAR at `target/dist/iCamera-cctv-1.0.0-all.jar`
3. `launch4j-maven-plugin` – wraps the uber-JAR into `target/dist/iCamera CCTV Monitor.exe`
4. `maven-assembly-plugin` – creates the Windows distribution ZIP at `target/`

---

## Output Files

| File | Location | Description |
|------|----------|-------------|
| `iCamera CCTV Monitor.exe` | `target/dist/` | Windows executable |
| `iCamera-cctv-1.0.0-all.jar` | `target/dist/` | Uber-JAR (for testing on any platform) |
| `iCamera CCTV Monitor-1.0.0-win64.zip` | `target/` | Distribution archive |

---

## Distribution Layout

```
iCamera CCTV Monitor/
├── iCamera CCTV Monitor.exe   ← launch this
├── jre/                       ← bundled JRE (from jre8-win64/ in project root)
└── config/
    ├── application.properties
    └── logback.xml
```

---

## Running from JAR (dev / cross-platform testing)

```bash
java -jar target/dist/iCamera-cctv-1.0.0-all.jar
```

> Add `--add-opens` flags if running on Java 11+:
> ```bash
> java --add-opens java.base/java.lang=ALL-UNNAMED \
>      --add-opens java.base/java.util=ALL-UNNAMED \
>      -jar target/dist/iCamera-cctv-1.0.0-all.jar
> ```

---

## Configuration

Edit `application.properties` (or `~/.icamera/settings.json` which overrides it):

```properties
jmx.host=localhost
jmx.port=1999
jmx.port.retries=5
poll.interval.seconds=30
ffprobe.path=.\ffprobe.exe
jetty.port=8080
```

JMX URL pattern used:
`service:jmx:rmi:///jndi/rmi://{host}:{port}/jmxrmi`

The application tries ports `1999, 2000, 2001, 2002, 2003, 2004` in sequence.

---

## Bundled JRE

Place a 64-bit Java 8 JRE in `jre8-win64/` at the project root before building.
The EXE will look for `./jre` relative to its location.

Example:
```
project-root/
├── pom.xml
├── jre8-win64/         ← unzip jre-8u391-windows-x64.tar.gz here
│   ├── bin/
│   ├── lib/
│   └── ...
└── src/
```

---

## Code Signing (optional)

Signing is disabled by default. To enable:

```bash
mvn clean package -Dapp.signing.enabled=true \
    -Dkeystore.file=path/to/cert.p12 \
    -Dkeystore.password=yourpassword \
    -Dkeystore.alias=youralias
```

Requires `signtool.exe` from Windows SDK on PATH.

---

## REST API (Jetty)

When the application is running, data is also exposed via:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `http://localhost:8080/api/status` | GET | Overall status summary |
| `http://localhost:8080/api/proxy`  | GET | Proxy + system metrics |
| `http://localhost:8080/api/cctv`   | GET | All CCTV data |
| `http://localhost:8080/api/alerts` | GET | Unresolved alerts |
| `http://localhost:8080/api/network`| GET | Network speed history |
| `http://localhost:8080/api/alerts/{id}/resolve` | POST | Resolve an alert |
