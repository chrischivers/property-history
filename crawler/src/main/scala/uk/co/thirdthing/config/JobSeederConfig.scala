package uk.co.thirdthing.config

final case class JobSeederConfig (jobChunkSize: Long, startingMaxListingIdForFirstRun: Long, emptyRecordsToDetermineLatest: Long)

object JobSeederConfig {

  private val jobChunkSize                            = 5000
  private val startingMaxListingIdForFirstRun                 = 127974344L
  private val subsequentEmptyRecordsToDetermineLatest = 1000

  def default = JobSeederConfig(jobChunkSize, startingMaxListingIdForFirstRun, subsequentEmptyRecordsToDetermineLatest)
}
