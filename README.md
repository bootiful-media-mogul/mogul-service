# README

See [the project README for what we're all about](https://github.com/bootiful-media-mogul/.github/blob/main/profile/README.md).


## Architecture

This project uses Spring Modulith (along with Spring Data JDBC, Spring Data Elasticsearch, Spring GraphQL, Spring MVC, Spring Security, Spring Boot, Spring AI, Spring Cloud, etc.) to build a well-designed, cycle-free API that manages all backend requirements.

The application builds and runs as a GraalVM native image. There's a script in the root, `./native.sh`, that does the work.

It uses Spring DevTools to facilitate live reload. This is very important because the application connects to countless external services, including Ably, an Elasticsearch cluster, a SQL database running in a Docker image, an Okta OAuth IdP, and more. 
