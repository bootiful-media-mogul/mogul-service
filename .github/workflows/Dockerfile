FROM ubuntu:22.04

RUN apt-get update
RUN apt-get install -y ca-certificates imagemagick
RUN rm -rf /var/lib/apt/lists/*

COPY ./target/api /app
CMD ["/app"]


#FROM gcr.io/distroless/static
#COPY ./my-native-app /app
#CMD ["/app"]
