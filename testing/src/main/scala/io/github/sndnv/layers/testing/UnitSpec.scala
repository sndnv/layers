package io.github.sndnv.layers.testing

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko.Done
import org.apache.pekko.util.Timeout
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers

trait UnitSpec extends AsyncFlatSpec with Matchers {
  implicit val timeout: Timeout = 500.milliseconds

  implicit class AwaitableFuture[T](val future: Future[T]) {

    /**
      * Awaits for the future to complete.
      *
      * Override [[timeout]] in the test spec to adjust the wait time.
      *
      * @return
      */
    def await: T = Await.result(future, timeout.duration)
  }

  /**
    * Blocks the current thread for the specified amount of time.
    *
    * @param delay amount of time to wait
    */
  def await(delay: FiniteDuration)(implicit system: org.apache.pekko.actor.typed.ActorSystem[_]): Unit = {
    val _ = org.apache.pekko.pattern.after(duration = delay)(Future.successful(Done)).await
  }

  /**
    * Executes the provided future after the specified amount of time.
    *
    * @param delay amount of time to wait
    * @param value future to run after the specified delay
    * @return the result of the future
    */
  def after[T](
    delay: FiniteDuration
  )(value: => Future[T])(implicit system: org.apache.pekko.actor.typed.ActorSystem[_]): Future[T] =
    org.apache.pekko.pattern.after(duration = delay)(value)

  /**
    * Retries all assertions/tests wrapped by this function.
    *
    * @param f tests to be retried
    * @return the final test outcome
    */
  def withRetry(f: => Future[Assertion]): Future[Assertion] =
    withRetry(times = 2)(f = f)

  /**
    * Retries all assertions/tests wrapped by this function.
    *
    * @param times number of times to retry the tests
    * @param f tests to be retried
    * @return the final test outcome
    */
  def withRetry(times: Int)(f: => Future[Assertion]): Future[Assertion] =
    try {
      f.recoverWith {
        case e if times > 0 =>
          alert(s"Test failed with [${e.getMessage}]; retrying...")
          withRetry(times = times - 1)(f = f)
      }(scala.concurrent.ExecutionContext.global)
    } catch {
      case e if times > 0 =>
        alert(s"Test failed with [${e.getMessage}]; retrying...")
        withRetry(times = times - 1)(f = f)
    }
}
