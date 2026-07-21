# Dockerfile Notes — neobank-backend

Doc 3 deliverable. Better starting point than the other 3 projects: already multi-stage, already Alpine-based (smaller than the full JDK/JRE images used elsewhere), already has dependency-layer caching.

## Fixed: graceful shutdown was configured nowhere, and the entrypoint would have defeated it anyway

Two compounding gaps, both fixed together since one made the other pointless:

1. `application.yml` had no `server.shutdown=graceful` at all (same gap as the other 3 projects) - added it.
2. The Dockerfile's `ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]` is exec-form Docker syntax, but `java` still runs as a *child* of the `sh` process, not as PID 1 itself - `sh -c` doesn't forward `SIGTERM` to an un-exec'd child by default. Even with graceful shutdown configured in Spring, the JVM would never have received the signal that triggers it - `docker stop` would just run out the grace period and get SIGKILLed. Fixed by adding `exec` (`exec java $JAVA_OPTS -jar app.jar`), which replaces the shell process with `java` so it becomes PID 1 and receives `SIGTERM` directly. The `sh -c` wrapper itself is kept (it's what allows `$JAVA_OPTS` to expand at runtime) - only the missing `exec` was the bug.

## Fixed: non-root user

Same gap as all 3 other projects - `eclipse-temurin` (Alpine variant too) runs as root by default. Added `addgroup`/`adduser` + `chown` + `USER spring`.

## Already good

Base images pinned reasonably (Alpine variants, smaller attack surface than the full JDK/JRE images seen elsewhere this session) - not flagging the exact-patch-pin gap here since Alpine tags already narrow the surface more than the other projects' base images did.
