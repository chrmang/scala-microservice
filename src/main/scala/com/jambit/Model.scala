package com.jambit

import akka.actor.typed.ActorRef

// actor protocol
sealed trait Command
final case class GetUsers(replyTo: ActorRef[Users]) extends Command
final case class CreateUser(user: User, replyTo: ActorRef[ActionPerformed]) extends Command
final case class GetUser(name: String, replyTo: ActorRef[GetUserResponse]) extends Command
final case class DeleteUser(name: String, replyTo: ActorRef[ActionPerformed]) extends Command

final case class ActionPerformed(description: String)
final case class GetUserResponse(maybeUser: Option[User])

// Model classes
final case class User(name: String, age: Int, countryOfResidence: String)
final case class Users(users: Seq[User])
