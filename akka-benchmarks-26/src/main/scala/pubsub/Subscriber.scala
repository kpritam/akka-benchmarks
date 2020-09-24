package pubsub

import akka.actor.typed.{ActorRef, ActorSystem}
import akka.stream.FlowShape
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.stage.GraphStage

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import akka.Done
import subscription._
import akka.stream.ActorAttributes
import akka.stream.Supervision
import akka.actor.Cancellable

class Subscriber(implicit actorSystem: ActorSystem[_]) {
  private val attributes =
    ActorAttributes.supervisionStrategy(_ => Supervision.Stop)

  def subscriptionModeStage(
      every: FiniteDuration,
      mode: SubscriptionMode
  ): GraphStage[FlowShape[String, String]] =
    mode match {
      case RateAdapterMode => new RateAdapterStage[String](every)
      case RateLimiterMode => new RateLimiterStage[String](every)
    }

  def subscribeAsync(
      eventSource: Source[String, Cancellable],
      callback: String => Future[_]
  ): Cancellable =
    eventSource
      .mapAsync(1)(x => callback(x))
      .withAttributes(attributes)
      .to(Sink.ignore)
      .run()

  def subscribeCallback(
      eventSource: Source[String, Cancellable],
      callback: String => Unit
  ): Cancellable =
    eventSource.to(Sink.foreach(callback)).withAttributes(attributes).run()

}
