# Instructions

This application would run a REST API server with in-memory storage. It's possible to create accounts and transfer funds between accounts.
It's a reactive application which extensively uses Kotlin coroutines.

(All commands are executed from the root folder of the project, OpenJDK 13 is recommended)

### Tests

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
    
### Request Examples (curl)

    $ curl -X POST -H 'Content-Type: application/json' -d '{"name": "Test1"}' http://localhost:8080/accounts
    $ curl -X POST -H 'Content-Type: application/json' -d '{"name": "Test2", "centAmount": 1000}' http://localhost:8080/accounts
    $ curl -X POST -H 'Content-Type: application/json' -d '{"fromAccountId": 2,"toAccountId":1 ,"centAmount":100}' http://localhost:8080/transactions
    
Transaction create response should look like:
```    
      {
        "id" : 1,
        "fromAccountId" : 2,
        "toAccountId" : 1,
        "centAmount" : 100,
        "status" : "SUCCESS",
        "createdAt" : 1582095641618
      }
```
    
### Server

Application is running on default port 8080. 

Server health:

    $ curl http://localhost:8080/health
   

### REST API Specification

See `docs/swagger.yaml`