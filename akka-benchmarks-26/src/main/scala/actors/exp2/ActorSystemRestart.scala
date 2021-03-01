package actors.exp2

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorRef
import akka.Done
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await
import akka.actor.typed.ActorSystem
import akka.actor.typed.Props
import akka.actor.typed.SpawnProtocol
import akka.actor.ActorSelection
import akka.actor.ActorContext
import akka.serialization.Serialization
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.ActorRefResolver
import akka.actor.CoordinatedShutdown

sealed trait AdminCmd
object AdminCmd {
  case class RestartWorker(replyTo: ActorRef[Done]) extends AdminCmd
  case class Shutdown(replyTo: ActorRef[Done]) extends AdminCmd
}

sealed trait WorkerCmd
object WorkerCmd {
  case class Stop(replyTo: ActorRef[Done]) extends WorkerCmd
}

object ConsoleLogger {
  private val enabled = false

  def printLine(msg: String) = if (enabled) println(msg)

}

object Timeouts {
  implicit val timeout: Timeout = 10.seconds
}

object ActorRefCodec {
  def encodeRef(ref: ActorRef[_]) =
    Serialization.serializedActorPath(ref.toClassic)

  def decodeRef(ref: String)(implicit system: ActorSystem[_]) =
    ActorRefResolver(system).resolveActorRef(ref)

  def getRemoteRef(ref: ActorRef[_])(implicit system: ActorSystem[_]) =
    decodeRef(encodeRef(ref))
}

import ConsoleLogger._
import Timeouts._
import ActorRefCodec._

object Admin {
  def behavior(workerWiring: WorkerWiring): Behavior[AdminCmd] =
    Behaviors.setup { ctx =>
      implicit val system = ctx.system
      val workerRef = getRemoteRef(workerWiring.worker)

      def shutdownWorker() = {
        printLine("[Admin] Received RestartWorker.")
        Await.result(workerRef ? (ref => WorkerCmd.Stop(ref)), 11.seconds)
        printLine("[Admin] Worker stopped successfully.")

        workerWiring.shutdown()
      }

      Behaviors.receiveMessage {
        case AdminCmd.RestartWorker(replyTo) =>
          shutdownWorker()
          val newWorkerWiring = new WorkerWiring
          replyTo ! Done
          behavior(newWorkerWiring)

        case AdminCmd.Shutdown(replyTo) =>
          shutdownWorker()
          replyTo ! Done
          Behaviors.stopped
      }
    }

}

object Worker {
  def behavior: Behavior[WorkerCmd] =
    Behaviors.receiveMessage {
      case WorkerCmd.Stop(replyTo) =>
        printLine(s"[Worker] Received Restart.")
        replyTo ! Done
        Behaviors.stopped
    }
}

object ActorManager {
  def spawn[T](
      behavior: Behavior[T],
      actorSystem: ActorSystem[SpawnProtocol.Command]
  ): ActorRef[T] = {
    implicit val s = actorSystem.scheduler

    Await.result(
      actorSystem ? (ref =>
        SpawnProtocol.Spawn(behavior, "worker", Props.empty, ref)
      ),
      11.seconds
    )
  }
}

class WorkerWiring {
  private lazy val workerSystem = ActorSystem(SpawnProtocol(), "worker-system")
  lazy val worker = ActorManager.spawn(Worker.behavior, workerSystem)

  def shutdown() = {
    Await.result(
      CoordinatedShutdown(workerSystem).run(
        CoordinatedShutdown.ActorSystemTerminateReason
      ),
      10.seconds
    )
  }

}

object ActorSystemRestart extends App {
  private implicit val adminSystem =
    ActorSystem(SpawnProtocol(), "admin-system")

  def spawnAdmin() =
    ActorManager.spawn(Admin.behavior(new WorkerWiring), adminSystem)

  try {
    val admin = spawnAdmin()
    val adminRef = getRemoteRef(admin).narrow[AdminCmd]

    (1 to Int.MaxValue).foreach { i =>
      println(s"==== $i ====")
      Await.result(adminRef ? (ref => AdminCmd.RestartWorker(ref)), 11.seconds)
      Thread.sleep(1000)
    }

    // cleanup
    Await.result(adminRef ? (ref => AdminCmd.Shutdown(ref)), 11.seconds)
  } finally {
    CoordinatedShutdown(adminSystem).run(
      CoordinatedShutdown.ActorSystemTerminateReason
    )
  }

}
