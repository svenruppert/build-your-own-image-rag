FROM svenruppert/temurin-prod:17.0.7
LABEL maintainer="Sven Ruppert sven.ruppert@gmail.com" description="application:0.0.1"

COPY application.jar /application.jar
# This is the port that your javalin application will listen on
EXPOSE 7070
ENTRYPOINT ["java", "-jar", "/application.jar"]