FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY build/libs/*.jar producer-service.jar
ENTRYPOINT ["java","-jar","producer-service.jar"]
