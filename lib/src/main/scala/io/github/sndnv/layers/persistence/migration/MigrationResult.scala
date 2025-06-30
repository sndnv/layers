package io.github.sndnv.layers.persistence.migration

/**
  * Aggregated migration result.
  *
  * @param found total number of migrations found
  * @param executed total number of executed migrations
  */
final case class MigrationResult(found: Int, executed: Int) {

  /**
    * Adds the provided migration to this one.
    *
    * @param other migration to add
    * @return the updated migration
    */
  def +(other: MigrationResult): MigrationResult =
    MigrationResult(found = found + other.found, executed = executed + other.executed)
}

object MigrationResult {
  def empty: MigrationResult = MigrationResult(found = 0, executed = 0)
}
