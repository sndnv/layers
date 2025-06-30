package io.github.sndnv.layers.testing

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.duration._
import scala.util.Random

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
object Generators {

  /**
    * Generates a new random duration between 0 and 1 day.
    *
    * @return the generated duration
    */
  def generateFiniteDuration(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): FiniteDuration =
    rnd.nextLong(0, 1.day.toSeconds).seconds

  /**
    * Generates a new sequence with the provider parameters.
    *
    * @param min minimum number of elements in the sequence (inclusive)
    * @param max maximum number of elements in the sequence (exclusive)
    * @param g element generator
    * @return the generated sequence
    */
  def generateSeq[T](
    min: Int = 0,
    max: Int = 10,
    g: => T
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): Seq[T] =
    LazyList.continually(g).take(rnd.nextInt(min, max))

  /**
    * Generates a new URI in the format `https://{host}:{port}/{endpoint}` with
    * `host`, `port` and `endpoint` randomly generated.
    *
    * @return the generated URI
    */
  def generateUri(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): String = {
    val host = generateString(withSize = 10)
    val port = rnd.nextInt(50000, 60000)
    val endpoint = generateString(withSize = 20)
    s"http://$host:${port.toString}/$endpoint".toLowerCase
  }

  /**
    * Generates a new random string with the specified size.
    *
    * @param withSize generated string size
    * @return the generated string
    */
  def generateString(
    withSize: Int
  )(implicit rnd: ThreadLocalRandom = ThreadLocalRandom.current()): String = {
    val random = Random.javaRandomToRandom(rnd)
    random.alphanumeric.take(withSize).mkString("")
  }
}
