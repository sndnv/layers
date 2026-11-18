package io.github.sndnv.layers.testing

import java.nio.file.Files
import java.nio.file.LinkOption

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

  they should "support clearing nested directories" in {
    import FileSystemHelpers.*

    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.empty)

    val root = fs.getPath("/target/test-classes/a")
    val nested = Seq(
      root.resolve("file-1"),
      root.resolve("b/file-2"),
      root.resolve("b/c/file-3"),
      root.resolve("b/c/d/file-4"),
      root.resolve("e/f/file-5")
    )

    nested.foreach { path =>
      Files.createDirectories(path.getParent)
      Files.writeString(path, "test")
    }

    root.files().size should be(nested.size)

    root.clear().await

    root.exists should be(true)
    root.files() should be(empty)
    Files.list(root).count() should be(0)
  }

  they should "not follow symbolic links when clearing directories" in {
    import FileSystemHelpers.*

    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.empty)

    val root = fs.getPath("/target/test-classes/a")
    val link = root.resolve("link")
    val outside = fs.getPath("/other")
    val outsideFile = outside.resolve("file")

    Files.createDirectories(root)
    Files.createDirectories(outside)
    Files.writeString(outsideFile, "test")
    Files.createSymbolicLink(link, outside)

    root.clear().await

    Files.exists(link, LinkOption.NOFOLLOW_LINKS) should be(false)
    outsideFile.exists should be(true)
  }

  they should "fail to use helper methods with invalid paths" in {
    import FileSystemHelpers.*

    val (fs, _) = createMockFileSystem(setup = FileSystemSetup.empty)

    val testClasses = fs.getPath("/target/test-classes")
    val directory = testClasses.resolve("a")
    val file = directory.resolve("b")
    val outside = fs.getPath("/other")

    Files.createDirectories(directory)
    Files.createDirectories(outside)
    Files.writeString(file, "test")

    (the[IllegalArgumentException] thrownBy directory.write(content = "test")).getMessage should be(
      "requirement failed: Expected [/target/test-classes/a] to be a file"
    )

    (the[IllegalArgumentException] thrownBy directory.content).getMessage should be(
      "requirement failed: Expected [/target/test-classes/a] to be a file"
    )

    (the[IllegalArgumentException] thrownBy file.clear()).getMessage should be(
      "requirement failed: Expected [/target/test-classes/a/b] to be a directory"
    )

    (the[IllegalArgumentException] thrownBy outside.clear()).getMessage should be(
      "requirement failed: Expected [/other] to be under target/test-classes"
    )

    (the[IllegalArgumentException] thrownBy testClasses.clear()).getMessage should be(
      "requirement failed: Expected [/target/test-classes] to be a child of target/test-classes"
    )

    (the[IllegalArgumentException] thrownBy file.files()).getMessage should be(
      "requirement failed: Expected [/target/test-classes/a/b] to be a directory"
    )
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
