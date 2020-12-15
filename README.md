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

See [Postman collection](./scala-microservice.postman_collection.json) for available requests.


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


Cluster singleton
-----------------

The package `com.jambit.singleton` contains two actors: `UsserActivity`and `UserCoordinator`. The `UserCoordinator` is 
a cluster singleton. It's responsible for coordinating `UserActivity` actors.
At startup time, the `UserCoordinator` restores all Usernames from its persistent state with CQRS. Every Username is 
also a name of a child actor of`UserActivity`. Like the `UserCoordinator` actor, every restored `UserActivity` actor 
gets its persistent state from the database.

The difference to 'database storage' is, every `User` object has its own actor for state handling. If you have only one 
big list of `User`objects, it's difficult to split the list and distribute the parts in a cluster. With many small 
actors, it's easier to implement this. The cluster singleton in this example is to show you a way to have only one 
coordinating instance in a cluster. The singleton is instantiated at the oldest node in the cluster. So you can not use
this method to have only one `UserActivity` actor per Username. All actors wil be created on only one node.

In a real cluster setup, you might also have a NodeCoordinator. In this case, the NodeCoordinator manages all 
`UserActivity` actors on its node. The `UserCoordinator` only manages a list of node in the cluster. With this list, the
NodeCoordinator actors can manage sharding of `UserActivity` actors to all nodes and (re-)balancing the amount of 
`UserActivity` on each node.
