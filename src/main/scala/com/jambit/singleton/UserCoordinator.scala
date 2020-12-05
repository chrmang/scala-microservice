package com.jambit.singleton

import akka.actor.typed.{ActorRef, Behavior, Scheduler}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect}
import akka.persistence.typed.PersistenceId
import akka.util.Timeout
import com.jambit._

import scala.concurrent.{Await, ExecutionContext, Future}

object UserCoordinator {

  // actor events
  sealed trait Event extends CborSerializable
  private final case class CreateUserEvent(userName: String) extends Event
  private final case class DeleteUserEvent(userName: String) extends Event

  // actor state
  final case class State(userNames: Set[String])

  def apply(entityId: String): Behavior[CommandWithUpdate] =
    Behaviors.setup { context =>
      val behaviour = userCoordinator(entityId, context)
      context.self ! Initialize
      behaviour
    }

  def userCoordinator(entityId: String, context: ActorContext[CommandWithUpdate]): Behavior[CommandWithUpdate] =
    EventSourcedBehavior.withEnforcedReplies[CommandWithUpdate, Event, State](
      persistenceId = PersistenceId.of("UserRegitry", entityId),
      emptyState = State(Set.empty),
      commandHandler = commandHandler(context),
      eventHandler = eventHandler
    )

  def commandHandler(context: ActorContext[CommandWithUpdate]): (State, Command) => ReplyEffect[Event, State] = { (state, command) =>
    command match {
      case Initialize =>
        state.userNames.map(userName => (userName,context.child(userName))).filter(_._2.isEmpty).map(_._1)
          .foreach(userName => context.spawn(UserActivity(userName), userName))
        Effect.none.thenNoReply()
      case GetUsers(replyTo) => processGetUsers(context, state, replyTo)
      case GetUser(userName, replyTo) => processGetUser(context, state, userName, replyTo)
      case CreateUser(user, replyTo) => processCreateUser(context, state, user, replyTo)
      case UpdateUser(user, replyTo) => processUpdateUser(context, state, user, replyTo)
      case DeleteUser(userName, replyTo) => processDeleteUser(context, userName, replyTo)
    }
  }

  val eventHandler: (State, Event) => State = { (state, event) =>
    event match {
      case CreateUserEvent(userName) => State(state.userNames + userName)
      case DeleteUserEvent(userName) => State(state.userNames.filterNot(_ == userName))
    }
  }

  def processGetUsers(context: ActorContext[CommandWithUpdate], state: State, replyTo: ActorRef[Users]): ReplyEffect[Event, State] = {
    implicit val executionContext: ExecutionContext = context.executionContext
    implicit val timeout: Timeout = Timeout.create(context.system.settings.config.getDuration("my-app.routes.actor-timeout"))
    implicit val scheduler: Scheduler = context.system.scheduler
    val users = state.userNames
      .map(userName => context.child(userName)).filter(_.isDefined).map(_.get)
      .map(actorRef => actorRef.asInstanceOf[ActorRef[UserActivity.Command]] ? UserActivity.GetUser).toList
    val list: List[GetUserResponse] = Await.result(Future.sequence(users), timeout.duration)
    Effect
      .none
      .thenReply(replyTo)(_ => Users(list.filter(_.maybeUser.isDefined).map(_.maybeUser.get)))
  }

  def processGetUser(context: ActorContext[CommandWithUpdate], state: State, userName: String, replyTo: ActorRef[GetUserResponse]): ReplyEffect[Event, State] = {
    val actorRef = context.child(userName)
    if (actorRef.isDefined) {
      actorRef.get.asInstanceOf[ActorRef[UserActivity.Command]] ! UserActivity.GetUser(replyTo)
      Effect.noReply
    }
    else {
      Effect
        .none
        .thenReply(replyTo)(_ => GetUserResponse(None))
    }
  }

  def processCreateUser(context: ActorContext[CommandWithUpdate], state: State, user: User, replyTo: ActorRef[ActionPerformed]): ReplyEffect[Event, State] = {
    val actorRef = context.child(user.name)
    if (actorRef.isEmpty) {
      val userRef = context.spawn(UserActivity(user.name), user.name)
      userRef ! UserActivity.CreateUser(user = user, replyTo = replyTo)
      Effect
        .persist(CreateUserEvent(user.name))
        .thenNoReply()
    } else {
      Effect
        .none
        .thenReply(replyTo)(_ => ActionPerformed(s"User ${user.name} already exists."))
    }
  }

  def processUpdateUser(context: ActorContext[CommandWithUpdate], state: State, user: User, replyTo: ActorRef[ActionPerformed]): ReplyEffect[Event, State] = {
    context.child(user.name)
      .map(actorRef => actorRef.asInstanceOf[ActorRef[UserActivity.Command]] ! UserActivity.UpdateUser(user, replyTo))
      .getOrElse(replyTo ! ActionPerformed(s"User ${user.name} not found"))
    Effect.none.thenNoReply()
  }

  def processDeleteUser(context: ActorContext[CommandWithUpdate], userName: String, replyTo: ActorRef[ActionPerformed]): ReplyEffect[Event, State] = {
    context.child(userName)
      .map( actorRef => actorRef.asInstanceOf[ActorRef[UserActivity.Command]] ! UserActivity.DeleteUser(replyTo))
      .getOrElse(replyTo ! ActionPerformed(s"User $userName not found"))
    Effect
      .persist(DeleteUserEvent(userName))
      .thenNoReply()
  }
}
