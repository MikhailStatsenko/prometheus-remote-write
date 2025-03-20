FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY build/libs/*.jar consumer-service.jar
ENTRYPOINT ["java","-jar","consumer-service.jar"]
