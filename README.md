Scala Microservices
===================

This repository shows different methods to handle Actor states in combination with Akka HTTP. 

- in memory
- in database
- in combination with cluster singleton

__The code is only for educational purposes and not intended for production use.__

The same problem is solved in different ways. Receive an HTTP-Request and process the data. To keep the different 
implementations comparable, there is only the bare minimum functionality implemented. 

The REST API is simple:
- create user object
- get user object
- delete user object
- read all user objects

See [Postman collection](./scala-microservice.postman_collection.json) for available requests


In memory storage
-----------------

The package `com.jembit.memory` contains an actor `MemoryUserRegistry`. The actor stores the state in-memory. This is 
implemented using the return value to keep the state between each invocation. The `CreateUser` message sends a reply
and returns `registry(users + user)`. The new Behaviour is the same function, but with a new argument - a set with the 
new user inside. 

In memory storage looses data when the actor terminates or the whole actor system. This pattern is used for caching 
data or storing intermediate results. 


Database storage
----------------

The package `com.jambit.database` contains an actor `DatabaseUserRegistry`. The actor uses 
[CQRS](https://martinfowler.com/bliki/CQRS.html). The `commandHandler` function processes all commands. If 
`Effect.persist` is called, the argument is used to invoke `eventHandler`. The argument is stored in a database for 
recreating the actor. The return value is the new state - the `replyto(...)(...)` function gets the new state for 
further processing. The next call to `commandHandler` also gets the new state.

If the actor with the same `PersistenceId` is created, all events are read from the database and applied to 
`eventHandler`. After restoring the last state, the next commands are processed.

__For easy testing, a LevelDB database is used. The data is stored in the `journal` folder. Don't use this setup for
production!__

