package uk.co.thirdthing

import cats.effect.{ExitCode, IO, IOApp, Resource}
import natchez.Trace.Implicits.noop
import org.http4s.Uri
import org.http4s.blaze.client.{BlazeClientBuilder, ParserMode}
import org.http4s.client.Client
import skunk.Session
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.co.thirdthing.clients.{RightmoveApiClient, RightmoveHtmlClient}
import uk.co.thirdthing.config.{JobSchedulerConfig, JobSeederConfig}
import uk.co.thirdthing.consumer.{JobRunnerConsumer, JobScheduleTriggerConsumer, JobSeedTriggerConsumer}
import uk.co.thirdthing.metrics.{CloudWatchMetricsRecorder, MetricsRecorder}
import uk.co.thirdthing.model.Model.RunJobCommand
import uk.co.thirdthing.secrets.{AmazonSecretsManager, SecretsManager}
import uk.co.thirdthing.service.{JobRunnerService, JobScheduler, JobSeeder, RetrievalService}
import uk.co.thirdthing.sqs.{SqsConfig, SqsProcessingStream, SqsPublisher}
import uk.co.thirdthing.store.{DynamoJobStore, Initializer, PostgresPropertyStore}

import scala.concurrent.duration._

object Main extends IOApp {

  final case class Resources(
    apiHttpClient: Client[IO],
    htmlScraperHttpClient: Client[IO],
    dynamoClient: DynamoDbAsyncClient,
    sqsClient: SqsAsyncClient,
    db: Resource[IO, Session[IO]],
    cloudWatchClient: CloudWatchAsyncClient
  )

  override def run(args: List[String]): IO[ExitCode] =
    buildSecretsManager.use { secretsManager =>
      resources(secretsManager).use { r =>
        val metricsRecorder = CloudWatchMetricsRecorder[IO](r.cloudWatchClient)
        Initializer.createDynamoTablesIfNotExisting[IO](r.dynamoClient) *>
          Initializer.createPostgresTablesIfNotExisting[IO](r.db) *>
          startJobSeedTriggerProcessingStream(r.apiHttpClient, r.dynamoClient, r.sqsClient, secretsManager)
            .concurrently(startJobScheduleTriggerProcessingStream(r.dynamoClient, r.sqsClient, secretsManager))
            .concurrently(
              startJobRunnerProcessingStream(
                r.sqsClient,
                r.apiHttpClient,
                r.htmlScraperHttpClient,
                r.dynamoClient,
                r.db,
                secretsManager,
                metricsRecorder
              )
            )
            .compile
            .drain
            .as(ExitCode.Success)
      }
    }

  private def buildSecretsManager: Resource[IO, SecretsManager] =
    Resource.fromAutoCloseable[IO, SecretsManagerClient](IO(SecretsManagerClient.builder().build())).map(AmazonSecretsManager(_))

  private def buildJobSeedTriggerConsumer(apiHttpClient: Client[IO], dynamoClient: DynamoDbAsyncClient) = {
    val jobStore           = DynamoJobStore[IO](dynamoClient)
    val jobSederConfig     = JobSeederConfig.default
    val rightmoveApiClient = RightmoveApiClient(apiHttpClient, Uri.unsafeFromString("https://api.rightmove.co.uk"))
    val jobSeeder          = JobSeeder[IO](rightmoveApiClient, jobStore, jobSederConfig)
    JobSeedTriggerConsumer(jobSeeder)
  }

  private def startJobSeedTriggerProcessingStream(
    apiHttpClient: Client[IO],
    dynamoClient: DynamoDbAsyncClient,
    sqsClient: SqsAsyncClient,
    secretsManager: SecretsManager
  ): fs2.Stream[IO, Unit] =
    fs2.Stream.eval(secretsManager.secretFor("job-seeder-queue-url")).flatMap { queueUrl =>
      val consumer  = buildJobSeedTriggerConsumer(apiHttpClient, dynamoClient)
      val sqsConfig = SqsConfig(queueUrl, 20.seconds, 5.minutes, 1.minute, 10.seconds, 1.minute, 1)
      new SqsProcessingStream[IO](sqsClient, sqsConfig, "Job Seed Trigger").startStream(consumer)
    }

  private def buildJobScheduleTriggerConsumer(sqsClient: SqsAsyncClient, dynamoClient: DynamoDbAsyncClient, runJobCommandQueueUrl: String) = {
    val jobStore           = DynamoJobStore[IO](dynamoClient)
    val jobSchedulerConfig = JobSchedulerConfig.default
    val publisher          = new SqsPublisher[IO, RunJobCommand](sqsClient)(runJobCommandQueueUrl)
    val jobScheduler       = JobScheduler[IO](jobStore, publisher, jobSchedulerConfig)
    JobScheduleTriggerConsumer(jobScheduler)
  }

  private def startJobScheduleTriggerProcessingStream(
    dynamoClient: DynamoDbAsyncClient,
    sqsClient: SqsAsyncClient,
    secretsManager: SecretsManager
  ): fs2.Stream[IO, Unit] =
    fs2.Stream.eval(secretsManager.secretFor("run-job-commands-queue-url")).flatMap { runJobCommandQueueUrl =>
      fs2.Stream.eval(secretsManager.secretFor("job-schedule-trigger-queue-url")).flatMap { jobScheduleTriggerQueueUrl =>
        val consumer  = buildJobScheduleTriggerConsumer(sqsClient, dynamoClient, runJobCommandQueueUrl)
        val sqsConfig = SqsConfig(jobScheduleTriggerQueueUrl, 20.seconds, 5.minutes, 1.minute, 10.seconds, 1.minute, 1)
        new SqsProcessingStream[IO](sqsClient, sqsConfig, "Job Schedule Trigger").startStream(consumer)
      }
    }

  private def buildJobRunnerConsumer(
    apiHttpClient: Client[IO],
    htmlHttpClient: Client[IO],
    dynamoClient: DynamoDbAsyncClient,
    dbPool: Resource[IO, Session[IO]],
    metricsRecorder: MetricsRecorder[IO]
  ) = {
    val jobStore = DynamoJobStore[IO](dynamoClient)
//    val propertyListingStore = DynamoPropertyListingStore[IO](dynamoClient)
    val postgresPropertyStore = PostgresPropertyStore[IO](dbPool)
    val rightmoveApiClient    = RightmoveApiClient(apiHttpClient, Uri.unsafeFromString("https://api.rightmove.co.uk"))
    val rightmoveHtmlClient   = RightmoveHtmlClient(htmlHttpClient, Uri.unsafeFromString("https://www.rightmove.co.uk"))
    val retrievalService      = RetrievalService[IO](rightmoveApiClient, rightmoveHtmlClient)
    val jobRunnerService      = JobRunnerService[IO](jobStore, postgresPropertyStore, retrievalService, metricsRecorder)
    JobRunnerConsumer(jobRunnerService)
  }

  private def startJobRunnerProcessingStream(
    sqsClient: SqsAsyncClient,
    apiHttpClient: Client[IO],
    htmlScraperHttpClient: Client[IO],
    dynamoClient: DynamoDbAsyncClient,
    dbPool: Resource[IO, Session[IO]],
    secretsManager: SecretsManager,
    metricsRecorder: MetricsRecorder[IO]
  ): fs2.Stream[IO, Unit] =
    fs2.Stream.eval(secretsManager.secretFor("run-job-commands-queue-url")).flatMap { runJobCommandQueueUrl =>
      val consumer  = buildJobRunnerConsumer(apiHttpClient, htmlScraperHttpClient, dynamoClient, dbPool, metricsRecorder)
      val sqsConfig = SqsConfig(runJobCommandQueueUrl, 20.seconds, 5.minutes, 1.minute, 10.seconds, 100.milliseconds, 8)
      new SqsProcessingStream[IO](sqsClient, sqsConfig, "Job Runner").startStream(consumer)
    }

  private def databaseSessionPool(secretsManager: SecretsManager): Resource[IO, Resource[IO, Session[IO]]] = {
    val secrets = for {
      host     <- secretsManager.secretFor("postgres-host")
      username <- secretsManager.secretFor("postgres-user")
      password <- secretsManager.secretFor("postgres-password")
    } yield (host, username, password)

    Resource.eval(secrets).flatMap {
      case (host, username, password) =>
        Session.pooled[IO](
          host = host,
          port = 5432,
          user = username,
          database = "propertyhistory",
          password = Some(password),
          max = 16
        )
    }

  }

  private def resources(secretsManager: SecretsManager): Resource[IO, Resources] =
    for {
      apiHttpClient <- BlazeClientBuilder[IO].withMaxTotalConnections(30).withRequestTimeout(20.seconds).withMaxWaitQueueLimit(1500).resource
      htmlScraperHtmlClient <- BlazeClientBuilder[IO]
                                .withParserMode(ParserMode.Lenient)
                                .withMaxResponseLineSize(8192)
                                .withMaxTotalConnections(30)
                                .withMaxWaitQueueLimit(1500)
                                .withBufferSize(16384)
                                .withRequestTimeout(20.seconds)
                                .resource
      dynamoClient     <- Resource.fromAutoCloseable[IO, DynamoDbAsyncClient](IO(DynamoDbAsyncClient.builder().build()))
      sqsClient        <- Resource.fromAutoCloseable[IO, SqsAsyncClient](IO(SqsAsyncClient.builder().build()))
      cloudwatchClient <- Resource.fromAutoCloseable[IO, CloudWatchAsyncClient](IO(CloudWatchAsyncClient.builder().build()))
      dbPool           <- databaseSessionPool(secretsManager)
    } yield Resources(apiHttpClient, htmlScraperHtmlClient, dynamoClient, sqsClient, dbPool, cloudwatchClient)
}
