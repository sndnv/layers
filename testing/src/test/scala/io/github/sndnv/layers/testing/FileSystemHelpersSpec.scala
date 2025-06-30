package io.github.sndnv.layers.testing

import java.nio.file.Files

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

class FileSystemHelpersSpec extends UnitSpec with FileSystemHelpers {
  "FileSystemHelpers" should "support creating mock file systems (empty)" in {
    import FileSystemHelpers._

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
    import FileSystemHelpers._

    val (_, createdObjects) = createMockFileSystem(setup = FileSystemSetup.Unix)

    createdObjects.rootDirs should be >= 1
    createdObjects.nestedChildDirsPerParent should be >= 1

    createdObjects.nestedParentDirs should be >= 1
    createdObjects.nestedDirs should be >= 1

    createdObjects.filesPerDir should be >= 1
    createdObjects.total should be >= 1
  }

  they should "provide helper methods" in {
    import FileSystemHelpers._

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

  they should "support providing test resources" in {
    "/test-file".asTestResource.content.await.trim should be("test-content")
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "FileSystemHelpersSpec"
  )
}
