# classification-service — reads a ticket, suggests what it is, finds duplicates.
#
# Two stages. The first has Maven and a full JDK and builds the jar; the second has only a JRE and
# the jar. The shipped image carries no compiler, no Maven and no source code.

# ---- build -----------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# The pom alone first. Docker caches this layer, so dependencies are downloaded again only when the
# pom changes - not on every edit to a Java file.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Tests are skipped here, not dropped: `mvn test` runs before a push. Running them inside the image
# build would make every deploy wait on them a second time.
RUN mvn -B -q -DskipTests package

# ---- run -------------------------------------------------------------------------------------
# Ubuntu-based rather than Alpine: glibc, which the ONNX runtime in classification-service needs,
# and one base image for every service keeps them alike.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Not root. Nothing here needs it, and a process that is broken into should not own the container.
RUN useradd --system --create-home --uid 10001 app
COPY --from=build /app/target/*.jar app.jar
USER app

# A container has a memory limit the JVM should respect. 75% for the heap leaves room for threads,
# metaspace and native memory. Overridable from the platform's variables.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Railway sets PORT; application.properties reads it and falls back to 8083.
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
