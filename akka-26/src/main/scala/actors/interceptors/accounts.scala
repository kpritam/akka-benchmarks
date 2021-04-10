
package actors.interceptors

import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed._
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

object AccountService {
  import scala.concurrent.ExecutionContext.Implicits.global

  def credit(amount: Long): Future[Unit] =
    Future {
      println(s"[AccountService] Crediting $amount")
      Thread.sleep(1000)
    }

  def debit(amount: Long): Future[Unit] =
    Future {
      println(s"[AccountService] Debiting $amount")
      Thread.sleep(1000)
    }
}

object AccountBehavior {
  sealed trait Command
  sealed trait Transaction                                                                                         extends Command
  final case class Credit(amount: Long, replyTo: ActorRef[TransactionResponse])                                    extends Command with Transaction
  final case class Debit(amount: Long, replyTo: ActorRef[TransactionResponse])                                     extends Command with Transaction
  final case class GetBalance(replyTo: ActorRef[Long])                                                             extends Command
  private final case class Credited(amount: Long, replyTo: ActorRef[TransactionResponse])                          extends Command
  private final case class Debited(amount: Long, replyTo: ActorRef[TransactionResponse])                           extends Command
  private final case class Rollback(command: Transaction, reasons: String, replyTo: ActorRef[TransactionResponse]) extends Command

  sealed trait TransactionResponse
  final case object Successful extends TransactionResponse
  final case object Failed     extends TransactionResponse

  def initial(balance: Long): Behavior[Command] = {
    Behaviors.setup[Command] { ctx =>
      Behaviors.receiveMessage { msg =>
        println(s"[AccountBehavior(initial)] Received $msg")

        msg match {
          case credit @ Credit(amount, replyTo) =>
            ctx.pipeToSelf(AccountService.credit(amount)) {
              case Failure(ex) => Rollback(credit, ex.getMessage, replyTo)
              case Success(_)  => Credited(amount, replyTo)
            }
            crediting(amount)
          case debit @ Debit(amount, replyTo) =>
            ctx.pipeToSelf(AccountService.debit(amount)) {
              case Failure(ex) => Rollback(debit, ex.getMessage, replyTo)
              case Success(_)  => Debited(amount, replyTo)
            }
            debiting(balance)
          case GetBalance(replyTo) => replyTo ! balance; Behaviors.same
          case _                   => Behaviors.unhandled
        }
      }
    }
  }

  private def crediting(balance: Long): Behaviors.Receive[Command] =
    Behaviors.receiveMessage { msg =>
      println(s"[AccountBehavior(crediting)] Received $msg")
      msg match {
        case Credited(amount, replyTo) =>
          replyTo ! Successful
          initial(balance + amount)
        case Rollback(msg, reason, replyTo) =>
          replyTo ! Failed
          println(s"Rolling back transaction $msg failed, reason: $reason")
          initial(balance)
        case _ => Behaviors.unhandled
      }
    }

  private def debiting(balance: Long): Behaviors.Receive[Command] =
    Behaviors.receiveMessage { msg =>
      println(s"[AccountBehavior(debiting)] Received $msg")

      msg match {
        case Debited(amount, replyTo) =>
          replyTo ! Successful
          initial(balance - amount)
        case Rollback(msg, reason, replyTo) =>
          replyTo ! Failed
          println(s"Rolling back transaction $msg failed, reason: $reason")
          initial(balance)
        case _ => Behaviors.unhandled
      }
    }
}

object Main {
  private val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(SpawnProtocol(), "AccountActor")
  private implicit val scheduler: Scheduler              = system.scheduler
  private implicit val timeout: Timeout                  = Timeout(5.seconds)

  private def ask[Req, Res](ref: ActorRef[Req], msg: ActorRef[Res] => Req) = Await.result(ref ? msg, timeout.duration)
  import AccountBehavior._

  private def logOnBehaviorTransitionInterceptor[T: ClassTag] =
    new BehaviorInterceptor[T, T]() {
      override def aroundReceive(
          ctx: TypedActorContext[T],
          msg: T,
          target: BehaviorInterceptor.ReceiveTarget[T]
      ): Behavior[T] = {
        val nextBehavior = target(ctx, msg)
        println(s"============== $msg PROCESSED ==============")
        nextBehavior
      }
    }

  private def logOnBehaviorTransition[T: ClassTag](behavior: Behavior[T]) =
    Behaviors.intercept { () => logOnBehaviorTransitionInterceptor[T] }(behavior)

  def main(args: Array[String]): Unit = {
    val accountActor =
      Await.result(
        system ? { ref: ActorRef[ActorRef[AccountBehavior.Command]] =>
          SpawnProtocol.Spawn(
            logOnBehaviorTransition(AccountBehavior.initial(0)),
            "AccountActor",
            Props.empty,
            ref
          )
        },
        timeout.duration
      )

    try {
      ask(accountActor, Credit(100, _))
      ask(accountActor, Credit(100, _))
      println(ask(accountActor, GetBalance))
      ask(accountActor, Debit(50, _))
      println(ask(accountActor, GetBalance))
    }
    finally {
      system.terminate()
    }

  }

}
