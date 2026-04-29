package io.github.sndnv.layers.testing

import java.nio.file.Files

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

class FileSystemHelpersSpec extends UnitSpec with FileSystemHelpers {
  "A FileSystemSetup" should "provide its name" in {
    import FileSystemHelpers.*

    FileSystemSetup.Unix.toString should be("Unix")
    FileSystemSetup.MacOS.toString should be("MacOS")
    FileSystemSetup.Windows.toString should be("Windows")
  }

  "FileSystemHelpers" should "support creating mock file systems (empty)" in {
    import FileSystemHelpers.*

    val (_, createdObjects) = createMockFileSystem(setup = FileSystemSetup.empty)

    // root directories are always created
    createdObjects.rootDirs should be >= 1
    createdObjects.nestedChildDirsPerParent should be >= 1

    createdObjects.nestedParentDirs should be(0)
    createdObjects.nestedDirs should be(0)

    createdObjects.filesPerDir should be(0)
    createdObjects.total should be(0)
  }

  they should "support creating mock file systems (non-empty)" in {
    import FileSystemHelpers.*

    val (_, createdObjects) = createMockFileSystem(setup = FileSystemSetup.Unix)

    createdObjects.rootDirs should be >= 1
    createdObjects.nestedChildDirsPerParent should be >= 1

    createdObjects.nestedParentDirs should be >= 1
    createdObjects.nestedDirs should be >= 1

    createdObjects.filesPerDir should be >= 1
    createdObjects.total should be >= 1
  }

  they should "provide helper methods" in {
    import FileSystemHelpers.*

    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.Unix)

    val file = fs.getPath("/target/test-classes/a/b/c")
    val parent = file.getParent

    parent.exists should be(false)
    Files.createDirectories(parent)
    parent.exists should be(true)

    file.exists should be(false)

    file.write(content = "test").await

    file.exists should be(true)

    file.content.await should be("test")

    parent.files() should be(Seq(file))

    parent.getParent.clear().await

    parent.getParent.files() should be(empty)
    parent.exists should be(false)
    file.exists should be(false)
  }

  they should "normalize Unix paths for a Unix filesystem" in {
    import FileSystemHelpers.*

    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.Unix.withEmptyDirs)

    fs.normalize("/test") should be("/test")
    fs.normalize("/test/file-1") should be("/test/file-1")
    fs.normalize("/a/b/c") should be("/a/b/c")
  }

  they should "normalize Unix paths for a MacOS filesystem" in {
    import FileSystemHelpers.*

    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.MacOS.withEmptyDirs)

    fs.normalize("/test") should be("/test")
    fs.normalize("/test/file-1") should be("/test/file-1")
    fs.normalize("/a/b/c") should be("/a/b/c")
  }

  they should "normalize Unix paths for a Windows filesystem" in {
    import FileSystemHelpers.*

    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.Windows.withEmptyDirs)

    fs.normalize("/test") should be("C:\\test")
    fs.normalize("/test/file-1") should be("C:\\test\\file-1")
    fs.normalize("/a/b/c") should be("C:\\a\\b\\c")
  }

  they should "normalize Windows paths for a Unix filesystem" in {
    import FileSystemHelpers.*

    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.Unix.withEmptyDirs)

    fs.normalize("C:\\test") should be("/test")
    fs.normalize("C:\\test\\file-1") should be("/test/file-1")
    fs.normalize("C:\\a\\b\\c") should be("/a/b/c")
  }

  they should "normalize Windows paths for a Windows filesystem" in {
    import FileSystemHelpers.*

    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.Windows.withEmptyDirs)

    fs.normalize("C:\\test") should be("C:\\test")
    fs.normalize("C:\\test\\file-1") should be("C:\\test\\file-1")
    fs.normalize("C:\\a\\b\\c") should be("C:\\a\\b\\c")
  }

  they should "normalize Windows paths for a MacOS filesystem" in {
    import FileSystemHelpers.*

    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.MacOS.withEmptyDirs)

    fs.normalize("C:\\test") should be("/test")
    fs.normalize("C:\\test\\file-1") should be("/test/file-1")
    fs.normalize("C:\\a\\b\\c") should be("/a/b/c")
  }

  they should "support providing test resources" in {
    "/test-file".asTestResource.content.await.trim should be("test-content")
  }

  they should "support providing the root of a filesystem" in {
    import FileSystemHelpers.*

    createMockFileSystem(setup = FileSystemSetup.Unix.withEmptyDirs)._1.root should be("/")
    createMockFileSystem(setup = FileSystemSetup.MacOS.withEmptyDirs)._1.root should be("/")
    createMockFileSystem(setup = FileSystemSetup.Windows.withEmptyDirs)._1.root should be("C:\\")
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "FileSystemHelpersSpec"
  )
}
