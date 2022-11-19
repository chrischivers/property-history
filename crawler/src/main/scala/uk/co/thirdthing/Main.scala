package uk.co.thirdthing

import cats.effect.{ExitCode, IO, IOApp, Resource}
import org.http4s.Uri
import org.http4s.blaze.client.{BlazeClientBuilder, ParserMode}
import org.http4s.client.Client
import software.amazon.awssdk.services.cloudwatch.{CloudWatchAsyncClient, CloudWatchClient}
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
import uk.co.thirdthing.store.{DynamoJobStore, DynamoPropertyListingStore, Initializer}

import scala.concurrent.duration._

object Main extends IOApp {

  final case class Resources(
    apiHttpClient: Client[IO],
    htmlScraperHttpClient: Client[IO],
    dynamoClient: DynamoDbAsyncClient,
    sqsClient: SqsAsyncClient,
    secretsManagerClient: SecretsManagerClient,
    cloudWatchClient: CloudWatchAsyncClient
  )

  override def run(args: List[String]): IO[ExitCode] = resources.use { r =>
    val secretsManager  = AmazonSecretsManager(r.secretsManagerClient)
    val metricsRecorder = CloudWatchMetricsRecorder[IO](r.cloudWatchClient)
    Initializer.createTablesIfNotExisting[IO](r.dynamoClient) *>
      startJobSeedTriggerProcessingStream(r.apiHttpClient, r.dynamoClient, r.sqsClient, secretsManager)
        .concurrently(startJobScheduleTriggerProcessingStream(r.dynamoClient, r.sqsClient, secretsManager))
        .concurrently(
          startJobRunnerProcessingStream(r.sqsClient, r.apiHttpClient, r.htmlScraperHttpClient, r.dynamoClient, secretsManager, metricsRecorder)
        )
        .compile
        .drain
        .as(ExitCode.Success)
  }

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
    metricsRecorder: MetricsRecorder[IO]
  ) = {
    val jobStore             = DynamoJobStore[IO](dynamoClient)
    val propertyListingStore = DynamoPropertyListingStore[IO](dynamoClient)
    val rightmoveApiClient   = RightmoveApiClient(apiHttpClient, Uri.unsafeFromString("https://api.rightmove.co.uk"))
    val rightmoveHtmlClient  = RightmoveHtmlClient(htmlHttpClient, Uri.unsafeFromString("https://www.rightmove.co.uk"))
    val retrievalService     = RetrievalService[IO](rightmoveApiClient, rightmoveHtmlClient)
    val jobRunnerService     = JobRunnerService[IO](jobStore, propertyListingStore, retrievalService, metricsRecorder)
    JobRunnerConsumer(jobRunnerService)
  }

  private def startJobRunnerProcessingStream(
    sqsClient: SqsAsyncClient,
    apiHttpClient: Client[IO],
    htmlScraperHttpClient: Client[IO],
    dynamoClient: DynamoDbAsyncClient,
    secretsManager: SecretsManager,
    metricsRecorder: MetricsRecorder[IO]
  ): fs2.Stream[IO, Unit] =
    fs2.Stream.eval(secretsManager.secretFor("run-job-commands-queue-url")).flatMap { runJobCommandQueueUrl =>
      val consumer  = buildJobRunnerConsumer(apiHttpClient, htmlScraperHttpClient, dynamoClient, metricsRecorder)
      val sqsConfig = SqsConfig(runJobCommandQueueUrl, 20.seconds, 5.minutes, 1.minute, 10.seconds, 100.milliseconds, 6)
      new SqsProcessingStream[IO](sqsClient, sqsConfig, "Job Runner").startStream(consumer)
    }

  private def resources: Resource[IO, Resources] =
    for {
      apiHttpClient <- BlazeClientBuilder[IO].withMaxTotalConnections(20).withRequestTimeout(20.seconds).withMaxWaitQueueLimit(1024).resource
      htmlScraperHtmlClient <- BlazeClientBuilder[IO]
                                .withParserMode(ParserMode.Lenient)
                                .withMaxResponseLineSize(8192)
                                .withMaxTotalConnections(20)
                                .withMaxWaitQueueLimit(1024)
                                .withBufferSize(16384)
                                .withRequestTimeout(20.seconds)
                                .resource
      dynamoClient         <- Resource.fromAutoCloseable[IO, DynamoDbAsyncClient](IO(DynamoDbAsyncClient.builder().build()))
      sqsClient            <- Resource.fromAutoCloseable[IO, SqsAsyncClient](IO(SqsAsyncClient.builder().build()))
      secretsManagerClient <- Resource.fromAutoCloseable[IO, SecretsManagerClient](IO(SecretsManagerClient.builder().build()))
      cloudwatchClient     <- Resource.fromAutoCloseable[IO, CloudWatchAsyncClient](IO(CloudWatchAsyncClient.builder().build()))
    } yield Resources(apiHttpClient, htmlScraperHtmlClient, dynamoClient, sqsClient, secretsManagerClient, cloudwatchClient)
}
