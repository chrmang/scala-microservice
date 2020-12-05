package com.jambit

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.{Cluster, ClusterSingleton, Join, SingletonActor}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.jambit.database.DatabaseUserRegistry
import com.jambit.memory.MemoryUserRegistry
import com.jambit.singleton.UserCoordinator
import org.slf4j.LoggerFactory

import scala.util.{Failure, Success}

object QuickstartApp {

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

  def main(args: Array[String]): Unit = {
    // eager start of slf4j to avoid warnings
    LoggerFactory.getLogger("")

    val rootBehavior = Behaviors.setup[Nothing] { context =>
      // standard actors
      val databaseUserRegistryActor = context.spawn(DatabaseUserRegistry("uniqueId"), "DatabaseUserRegistryActor")
      context.watch(databaseUserRegistryActor)
      val memoryUserRegistryActor = context.spawn(MemoryUserRegistry(), "MemoryUserRegistryActor")
      context.watch(memoryUserRegistryActor)

      // singleton actor
      val singletonManager = ClusterSingleton(context.system)
      val singletonUserRegistryActor = singletonManager.init(SingletonActor(UserCoordinator("coordinatorId"), "UserCoordinator"))

      val databaseRoutes = new UserRoutes("database", databaseUserRegistryActor, None)(context.system)
      val memoryRoutes = new UserRoutes("memory", memoryUserRegistryActor, None)(context.system)
      val singletonRoutes = new UserRoutes("singleton",
        singletonUserRegistryActor.asInstanceOf[ActorRef[Command]], Some(singletonUserRegistryActor))(context.system)
      startHttpServer(concat(
        databaseRoutes.userRoutes,
        memoryRoutes.userRoutes,
        singletonRoutes.userRoutes
      ))(context.system)

      Behaviors.empty
    }
    val system = ActorSystem[Nothing](rootBehavior, "ClusterSystem")
    val cluster = Cluster(system)
    cluster.manager ! Join(cluster.selfMember.address)
  }
}
