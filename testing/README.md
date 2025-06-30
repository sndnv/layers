## `layers-testing`

Helper classes and functions for testing.

### Usage

#### `UnitSpec`

Provides functionality for handling `Future`s and retrying tests.

```
import io.github.sndnv.layers.testing._

class TestSpec extends UnitSpec {
  "A Test" should "test" in withRetry { // `withRetry` will retry the tests if any assertions fail
      val future: Future[String] = ???

      val result: String = future.await // `await` allows blocking until the future resolves
      
      result should be("test")
  }
}
```

#### `FileSystemHelpers`

Provides functionality for dealing with files and file systems; uses [`jimfs`](https://github.com/google/jimfs) as a mock file
system.

```
import io.github.sndnv.layers.testing._

class TestSpec extends UnitSpec with FileSystemHelpers {
  "A Test" should "test" in {
    val (fs, _) = createMockFileSystem(setup = FileSystemHelpers.FileSystemSetup.Unix) // creates a new file system

    val file = fs.getPath("/a/b/c")
    Files.createDirectories(file.getParent)

    file.exists should be(false)
    file.write(content = "test").await // writes the provided content to the file and awaits for it to complete
    file.exists should be(true)
  }
}
```

### `Generators`

Provides basic random data generators for various types (ex: `String`, `Uri`).
