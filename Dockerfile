FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="Jiangbo Li"
LABEL description="Smart Agent Framework"

WORKDIR /app

COPY smart-agent-start/target/smart-agent-start-*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar ${SPRING_PROFILES_ACTIVE:-}"]
