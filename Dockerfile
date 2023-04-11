FROM alpine:3.17.3 as base

ARG SHELLCHECK_VERSION=0.9.0

RUN apk add --no-cache bash

RUN export EXTRACTED_DIR_NAME=shellcheck-v$SHELLCHECK_VERSION && \
  export ARCHIVE_NAME=$EXTRACTED_DIR_NAME.linux.x86_64.tar.xz && \
  wget https://github.com/koalaman/shellcheck/releases/download/v$SHELLCHECK_VERSION/$ARCHIVE_NAME && \
  tar -xf $ARCHIVE_NAME && \
  mv $EXTRACTED_DIR_NAME/shellcheck /usr/bin && \
  rm -rf $EXTRACTED_DIR_NAME $ARCHIVE_NAME

FROM base as dev

RUN apk add --no-cache openjdk11
COPY docs /docs
RUN adduser --uid 2004 --disabled-password --gecos "" docker
COPY target/universal/stage/ /workdir/
RUN chmod +x /workdir/bin/codacy-shellcheck
USER docker
WORKDIR /workdir
ENTRYPOINT ["bin/codacy-shellcheck"]

FROM base

COPY docs /docs
RUN adduser --uid 2004 --disabled-password --gecos "" docker
COPY target/graalvm-native-image/codacy-shellcheck /workdir/
USER docker
WORKDIR /workdir
ENTRYPOINT ["/workdir/codacy-shellcheck"]
