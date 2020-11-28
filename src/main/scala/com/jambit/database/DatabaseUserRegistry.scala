package com.jambit.database

import akka.actor.typed.Behavior
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import akka.persistence.typed.PersistenceId
import com.jambit._

object DatabaseUserRegistry {

  // actor events
  sealed trait Event
  final case class CreateUserEvent(user: User) extends Event with CborSerializable
  final case class DeleteUserEvent(name: String) extends Event with CborSerializable

  // actor state
  final case class State(users: Set[User])

  def apply(entityId: String): Behavior[Command] =
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      persistenceId = PersistenceId.of("UserRegitry", entityId),
      emptyState = State(Set.empty),
      commandHandler = commandHandler,
      eventHandler = eventHandler
    )

  val commandHandler: (State, Command) => ReplyEffect[Event, State] = { (state, command) =>
    command match {
      case GetUsers(replyTo) => Effect.none.thenReply(replyTo)(state => Users(state.users.toSeq))
      case CreateUser(user, replyTo) => Effect.persist(CreateUserEvent(user)).thenReply(replyTo)(_ => ActionPerformed(s"User ${user.name} created."))
      case GetUser(name, replyTo) => Effect.none.thenReply(replyTo)(state => GetUserResponse(state.users.find(_.name == name)))
      case DeleteUser(name, replyTo) => Effect.persist(DeleteUserEvent(name)).thenReply(replyTo)(_ => ActionPerformed(s"User $name deleted."))
    }
  }

  val eventHandler: (State, Event) => State = { (state, event) =>
    event match {
      case CreateUserEvent(user) => State(state.users + user)
      case DeleteUserEvent(name) => State(state.users.filterNot(_.name == name))
    }
  }
}
