#!/bin/bash
    echo start building the images
    docker build -t svenruppert/temurin-dev .

    docker tag svenruppert/temurin-dev:latest svenruppert/temurin-dev:17.0.7
    #docker push svenruppert/temurin-dev:17.0.7

    #docker image rm svenruppert/temurin-dev:latest
