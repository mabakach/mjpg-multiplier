# MJPG Multiplier Server

## Overview
The **MJPG Multiplier Server** is a Spring Boot-based application designed to process MJPG (Motion JPEG) streams. It reads an MJPG stream from a configurable source, processes the data, and distributes the frames to multiple consumers via a queue-based mechanism. This project is ideal for scenarios where a single MJPG stream needs to be shared among multiple clients efficiently.

**Restriction:** The stream must contain the Content-Length header and the header must come last. This behavior can be adapted in the `MjpegInputStreamReaderComponent` class.

## Features
- **Configurable Input Stream**: The MJPG stream URL is configurable via the `application.properties` file.
- **Concurrent Frame Processing**: Uses a thread-safe queue mechanism to distribute frames to multiple consumers.
- **Error Handling and Retry**: Automatically retries the connection to the MJPG stream in case of errors.
- **Spring Boot Integration**: Leverages Spring Boot for dependency injection and configuration management.

## Prerequisites
- **Java 25**
- **Maven** (the Maven wrapper `./mvnw` is included)
- A running MJPG stream source

## Configuration
The application requires the MJPG stream URL to be specified in the `application.properties` file. Example:

`stream.url=http://example.com/stream.mjpg`

## How It Works
1. **Input Stream Provider**: The `HttpInputStreamProvider` fetches the MJPG stream from the configured URL.
2. **Frame Processing**: The `MjpegInputStreamReaderComponent` reads the stream, extracts frames, and distributes them to consumers via a queue.
3. **Queue Management**: Frames are stored in blocking queues, ensuring thread-safe access for multiple consumers.


## Build and Run
* Clone the repository:

        git clone https://github.com/mabakach/mjpg-multiplier.git
        cd mjpg-multiplier
* Build the project:

        mvn clean install

* Run the application:

        mvn spring-boot:run

## Docker Image
Every release publishes a Docker image to the GitHub Container Registry:

    docker pull ghcr.io/mabakach/mjpg-multiplier:latest

Use a version tag such as `1.0.30` to pin a release. The image is built for `linux/arm64` only.

Spring Boot reads its configuration from `/config/application.properties` inside the container. Mount your own file there:

    docker run -p 8080:8080 \
      -v ./application.properties:/config/application.properties \
      ghcr.io/mabakach/mjpg-multiplier:latest

Each [GitHub release](https://github.com/mabakach/mjpg-multiplier/releases) also has the image as a tar file. Load it with `docker load -i mjpg-multiplier-server-<version>.tar`.

## CI/CD
- `.github/workflows/ci.yml` builds and tests every pull request and every push to `master`.
- `.github/workflows/release.yml` runs when a release tag such as `1.0.30` is pushed. It builds the image with Jib, pushes it to GHCR and creates a GitHub release with the image tar attached.

## Release Process
Run `mvn release:prepare` on `master`. It sets the release version, creates and pushes the tag, and moves the project to the next snapshot version. The release workflow does the rest. `release.sh` is the manual variant of the same process.

## Reference Documentation
For further reference, consider the following:
- [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
- [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.2/maven-plugin)
- [Create an OCI image](https://docs.spring.io/spring-boot/4.1.2/maven-plugin/build-image.html)
- [Spring Web](https://docs.spring.io/spring-boot/4.1.2/reference/web/servlet.html)
- [Docker Compose Support](https://docs.spring.io/spring-boot/4.1.2/reference/features/dev-services.html#features.dev-services.docker-compose)

## License
This project is licensed under the MIT License. See the `LICENSE` file for details.

## Contributing
Contributions are welcome! Please fork the repository and submit a pull request with your changes.

## Contact
For questions or support, please contact the project maintainer.