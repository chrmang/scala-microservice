package com.jambit.memory

//#user-registry-actor
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.jambit._

object MemoryUserRegistry {

  def apply(): Behavior[Command] = registry(Set.empty)

  private def registry(users: Set[User]): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetUsers(replyTo) =>
        replyTo ! Users(users.toSeq)
        Behaviors.same
      case CreateUser(user, replyTo) =>
        replyTo ! ActionPerformed(s"User ${user.name} created.")
        registry(users + user)
      case GetUser(name, replyTo) =>
        replyTo ! GetUserResponse(users.find(_.name == name))
        Behaviors.same
      case DeleteUser(name, replyTo) =>
        replyTo ! ActionPerformed(s"User $name deleted.")
        registry(users.filterNot(_.name == name))
    }
}
//#user-registry-actor
