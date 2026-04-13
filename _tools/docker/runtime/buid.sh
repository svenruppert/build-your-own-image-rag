#!/bin/bash
    echo start building the images
    docker build -t svenruppert/temurin-prod:17.0.7 .

    docker tag svenruppert/temurin-prod:latest svenruppert/temurin-prod:17.0.7
    #docker push svenruppert/temurin-prod:17.0.7

    #docker image rm svenruppert/temurin-prod:latest
