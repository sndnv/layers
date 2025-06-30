package io.github.sndnv.layers.files

import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import org.slf4j.Logger

/**
  * A [[java.nio.file.FileVisitor]] that uses the provided [[java.nio.file.PathMatcher]]
  * to collect matching files and directories.
  *
  * @param matcher the matcher to use for filtering
  */
class FilteringFileVisitor(matcher: PathMatcher) extends FileVisitor[Path] {
  private val collected = scala.collection.mutable.ListBuffer.empty[Path]
  private val collectedFailures = scala.collection.mutable.ListBuffer.empty[(Path, String)]

  /**
    * Provides all paths (files or directories) that were found.
    *
    * @return the matched paths
    */
  def matched: Seq[Path] = collected.toSeq

  /**
    * Provides all paths that could not be processed, along with the issue that was encountered for each one.
    *
    * @return
    */
  def failed: Seq[(Path, String)] = collectedFailures.toSeq

  /**
    * Walks the file tree from the specified `start` path.
    *
    * @param start starting path
    * @return the result
    */
  def walk(start: Path): FilteringFileVisitor.Result = {
    val _ = Files.walkFileTree(start, this)
    FilteringFileVisitor.Result(matched = matched, failed = failed)
  }

  override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
    Option(dir).filter(matcher.matches).foreach { current => collected.append(current) }
    FileVisitResult.CONTINUE
  }

  override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
    Option(file).filter(matcher.matches).foreach { current => collected.append(current) }
    FileVisitResult.CONTINUE
  }

  override def visitFileFailed(file: Path, exc: IOException): FileVisitResult = {
    val _ = collectedFailures.append(file -> s"${exc.getClass.getSimpleName} - ${exc.getMessage}")
    FileVisitResult.CONTINUE
  }

  override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult =
    FileVisitResult.CONTINUE
}

object FilteringFileVisitor {
  def apply(matcher: PathMatcher): FilteringFileVisitor =
    new FilteringFileVisitor(matcher)

  final case class Result(
    matched: Seq[Path],
    failed: Seq[(Path, String)]
  ) {

    /**
      * Provides only the successfully matched paths and logs all failures.
      *
      * @param log logger to use for any failures
      * @return the successfully matched paths
      */
    def successful(log: Logger): Seq[Path] = {
      failed.foreach { case (path, failure) =>
        log.debug("Visiting entity [{}] failed with [{}]", path.normalize().toAbsolutePath.toString, failure)
      }

      matched
    }
  }
}
