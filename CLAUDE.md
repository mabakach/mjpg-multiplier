# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Spring Boot 4.1 / Java 25 / Maven (wrapper `./mvnw` available).

- Build + test: `mvn clean install`
- Run: `mvn spring-boot:run`
- Single test class: `mvn test -Dtest=MyStromClientTest`
- Single test method: `mvn test -Dtest=MyStromClientTest#methodName`
- Release: `./release.sh` (runs `release:prepare`, `release:perform`, checks out the tag, builds Docker image tar via `jib:buildTar`, returns to original branch). Do not run casually: it tags and pushes.

## Architecture

MJPEG fan-out proxy: reads one upstream MJPEG stream, re-serves it to many HTTP clients.

Data flow:
1. `MjpegInputStreamReaderComponent` (`@PostConstruct`) starts a single-thread executor looping on `HttpInputStreamProvider.getInputStream()` (URL from `stream.url`). Retries after 1s on any error; sets `isBackendStreamAvailable`.
2. `processData` accumulates bytes in a buffer, finds `Content-Length:` header, cuts out one JPEG when enough bytes arrived. Hard assumption: upstream sends `Content-Length` as the **last** part header (see README restriction). Parsing lives in this class.
3. Each frame is pushed to every per-client `BlockingQueue<byte[]>` registered in `ImageQueueHolderComponent`. Queues are trimmed to 30 frames (drop oldest) so slow clients do not grow memory.
4. `StreamController` (`/api/stream.mjpg`) registers a new queue per request and writes `multipart/x-mixed-replace; boundary=FRAME` via `StreamingResponseBody`. `/api/status` reports backend availability. `AsyncConfig` sets near-infinite async timeout; `application.properties` disables Tomcat timeouts. Both needed for long-lived streams.

Health/monitoring: `BackendStreamHealthIndicator` and the `backendStream` actuator endpoint both expose `isBackendStreamAvailable`. All actuator endpoints exposed.

`mystrom/MyStromClient`: client for a myStrom smart switch powering the camera. Polls relay state every minute (`@Scheduled`, enabled in `MyStromSchedulingConfig`). `tryPowerCycleIfAllowed()` has a 5-minute cooldown; if relay is off it turns it on instead of cycling; if `/power_cycle` returns 404 it falls back to manual off, wait, on (`mystrom.powerCycleFallbackOffDuration`). Endpoint paths are all configurable via `mystrom.*` properties. `HttpInputStreamProvider` calls `tryPowerCycleIfAllowed()` when connecting to the upstream stream fails, so camera power-cycle is tied to backend failures.

Tests: `MyStromClientTest` uses the package-private constructor to inject an `HttpClient` and base URI.
