# build stage
FROM gradle:7.6.4-jdk17 AS build

WORKDIR /apps
COPY . .

WORKDIR /apps/passport-server

RUN ./gradlew clean generateProto
RUN ./gradlew build -x test

# runtime stage
FROM openjdk:17-jdk-slim
WORKDIR /apps

COPY --from=build /apps/passport-server/build/libs/*SNAPSHOT.jar app.jar

EXPOSE 9080
CMD ["java", "-jar", "app.jar"]