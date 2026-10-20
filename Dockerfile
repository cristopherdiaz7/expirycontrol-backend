FROM maven:3.9-eclipse-temurin-21-noble AS base
WORKDIR /java/app/base

RUN apt-get update 
RUN apt-get install -y build-essential
RUN apt-get purge -y --auto-remove -o APT::AutoRemove::RecommendsImportant=false
RUN rm -rf /var/lib/apt/lists/*

COPY pom.xml .
RUN mvn dependency:resolve -B

FROM maven:3.9-eclipse-temurin-21-noble AS builder
WORKDIR /java/app/builder

COPY --from=base /root/.m2 /root/.m2
COPY . .
RUN mvn clean package -DskipTests -B

FROM gcr.io/distroless/java21-debian13:nonroot AS runtime
ENV SPRING_OUTPUT_ANSI_ENABLED=ALWAYS
ENV JAVA_OPTS=""

USER nobody
WORKDIR /app

COPY --from=builder /java/app/builder/target/app.jar /app/app.jar

# Agente de Application Insights (monitoreo en Azure), versión fija y verificada por checksum.
# Solo envía datos si recibe APPLICATIONINSIGHTS_CONNECTION_STRING; sin ella no envía nada.
# JDK_JAVA_OPTIONS lo lee el comando java al arrancar, sin cambiar el ENTRYPOINT.
ADD --chmod=444 --checksum=sha256:93a70c8f5d364c7e777f6c4d1b235dba91aef8448bd3fa94359f1d7f3e2eb0ec https://github.com/microsoft/ApplicationInsights-Java/releases/download/3.7.10/applicationinsights-agent-3.7.10.jar /app/applicationinsights-agent.jar
ENV JDK_JAVA_OPTIONS="-javaagent:/app/applicationinsights-agent.jar"
ENV APPLICATIONINSIGHTS_SELF_DIAGNOSTICS_DESTINATION=console
EXPOSE 8080

ENTRYPOINT [ "java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar", "${JAVA_OPTS}", "{0}", "{@}" ]

