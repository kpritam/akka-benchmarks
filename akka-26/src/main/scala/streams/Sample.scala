package streams

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.Source

import scala.concurrent.duration._

object Sample extends App {

  private implicit val system = ActorSystem(Behaviors.empty, "perf")
  private implicit val ec = system.executionContext

  val start = System.nanoTime()
  val done = Source
    .repeat("tick")
    .throttle(1, 10.millis)
    // .tick(10.millis, 10.millis, ())
    .statefulMapConcat { () =>
      var previous = start
      _ => {
        val now = System.nanoTime()
        val duration = now - previous
        previous = now
        List(duration)
      }
    }
    .groupedWithin(Int.MaxValue, 1.second)
    .take(120)
    .runForeach { ticks =>
      val durationMillis = ticks.map(_.nanos.toMillis)
      println(s"${ticks.size} ticks: [${durationMillis.mkString(", ")}]")
    }
}
