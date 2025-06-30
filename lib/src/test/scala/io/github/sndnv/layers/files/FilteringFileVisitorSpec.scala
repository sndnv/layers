package io.github.sndnv.layers.files

import io.github.sndnv.layers.testing.FileSystemHelpers
import io.github.sndnv.layers.testing.FileSystemHelpers.FileSystemSetup
import io.github.sndnv.layers.testing.UnitSpec
import org.mockito.scalatest.AsyncMockitoSugar

class FilteringFileVisitorSpec extends UnitSpec with FileSystemHelpers with AsyncMockitoSugar with FilteringFileVisitorBehaviour {
  "A FilteringFileVisitor on a Unix filesystem" should behave like visitor(setup = FileSystemSetup.Unix)

  "A FilteringFileVisitor on a MacOS filesystem" should behave like visitor(setup = FileSystemSetup.MacOS)

  "A FilteringFileVisitor on a Windows filesystem" should behave like visitor(setup = FileSystemSetup.Windows)
}
