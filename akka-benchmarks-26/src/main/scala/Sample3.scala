import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.Source

import scala.concurrent.duration._

object Sample3 extends App {

  private implicit val system = ActorSystem(Behaviors.empty, "perf")
  private implicit val ec = system.executionContext

//   Source
//     .tick(0.millis, 10.millis, ())
//     .map(_ => "Hello")
//     .groupedWithin(Int.MaxValue, 1.second)
//     .runForeach(xs => println(xs.length))

  Source
    .repeat("tick")
    .throttle(100, 1.second)
    .map(_ => "Hello")
    .groupedWithin(Int.MaxValue, 1.second)
    .runForeach(xs => println(xs.length))
}
