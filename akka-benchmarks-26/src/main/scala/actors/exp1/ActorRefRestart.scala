package actors.exp1

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

sealed trait AdminCmd
object AdminCmd {
  case class RestartWorker(replyTo: ActorRef[Done]) extends AdminCmd
  case class UpdateWorker(worker: ActorRef[WorkerCmd], replyTo: ActorRef[Done])
      extends AdminCmd
}

sealed trait WorkerCmd
object WorkerCmd {
  case class Restart(replyTo: ActorRef[Done]) extends WorkerCmd
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
  def behavior(worker: ActorRef[WorkerCmd]): Behavior[AdminCmd] =
    Behaviors.setup { ctx =>
      implicit val s = ctx.system.scheduler
      val workerRef = getRemoteRef(worker)(ctx.system)

      Behaviors.receiveMessage {
        case AdminCmd.RestartWorker(replyTo) =>
          printLine("[Admin] Received RestartWorker.")
          Await.result(workerRef ? (ref => WorkerCmd.Restart(ref)), 11.seconds)
          printLine("[Admin] Worker stopped successfully.")
          replyTo ! Done
          Behaviors.same
        case AdminCmd.UpdateWorker(newWorker, replyTo) =>
          printLine(s"[Admin] Received UpdateWorker newWorker = $newWorker.")
          replyTo ! Done
          behavior(newWorker)
      }
    }

}

object Worker {
  def behavior: Behavior[WorkerCmd] =
    Behaviors.receiveMessage {
      case WorkerCmd.Restart(replyTo) =>
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

object ActorRefRestart extends App {
  private val adminSystem = ActorSystem(SpawnProtocol(), "admin-system")
  private val workerSystem = ActorSystem(SpawnProtocol(), "worker-system")
  private implicit val s = adminSystem.scheduler

  def spawnAdmin(worker: ActorRef[WorkerCmd]) =
    ActorManager.spawn(Admin.behavior(worker), adminSystem)
  def spawnWorker = ActorManager.spawn(Worker.behavior, workerSystem)

  try {
    val worker = spawnWorker
    val admin = spawnAdmin(worker)

    (1 to Int.MaxValue).foreach { i =>
      println(s"==== $i ====")
      Await.result(admin ? (ref => AdminCmd.RestartWorker(ref)), 11.seconds)
      val newWorker = spawnWorker
      Await.result(
        admin ? (ref => AdminCmd.UpdateWorker(newWorker, ref)),
        11.seconds
      )
      Thread.sleep(100)
    }
  } finally {
    adminSystem.terminate()
    workerSystem.terminate()
  }

}
