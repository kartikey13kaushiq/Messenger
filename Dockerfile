# Use an official OpenJDK runtime as a parent i
FROM eclipse-temurin:17-jdk-alpine

# Set the working directory to /app
WORKDIR /app

# Copy the current directory contents into the container at /app
COPY target/messenger-app.jar /app

# Expose port 8080
EXPOSE 8080

# Define environment variable
ENV JAVA_OPTS=""

# Run the JAR file
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar messenger-app.jar"]
