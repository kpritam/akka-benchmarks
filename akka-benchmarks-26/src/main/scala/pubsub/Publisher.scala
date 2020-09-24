package pubsub

import akka.Done
import akka.actor.typed.ActorSystem
import akka.actor.{Cancellable, PoisonPill}
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}
import scala.util.control.NonFatal
import akka.stream.ActorAttributes
import akka.stream.Supervision
import scala.concurrent.duration._

class Publisher(implicit actorSystem: ActorSystem[_]) {

  import actorSystem.executionContext

  private val attributes =
    ActorAttributes.supervisionStrategy(_ => Supervision.Stop)
  private val parallelism = 1
  private val defaultInitialDelay: FiniteDuration = 0.millis

  def createSource(
      eventGenerator: => Future[String],
      every: FiniteDuration
  ): Source[String, Cancellable] =
    Source
      .tick(defaultInitialDelay, every, ())
      .mapAsync(parallelism)(_ => eventGenerator)

}
