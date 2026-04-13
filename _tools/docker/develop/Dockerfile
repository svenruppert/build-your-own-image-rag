FROM debian:bookworm-slim

LABEL maintainer="Sven Ruppert sven.ruppert@gmail.com" description="temurin-dev:17.0.7"

# Defining default Java and Maven version
ARG JAVA_VERSION="17.0.7-tem"
ARG MAVEN_VERSION="3.9.2"

# Defining default non-root user UID, GID, and name
ARG USER_UID="4000"
ARG USER_GID="4000"
ARG USER_NAME="worker"

# Creating default non-user
RUN groupadd -g $USER_GID $USER_NAME && \
	useradd -m -g $USER_GID -u $USER_UID $USER_NAME

# Installing basic packages
RUN apt-get update && \
	apt-get install -y zip unzip curl && \
	rm -rf /var/lib/apt/lists/* && \
	rm -rf /tmp/*

# Switching to non-root user to install SDKMAN!
# USER $USER_UID:$USER_GID

# Downloading SDKMAN!
RUN curl -s "https://get.sdkman.io" | bash

# Installing Java and Maven, removing some unnecessary SDKMAN files
RUN bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && \
    yes | sdk install java $JAVA_VERSION && \
    yes | sdk install maven $MAVEN_VERSION && \
    rm -rf $HOME/.sdkman/archives/* && \
    rm -rf $HOME/.sdkman/tmp/*"

#ENV PATH=/home/$USER_NAME/.sdkman/candidates/java/current/bin:$PATH
#ENV PATH=/home/$USER_NAME/.sdkman/candidates/maven/current/bin:$PATH
ENV PATH=/root/.sdkman/candidates/java/current/bin:$PATH
ENV PATH=/root/.sdkman/candidates/maven/current/bin:$PATH

CMD ["mvn"]