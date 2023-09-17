# Use a base image with Java (replace with the appropriate Java version)
FROM registry.access.redhat.com/ubi8/openjdk-17:1.16

# Set the working directory in the container
WORKDIR /app

# Copy the lib folder from your local machine to the container
COPY target/lib ./lib

# Copy your application JAR file (e.g., your-app.jar) from your local machine to the container
COPY target/rinha-1.0.0-SNAPSHOT.jar .

# Expose the port your Vert.x application listens on
EXPOSE 8080

# Define the command to run your application
CMD ["java", "-jar", "rinha-1.0.0-SNAPSHOT.jar", "-cp", "lib/*", "com.rinha.Main"]
