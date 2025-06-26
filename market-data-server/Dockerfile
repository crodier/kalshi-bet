FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy the JAR file
COPY target/market-data-server-*.jar app.jar

# Expose the application port
EXPOSE 8084

# Set JVM options for container environment
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]