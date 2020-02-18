# Instructions

This application would run a REST API server with in-memory storage. It's possible to create accounts and transfer funds between accounts.
It's a reactive application which extensively uses Kotlin coroutines.

(All commands are executed from the root folder of the project, OpenJDK 13 is recommended)

### Test

    $ ./gradlew test
    
### Integration Tests
    $ ./gradlew integTest
    
### Build

    $ ./gradlew build

### Run
Simplest way:

    $ ./gradlew run
    
It's possible to build a fat jar and run an standalone application:
    
    $ ./gradlew shadowJar
    $ java -jar ./build/libs/money-transfer-service-0.0.1-SNAPSHOT-all.jar
    
### Server

Application is running on default port 8080. 
Simplest way to start:

    $ ./gradlew run

```
curl http://localhost:8080/health
```   

### REST API Specification

See `docs/swagger.json`