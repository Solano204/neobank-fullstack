FROM maven:3-eclipse-temurin-24 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src src
RUN mvn -q package -DskipTests

FROM public.ecr.aws/lambda/java:17
COPY --from=build /build/target/*.jar ${LAMBDA_TASK_ROOT}/lib/
CMD ["neobank.NotificationHandler::handleRequest"]
