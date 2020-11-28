package com.jambit

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.jambit.database.DatabaseUserRegistry
import com.jambit.memory.MemoryUserRegistry
import akka.http.scaladsl.server.Directives._
import org.slf4j.LoggerFactory

import scala.util.Failure
import scala.util.Success

//#main-class
object QuickstartApp {
  //#start-http-server
  private def startHttpServer(routes: Route)(implicit system: ActorSystem[_]): Unit = {
    // Akka HTTP still needs a classic ActorSystem to start
    import system.executionContext

    val futureBinding = Http().newServerAt("localhost", 8080).bind(routes)
    futureBinding.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info("Server online at http://{}:{}/", address.getHostString, address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind HTTP endpoint, terminating system", ex)
        system.terminate()
    }
  }
  //#start-http-server
  def main(args: Array[String]): Unit = {
    // eager start of slf4j to avoid warnings
    LoggerFactory.getLogger("")

    //#server-bootstrapping
    val rootBehavior = Behaviors.setup[Nothing] { context =>
      val databaseUserRegistryActor = context.spawn(DatabaseUserRegistry("uniqueId"), "DatabaseUserRegistryActor")
      context.watch(databaseUserRegistryActor)
      val memoryUserRegistryActor = context.spawn(MemoryUserRegistry(), "MemoryUserRegistryActor")
      context.watch(memoryUserRegistryActor)

      val databaseRoutes = new UserRoutes("database", databaseUserRegistryActor)(context.system)
      val memoryRoutes = new UserRoutes("memory", memoryUserRegistryActor)(context.system)
      startHttpServer(concat(databaseRoutes.userRoutes, memoryRoutes.userRoutes))(context.system)

      Behaviors.empty
    }
    val system = ActorSystem[Nothing](rootBehavior, "HelloAkkaHttpServer")
    //#server-bootstrapping
  }
}
//#main-class
