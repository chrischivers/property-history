package uk.co.thirdthing

import cats.effect.{ExitCode, IO, IOApp, Resource}
import natchez.Trace.Implicits.noop
import org.http4s.Uri
import org.http4s.blaze.client.{BlazeClientBuilder, ParserMode}
import org.http4s.client.Client
import skunk.Session
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.co.thirdthing.clients.{RightmoveApiClient, RightmoveListingHtmlClient, RightmovePostcodeSearchHtmlClient}
import uk.co.thirdthing.config.{JobRunnerPollerConfig, JobSchedulingConfig, JobSeederConfig}
import uk.co.thirdthing.consumer.JobSeedTriggerConsumer
import uk.co.thirdthing.metrics.{CloudWatchMetricsRecorder, MetricsRecorder}
import uk.co.thirdthing.pollers.UpdatePropertyHistoryPoller
import uk.co.thirdthing.secrets.{AmazonSecretsManager, SecretsManager}
import uk.co.thirdthing.service.{JobSeeder, RetrievalService, UpdateAddressDetailsService, UpdatePropertyHistoryService}
import uk.co.thirdthing.sqs.SqsConfig.*
import uk.co.thirdthing.sqs.SqsConsumer.*
import uk.co.thirdthing.sqs.{SqsConfig, SqsProcessingStream}
import uk.co.thirdthing.store.{
  JobStore,
  PostgresAddressStore,
  PostgresInitializer,
  PostgresJobStore,
  PostgresPostcodeStore,
  PostgresPropertyStore
}

import scala.concurrent.duration.*

object Main extends IOApp:

  final case class Resources(
    apiHttpClient: Client[IO],
    htmlScraperHttpClient: Client[IO],
    sqsClient: SqsAsyncClient,
    db: Resource[IO, Session[IO]],
    cloudWatchClient: CloudWatchAsyncClient
  )

  override def run(args: List[String]): IO[ExitCode] =
    buildSecretsManager.use { secretsManager =>
      resources(secretsManager).use { r =>
        val metricsRecorder = CloudWatchMetricsRecorder[IO](r.cloudWatchClient)
        val jobStore        = PostgresJobStore[IO](r.db, JobSchedulingConfig.default)
        val postcodeStore   = PostgresPostcodeStore[IO](r.db, JobSchedulingConfig.default)
        val updatePropertyHistoryService =
          buildUpdatePropertyHistoryService(r.apiHttpClient, r.htmlScraperHttpClient, jobStore, r.db, metricsRecorder)
        val updatePropertyHistoryPoller =
          pollers.UpdatePropertyHistoryPoller[IO](jobStore, updatePropertyHistoryService)
        val updateAddressDetailsService =
          buildUpdateAddressDetailsService(r.htmlScraperHttpClient, r.db, metricsRecorder)
        val updateAddressDetailsPoller = pollers.AddressDetailsPoller(postcodeStore, updateAddressDetailsService)

        PostgresInitializer.initializeAll[IO](r.db) *>
          startJobSeedTriggerProcessingStream(r.apiHttpClient, r.sqsClient, secretsManager, jobStore)
            .concurrently(
              updatePropertyHistoryPoller.startPolling(JobRunnerPollerConfig.default.minimumPollingInterval)
            )
            .concurrently(updateAddressDetailsPoller.startPolling(JobRunnerPollerConfig.default.minimumPollingInterval))
            .compile
            .drain
            .as(ExitCode.Success)

      }
    }

  private def buildSecretsManager: Resource[IO, SecretsManager[IO]] =
    Resource
      .fromAutoCloseable[IO, SecretsManagerClient](IO(SecretsManagerClient.builder().build()))
      .map(AmazonSecretsManager[IO](_))

  private def buildJobSeedTriggerConsumer(apiHttpClient: Client[IO], jobStore: JobStore[IO]) =
    val rightmoveApiClient = RightmoveApiClient(apiHttpClient, Uri.unsafeFromString("https://api.rightmove.co.uk"))
    val jobSeeder          = JobSeeder[IO](rightmoveApiClient, jobStore, JobSeederConfig.default)
    JobSeedTriggerConsumer(jobSeeder)

  private def startJobSeedTriggerProcessingStream(
    apiHttpClient: Client[IO],
    sqsClient: SqsAsyncClient,
    secretsManager: SecretsManager[IO],
    jobStore: JobStore[IO]
  ): fs2.Stream[IO, Unit] =
    fs2.Stream.eval(secretsManager.secretFor("job-seeder-queue-url")).flatMap { queueUrl =>
      val consumer = buildJobSeedTriggerConsumer(apiHttpClient, jobStore)
      val sqsConfig = SqsConfig(
        QueueUrl(queueUrl),
        WaitTime(20.seconds),
        VisibilityTimeout(5.minutes),
        HeartbeatInterval(1.minute),
        RetrySleepTime(10.seconds),
        StreamThrottlingRate(1.minute),
        Parallelism(1)
      )
      new SqsProcessingStream[IO](sqsClient, sqsConfig, ConsumerName("Job Seed Trigger")).startStream(consumer)
    }

  private def buildUpdatePropertyHistoryService(
    apiHttpClient: Client[IO],
    htmlHttpClient: Client[IO],
    jobStore: JobStore[IO],
    dbPool: Resource[IO, Session[IO]],
    metricsRecorder: MetricsRecorder[IO]
  ) =
    val postgresPropertyStore = PostgresPropertyStore[IO](dbPool)
    val rightmoveApiClient    = RightmoveApiClient(apiHttpClient, Uri.unsafeFromString("https://api.rightmove.co.uk"))
    val rightmoveHtmlClient =
      RightmoveListingHtmlClient(htmlHttpClient, Uri.unsafeFromString("https://www.rightmove.co.uk"))
    val retrievalService = RetrievalService[IO](rightmoveApiClient, rightmoveHtmlClient)
    UpdatePropertyHistoryService[IO](jobStore, postgresPropertyStore, retrievalService, metricsRecorder)

  private def buildUpdateAddressDetailsService(
    htmlHttpClient: Client[IO],
    dbPool: Resource[IO, Session[IO]],
    metricsRecorder: MetricsRecorder[IO]
  ) =
    val postgresAddressStore  = PostgresAddressStore[IO](dbPool)
    val postgresPropertyStore = PostgresPropertyStore[IO](dbPool)
    val rightmovePostcodeSearchClient =
      RightmovePostcodeSearchHtmlClient(htmlHttpClient, Uri.unsafeFromString("https://www.rightmove.co.uk"))
    UpdateAddressDetailsService[IO](
      postgresAddressStore,
      postgresPropertyStore,
      rightmovePostcodeSearchClient,
      metricsRecorder
    )

  private def databaseSessionPool(secretsManager: SecretsManager[IO]): Resource[IO, Resource[IO, Session[IO]]] =
    val secrets = for
      host     <- secretsManager.secretFor("postgres-host")
      username <- secretsManager.secretFor("postgres-user")
      password <- secretsManager.secretFor("postgres-password")
    yield (host, username, password)

    Resource.eval(secrets).flatMap { case (host, username, password) =>
      Session.pooled[IO](
        host = host,
        port = 5432,
        user = username,
        database = "propertyhistory",
        password = Some(password),
        max = 50
      )
    }

  private def resources(secretsManager: SecretsManager[IO]): Resource[IO, Resources] =
    for
      apiHttpClient <- BlazeClientBuilder[IO]
        .withMaxTotalConnections(30)
        .withRequestTimeout(20.seconds)
        .withMaxWaitQueueLimit(1500)
        .resource
      htmlScraperHtmlClient <- BlazeClientBuilder[IO]
        .withParserMode(ParserMode.Lenient)
        .withMaxResponseLineSize(8192)
        .withMaxTotalConnections(30)
        .withMaxWaitQueueLimit(1500)
        .withBufferSize(16384)
        .withRequestTimeout(20.seconds)
        .resource
      sqsClient <- Resource.fromAutoCloseable[IO, SqsAsyncClient](IO(SqsAsyncClient.builder().build()))
      cloudwatchClient <- Resource.fromAutoCloseable[IO, CloudWatchAsyncClient](
        IO(CloudWatchAsyncClient.builder().build())
      )
      dbPool <- databaseSessionPool(secretsManager)
    yield Resources(apiHttpClient, htmlScraperHtmlClient, sqsClient, dbPool, cloudwatchClient)
