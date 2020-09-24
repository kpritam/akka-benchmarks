import akka.stream.scaladsl.Source
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.Random
import akka.stream.scaladsl.Sink
import reporter._
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.typed.scaladsl.ActorMaterializer

object Sample extends App {

  private implicit val system = ActorSystem(Behaviors.empty, "perf")
  private implicit val ec = system.executionContext
  private implicit val mat = ActorMaterializer()

  Source
    .tick(0.millis, 10.millis, ())
    .map(_ => "Hello")
    .groupedWithin(Int.MaxValue, 1.second)
    .runForeach(xs => println(xs.length))

}
