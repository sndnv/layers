package io.github.sndnv.layers

import org.apache.pekko.Done
import org.apache.pekko.util.Timeout
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait UnitSpec extends AsyncFlatSpec with Matchers {
  implicit val timeout: Timeout = 500.milliseconds

  implicit class AwaitableFuture[T](val future: Future[T]) {
    def await: T = Await.result(future, timeout.duration)
  }

  def await(delay: FiniteDuration)(implicit system: org.apache.pekko.actor.typed.ActorSystem[_]): Unit = {
    val _ = org.apache.pekko.pattern.after(duration = delay)(Future.successful(Done)).await
  }

  def after[T](
    delay: FiniteDuration
  )(value: => Future[T])(implicit system: org.apache.pekko.actor.typed.ActorSystem[_]): Future[T] =
    org.apache.pekko.pattern.after(duration = delay)(value)

  def withRetry(f: => Future[Assertion]): Future[Assertion] =
    withRetry(times = 2)(f = f)

  def withRetry(times: Int)(f: => Future[Assertion]): Future[Assertion] =
    try {
      f.recoverWith {
        case e if times > 0 =>
          alert(s"Test failed with [${e.getMessage}]; retrying...")
          withRetry(times = times - 1)(f = f)
      }
    } catch {
      case e if times > 0 =>
        alert(s"Test failed with [${e.getMessage}]; retrying...")
        withRetry(times = times - 1)(f = f)
    }
}
