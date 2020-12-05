package com.jambit.singleton

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import akka.persistence.typed.PersistenceId
import com.jambit._

object UserActivity {

  // actor protocol
  sealed trait Command
  final case class CreateUser(user: User, replyTo: ActorRef[ActionPerformed]) extends Command
  final case class GetUser(replyTo: ActorRef[GetUserResponse]) extends Command
  final case class UpdateUser(user: User, replyTo: ActorRef[ActionPerformed]) extends Command
  final case class DeleteUser(replyTo: ActorRef[ActionPerformed]) extends Command

  // actor events
  sealed trait Event extends CborSerializable
  private final case class CreateUserEvent(user: User) extends Event
  private final case class UpdateUserEvent(user: User) extends Event
  private final case object DeleteUserEvent extends Event

  // actor state
  final case class State(user: Option[User])

  def apply(entityId: String): Behavior[Command] =
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      persistenceId = PersistenceId.of("UserActivity", entityId),
      emptyState = State(None),
      commandHandler = commandHandler,
      eventHandler = eventHandler
    )

  val commandHandler: (State, Command) => ReplyEffect[Event, State] = { (state, command) =>
    command match {
      case CreateUser(user, replyTo) =>
        if (state.user.isEmpty)
          Effect
            .persist(CreateUserEvent(user))
            .thenReply(replyTo)(_ => ActionPerformed(s"User ${user.name} created."))
        else
          Effect
            .none
            .thenReply(replyTo)(_ => ActionPerformed(s"User ${user.name} exists."))
      case GetUser(replyTo) =>
        Effect
          .none
          .thenReply(replyTo)(state => GetUserResponse(state.user))
      case UpdateUser(user, replyTo) =>
        Effect
          .persist(UpdateUserEvent(user))
          .thenReply(replyTo)(_ => ActionPerformed(s"User ${user.name} updated."))
      case DeleteUser(replyTo) =>
        Effect
          .persist(DeleteUserEvent)
          .thenStop()
          .thenReply(replyTo)(_ => ActionPerformed(s"User deleted."))
    }
  }

  val eventHandler: (State, Event) => State = { (oldstate, event) =>
    event match {
      case CreateUserEvent(user) => State(Some(user))
      case UpdateUserEvent(user) =>
        State(Some(oldstate.user.get.copy(age = user.age, countryOfResidence = user.countryOfResidence)))
      case DeleteUserEvent => State(None)
    }
  }
}
