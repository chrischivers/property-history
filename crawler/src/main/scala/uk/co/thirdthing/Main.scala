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
import uk.co.thirdthing.clients.{RightmoveApiClient, RightmoveHtmlClient}
import uk.co.thirdthing.config.{JobSchedulerConfig, JobSeederConfig}
import uk.co.thirdthing.consumer._
import uk.co.thirdthing.consumer.{JobScheduleTriggerConsumer, JobSeedTriggerConsumer}
import uk.co.thirdthing.metrics.{CloudWatchMetricsRecorder, MetricsRecorder}
import uk.co.thirdthing.model.Model.RunJobCommand
import uk.co.thirdthing.secrets.{AmazonSecretsManager, SecretsManager}
import uk.co.thirdthing.service.{JobScheduler, JobSeeder, RetrievalService}
import uk.co.thirdthing.sqs.{SqsConfig, SqsProcessingStream, SqsPublisher}
import uk.co.thirdthing.store.{JobStore, PostgresInitializer, PostgresJobStore, PostgresPropertyStore}

import scala.concurrent.duration._

object Main extends IOApp {

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
        secretsManager.secretFor("run-job-commands-queue-url").flatMap { runJobCommandQueueUrl =>
          val metricsRecorder        = CloudWatchMetricsRecorder[IO](r.cloudWatchClient)
          val jobStore               = PostgresJobStore[IO](r.db)
          val runJobCommandPublisher = new SqsPublisher[IO, RunJobCommand](r.sqsClient)(runJobCommandQueueUrl)
          val jobScheduler           = JobScheduler[IO](jobStore, runJobCommandPublisher, JobSchedulerConfig.default)

            PostgresInitializer.createPropertiesTableIfNotExisting[IO](r.db) *>
            PostgresInitializer.createJobsTableIfNotExisting[IO](r.db) *>
            startJobSeedTriggerProcessingStream(r.apiHttpClient, r.sqsClient, secretsManager, jobScheduler, jobStore)
              .concurrently(startJobScheduleTriggerProcessingStream(r.sqsClient, secretsManager, jobScheduler))
              .compile
              .drain
              .as(ExitCode.Success)
        }
      }
    }

  private def buildSecretsManager: Resource[IO, SecretsManager[IO]] =
    Resource.fromAutoCloseable[IO, SecretsManagerClient](IO(SecretsManagerClient.builder().build())).map(AmazonSecretsManager[IO](_))

  private def buildJobSeedTriggerConsumer(apiHttpClient: Client[IO], jobStore: JobStore[IO], jobScheduler: JobScheduler[IO]) = {
    val rightmoveApiClient = RightmoveApiClient(apiHttpClient, Uri.unsafeFromString("https://api.rightmove.co.uk"))
    val jobSeeder          = JobSeeder[IO](rightmoveApiClient, jobStore, JobSeederConfig.default, jobScheduler)
    JobSeedTriggerConsumer(jobSeeder)
  }

  private def startJobSeedTriggerProcessingStream(
    apiHttpClient: Client[IO],
    sqsClient: SqsAsyncClient,
    secretsManager: SecretsManager[IO],
    jobScheduler: JobScheduler[IO],
    jobStore: JobStore[IO]
  ): fs2.Stream[IO, Unit] =
    fs2.Stream.eval(secretsManager.secretFor("job-seeder-queue-url")).flatMap { jobSeederQueueUrl =>
      val consumer = buildJobSeedTriggerConsumer(apiHttpClient, jobStore, jobScheduler)
      val sqsConfig = SqsConfig(
        QueueUrl(jobSeederQueueUrl),
        WaitTime(20.seconds),
        VisibilityTimeout(5.minutes),
        HeartbeatInterval(1.minute),
        RetrySleepTime(10.seconds),
        StreamThrottlingRate(1.minute),
        Parallelism(1)
      )
      new SqsProcessingStream[IO](sqsClient, sqsConfig, ConsumerName("Job Seed Trigger")).startStream(consumer)
    }

  private def startJobScheduleTriggerProcessingStream(
    sqsClient: SqsAsyncClient,
    secretsManager: SecretsManager[IO],
    jobScheduler: JobScheduler[IO]
  ): fs2.Stream[IO, Unit] =
    fs2.Stream.eval(secretsManager.secretFor("run-job-commands-queue-url")).flatMap { runJobCommandQueueUrl =>
      fs2.Stream.eval(secretsManager.secretFor("job-schedule-trigger-queue-url")).flatMap { jobScheduleTriggerQueueUrl =>
        val consumer = JobScheduleTriggerConsumer(jobScheduler)
        val sqsConfig = SqsConfig(
          QueueUrl(jobScheduleTriggerQueueUrl),
          WaitTime(20.seconds),
          VisibilityTimeout(5.minutes),
          HeartbeatInterval(1.minute),
          RetrySleepTime(10.seconds),
          StreamThrottlingRate(1.minute),
          Parallelism(1)
        )
        new SqsProcessingStream[IO](sqsClient, sqsConfig, ConsumerName("Job Schedule Trigger")).startStream(consumer)
      }
    }


  private def databaseSessionPool(secretsManager: SecretsManager[IO]): Resource[IO, Resource[IO, Session[IO]]] = {
    val secrets = for {
      host     <- secretsManager.secretFor("postgres-host")
      username <- secretsManager.secretFor("postgres-user")
      password <- secretsManager.secretFor("postgres-password")
    } yield (host, username, password)

    Resource.eval(secrets).flatMap { case (host, username, password) =>
      Session.pooled[IO](
        host = host,
        port = 5432,
        user = username,
        database = "propertyhistory",
        password = Some(password),
        max = 10
      )
    }
  }

  private def resources(secretsManager: SecretsManager[IO]): Resource[IO, Resources] =
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
      sqsClient        <- Resource.fromAutoCloseable[IO, SqsAsyncClient](IO(SqsAsyncClient.builder().build()))
      cloudwatchClient <- Resource.fromAutoCloseable[IO, CloudWatchAsyncClient](IO(CloudWatchAsyncClient.builder().build()))
      dbPool           <- databaseSessionPool(secretsManager)
    } yield Resources(apiHttpClient, htmlScraperHtmlClient, sqsClient, dbPool, cloudwatchClient)
}
