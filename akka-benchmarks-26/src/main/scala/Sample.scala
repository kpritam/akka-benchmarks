import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import scala.util.Random
import scala.concurrent.Future
import scala.concurrent.duration._
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import pubsub.reporter.ConsoleReporter
import pubsub._

object Sample extends App {

  private implicit val system = ActorSystem(Behaviors.empty, "perf")
  private implicit val ec = system.executionContext
  private val subscriber = new Subscriber
  private val publisher = new Publisher

  private val reporterExecutor: ExecutorService =
    Executors.newFixedThreadPool(1)

  private val reporter = new ConsoleReporter("perf")
  reporterExecutor.execute(reporter)

  val stream1 = publisher.createSource(
    Future(Random.alphanumeric.take(10).mkString),
    50.millis
  )

  subscriber.subscribeCallback(stream1, report)

  def report(event: String): Unit = {
    reporter.onMessage(1, event.getBytes().size)
  }
}
