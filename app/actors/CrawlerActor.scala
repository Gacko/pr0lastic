package actors

import actors.CrawlerActor.Start
import actors.CrawlerActor.Stop
import akka.actor.Actor
import play.api.Logger

/**
  * Marco Ebert 24.09.16
  */
object CrawlerActor {

  /**
    * Actor name.
    */
  final val Name = "crawler-actor"

  /**
    * Start message.
    */
  case object Start

  /**
    * Stop message.
    */
  case object Stop

}

final class CrawlerActor extends Actor {

  /**
    * Handle crawler actor messages.
    *
    * @return Receive.
    */
  override def receive: Receive = {
    case Start =>
      Logger info "CrawlerActor::receive: Received start message."
    case Stop =>
      Logger info "CrawlerActor::receive: Received stop message."
  }

}
