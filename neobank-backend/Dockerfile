FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN ./mvnw dependency:go-offline -B

COPY src src

RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Alpine's eclipse-temurin runs as root by default - was never given a
# non-root user.
RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /app/target/*.jar app.jar
RUN chown spring:spring /app/app.jar

USER spring

EXPOSE 8080

ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Was ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"] with no `exec` -
# that form is exec-form Docker syntax, but it still runs java as a *child*
# of the sh process, not as PID 1 itself. sh -c does not forward SIGTERM to
# an un-exec'd child by default, so a `docker stop` would leave java never
# receiving the signal at all - it would just run out its 10s grace period
# and get SIGKILLed, defeating server.shutdown=graceful (added this pass -
# it had nowhere to matter without this fix, since the JVM never saw the
# signal that triggers it). Adding `exec` replaces the shell process with
# java, so java itself becomes PID 1 and receives SIGTERM directly.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]