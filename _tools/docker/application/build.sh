#!/bin/bash
echo start building the images
cp ../../../target/application.jar .
docker build -t projectname/application .

docker tag projectname/application:latest projectname/application:0.0.1
#docker push projectname/application:0.0.1
