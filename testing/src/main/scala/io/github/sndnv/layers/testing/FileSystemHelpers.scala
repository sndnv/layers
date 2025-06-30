package io.github.sndnv.layers.testing

import java.nio.file._

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.FileIO
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

trait FileSystemHelpers {
  import FileSystemHelpers._

  implicit class StringToTestResourcePath(resourcePath: String) {

    /**
      * Creates a path (on the default file system) using the string.
      *
      * @return the created path
      */
    def asTestResource: Path =
      Paths.get(getClass.getResource(resourcePath).getPath)
  }

  implicit class PathWithIO(resourcePath: Path) {

    /**
      * Checks if the path exists.
      *
      * See [[java.nio.file.Files#exists]] for more information.
      *
      * @return `true` if the path exists.
      */
    def exists: Boolean = Files.exists(resourcePath)

    /**
      * Writes the specified `content` to the path.
      *
      * @param content data to be written (as text / UTF-8)
      * @return result of the operation
      */
    def write(content: String)(implicit mat: Materializer): Future[Done] = {
      require(!Files.isDirectory(resourcePath), s"Expected [${resourcePath.toString}] to be a file")

      Source
        .single(ByteString(content))
        .runWith(FileIO.toPath(resourcePath))
        .map(_ => Done)(mat.executionContext)
    }

    /**
      * Reads the content from the path.
      *
      * @return data read from the file (as text / UTF-8)
      */
    def content(implicit mat: Materializer): Future[String] = {
      require(!Files.isDirectory(resourcePath), s"Expected [${resourcePath.toString}] to be a file")

      FileIO
        .fromPath(resourcePath)
        .runFold(ByteString.empty)(_ concat _)
        .map(_.utf8String)(mat.executionContext)
    }

    /**
      * Recursively deletes all files and directories on the path.
      *
      * @return result of the operation
      */
    def clear()(implicit mat: Materializer): Future[Done] = {
      implicit val ec: ExecutionContext = mat.executionContext

      require(Files.isDirectory(resourcePath), s"Expected [${resourcePath.toString}] to be a directory")

      val resourcePathAsString = resourcePath.toAbsolutePath.toString
      val target = "target"
      val testClasses = "test-classes"

      val pathIsUnderTarget = resourcePathAsString.contains(target)
      val pathIsUnderTestClasses = resourcePathAsString.contains(testClasses)
      require(
        pathIsUnderTarget && pathIsUnderTestClasses,
        s"Expected [${resourcePath.toString}] to be under $target/$testClasses"
      )

      val pathEndsInTarget = resourcePathAsString.endsWith(target) || resourcePathAsString.endsWith(s"$target/")
      val pathEndsInTestClasses = resourcePathAsString.endsWith(testClasses) || resourcePathAsString.endsWith(s"$testClasses/")
      require(
        !pathEndsInTarget && !pathEndsInTestClasses,
        s"Expected [${resourcePath.toString}] to be a child of $target/$testClasses"
      )

      def deleteEntity(path: Path): Future[Done] =
        Future {
          val _ = Files.deleteIfExists(path)
          Done
        }

      val stream: java.util.stream.Stream[Future[Done]] = Files
        .walk(resourcePath, Seq.empty[FileVisitOption]: _*)
        .filter(path => !Files.isHidden(path) && path != resourcePath)
        .sorted()
        .map {
          case path if Files.isDirectory(path) =>
            path
              .clear()
              .flatMap(_ => deleteEntity(path))
              .recoverWith { case _: NoSuchFileException => Future.successful(Done) }

          case path =>
            deleteEntity(path)
        }

      Future.sequence(stream.iterator().asScala).map(_ => Done)
    }

    /**
      * Retrieves all files on the path.
      *
      * @return the files that were found
      */
    def files(): Seq[Path] = {
      require(Files.isDirectory(resourcePath), s"Expected [${resourcePath.toString}] to be a directory")

      Files.walk(resourcePath).filter(Files.isRegularFile(_)).iterator().asScala.toSeq
    }
  }

  /**
    * Creates a new mock file system.
    *
    * @param setup file system configuration
    * @return the file system itself and the objects that were created as part of the setup
    */
  def createMockFileSystem(setup: FileSystemSetup): (FileSystem, FileSystemObjects) = {
    val filesystem = Jimfs.newFileSystem(
      Configuration.unix().toBuilder.setAttributeViews("basic", "posix").build()
    )

    val chars: Set[Char] = setup.chars
      .map { char =>
        if (setup.caseSensitive) char else char.toLower
      }
      .filterNot(setup.disallowedChars.contains)
      .toSet

    val rootDirectories = for {
      char <- chars
    } yield {
      s"root-dir-${char.toString}"
    }

    val nestedParentDirs = for {
      i <- 0 until setup.nestedParentDirs
    } yield {
      s"root/parent-${i.toString}"
    }

    val nestedDirectories = for {
      char <- chars
      parent <- nestedParentDirs
    } yield {
      s"$parent/child-dir-${char.toString}"
    }

    val files = chars
      .map(_.toString)
      .filterNot(setup.disallowedFileNames.contains)
      .take(setup.maxFilesPerDir)

    files.foreach { file =>
      Files.createFile(filesystem.getPath(file))
    }

    rootDirectories.foreach { directory =>
      val parent = Files.createDirectory(filesystem.getPath(directory))
      files.foreach { file =>
        Files.createFile(parent.resolve(filesystem.getPath(file)))
      }
    }

    nestedDirectories.foreach { directory =>
      val parent = Files.createDirectories(filesystem.getPath(directory))
      files.foreach { file =>
        Files.createFile(parent.resolve(filesystem.getPath(file)))
      }
    }

    (
      filesystem,
      FileSystemObjects(
        filesPerDir = files.size,
        rootDirs = rootDirectories.size,
        nestedParentDirs = nestedParentDirs.size,
        nestedChildDirsPerParent = chars.size,
        nestedDirs = nestedDirectories.size
      )
    )
  }
}

object FileSystemHelpers {

  /**
    * Configuration for the mock file system.
    *
    * Some configurations are already defined in [[FileSystemSetup.Unix]], [[FileSystemSetup.MacOS]]
    * and [[FileSystemSetup.Windows]].
    *
    * @param config underlying `jimfs` config
    * @param chars list of characters used for creating test directories and files during the setup
    * @param disallowedChars list of characters that are not allowed
    * @param disallowedFileNames list of file names that are not allowed
    * @param maxFilesPerDir maximum number of files per directory
    * @param nestedParentDirs nested parent directories
    * @param caseSensitive set to `true` to use uppercase and lowercase characters
    *                      or set to `false` to only use lowercase characters
    */
  final case class FileSystemSetup(
    config: Configuration,
    chars: Seq[Char],
    disallowedChars: Seq[Char],
    disallowedFileNames: Seq[String],
    maxFilesPerDir: Int,
    nestedParentDirs: Int,
    caseSensitive: Boolean
  ) {
    def withEmptyDirs: FileSystemSetup =
      copy(maxFilesPerDir = 0, nestedParentDirs = 0)
  }

  object FileSystemSetup {
    def empty: FileSystemSetup = Unix.withEmptyDirs

    object Chars {
      final val Default: Seq[Char] = (Byte.MinValue to Byte.MaxValue).map(_.toChar)
      final val AlphaNumeric: Seq[Char] = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
    }

    final val Unix: FileSystemSetup = FileSystemSetup(
      config = Configuration.unix(),
      chars = Chars.Default,
      disallowedChars = Seq('\u0000', '/', '\n', '\r'),
      disallowedFileNames = Seq(".", ".."),
      maxFilesPerDir = Int.MaxValue,
      nestedParentDirs = 4,
      caseSensitive = true
    )

    final val MacOS: FileSystemSetup = FileSystemSetup(
      config = Configuration.osX(),
      chars = Chars.Default,
      disallowedChars = Seq('\u0000', '/', '\n', '\r'),
      disallowedFileNames = Seq(".", ".."),
      maxFilesPerDir = Int.MaxValue,
      nestedParentDirs = 4,
      caseSensitive = false
    )

    final val Windows: FileSystemSetup = FileSystemSetup(
      config = Configuration.windows(),
      chars = Chars.Default,
      disallowedChars = (0 to 31).map(_.toChar) ++ Seq(
        '<', '>', ':', '"', '/', '\\', '|', '?', '*'
      ),
      disallowedFileNames = Seq(" ", "."),
      maxFilesPerDir = Int.MaxValue,
      nestedParentDirs = 4,
      caseSensitive = false
    )
  }

  /**
    * Report on the number of objets that were created on the mock file system.
    *
    * @param filesPerDir number of files created, per directory
    * @param rootDirs number of root directories created (ex: `/root-dir-a`)
    * @param nestedParentDirs number of parent directories created (ex: `/root/parent-a`)
    * @param nestedChildDirsPerParent number of child directories per parent directory (ex: `/root/parent-a/child-dir-a`)
    * @param nestedDirs total number of child directories (ex: `/root/parent-a/child-dir-a`)
    */
  final case class FileSystemObjects(
    filesPerDir: Int,
    rootDirs: Int,
    nestedParentDirs: Int,
    nestedChildDirsPerParent: Int,
    nestedDirs: Int
  ) {

    /**
      * Total number of files and directories created.
      */
    lazy val total: Int = filesPerDir + rootDirs * filesPerDir + nestedDirs * filesPerDir
  }
}
