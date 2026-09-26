FROM ubuntu:25.04

ARG APP_NAME
ENV APP_NAME $APP_NAME

# no ffmpeg, no imagemagick: normalization, production and transcription all run in
# the processors image now, and this process forks no subprocesses at all.
RUN apt-get update
RUN apt-get install -y ca-certificates
RUN rm -rf /var/lib/apt/lists/*

COPY target/${APP_NAME} /app
CMD ["/app"]
