import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import scala.util.Random
import scala.concurrent.Future
import scala.concurrent.duration._
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import pubsub.reporter.ConsoleReporter
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink

object Sample1 extends App {

  private implicit val system = ActorSystem(Behaviors.empty, "perf")
  private implicit val ec = system.executionContext

  private val reporterExecutor: ExecutorService =
    Executors.newFixedThreadPool(1)

  private val reporter = new ConsoleReporter("perf")
  reporterExecutor.execute(reporter)

  Source
    .tick(0.millis, 50.millis, ())
    .mapAsync(1)(_ => Future(Random.alphanumeric.take(10).mkString))
    .to(Sink.foreach(report))
    .run()

  def report(event: String): Unit = {
    reporter.onMessage(1, event.getBytes().size)
  }
}
