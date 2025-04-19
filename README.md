# MJPG Multiplier Server

## Overview
The **MJPG Multiplier Server** is a Spring Boot-based application designed to process MJPG (Motion JPEG) streams. It reads an MJPG stream from a configurable source, processes the data, and distributes the frames to multiple consumers via a queue-based mechanism. This project is ideal for scenarios where a single MJPG stream needs to be shared among multiple clients efficiently.

**Restriction:**

The stream must contain the Content-Length header and the header must come last. This behavior can be adapted in the `MjpegInputStreamReaderComponent` class.

## Features
- **Configurable Input Stream**: The MJPG stream URL is configurable via the `application.properties` file.
- **Concurrent Frame Processing**: Uses a thread-safe queue mechanism to distribute frames to multiple consumers.
- **Error Handling and Retry**: Automatically retries the connection to the MJPG stream in case of errors.
- **Spring Boot Integration**: Leverages Spring Boot for dependency injection and configuration management.

## Prerequisites
- **Java 17** or higher
- **Maven 3.6+**
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

        git clone repository-url
        cd repository-directory```
* Build the project:

        mvn clean install

* Run the application:

        mvn spring-boot:run

## Maven Release Process
This project uses the `maven-release-plugin` for managing releases. To perform a release:
* Prepare the release:
     
       mvn release:prepare

* Perform the release:

       mvn release:perform

    
## Reference Documentation
For further reference, consider the following:
- [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
- [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/3.4.4/maven-plugin)
- [Create an OCI image](https://docs.spring.io/spring-boot/3.4.4/maven-plugin/build-image.html)
- [Spring Web](https://docs.spring.io/spring-boot/3.4.4/reference/web/servlet.html)
- [Docker Compose Support](https://docs.spring.io/spring-boot/3.4.4/reference/features/dev-services.html#features.dev-services.docker-compose)

## License
This project is licensed under the MIT License. See the `LICENSE` file for details.

## Contributing
Contributions are welcome! Please fork the repository and submit a pull request with your changes.

## Contact
For questions or support, please contact the project maintainer.