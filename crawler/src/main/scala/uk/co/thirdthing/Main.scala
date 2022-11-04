package uk.co.thirdthing

import cats.effect.{ExitCode, IO, IOApp, Resource}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.co.thirdthing.clients.RightmoveApiClient
import uk.co.thirdthing.config.JobSeederConfig
import uk.co.thirdthing.consumer.JobSeedRequestConsumer
import uk.co.thirdthing.secrets.{AmazonSecretsManager, SecretsManager}
import uk.co.thirdthing.service.JobSeeder
import uk.co.thirdthing.sqs.{SqsConfig, SqsProcessingStream}
import uk.co.thirdthing.store.DynamoJobStore

import scala.concurrent.duration._

object Main extends IOApp {

  final case class Resources(
    httpClient: Client[IO],
    dynamoClient: DynamoDbAsyncClient,
    sqsClient: SqsAsyncClient,
    secretsManagerClient: SecretsManagerClient
  )

  override def run(args: List[String]): IO[ExitCode] = resources.use { r =>
    val secretsManager = AmazonSecretsManager(r.secretsManagerClient)
    startJobSeederRequestProcessingStream(r.httpClient, r.dynamoClient, r.sqsClient, secretsManager).compile.drain.as(ExitCode.Success)
  }

  private def startJobSeederRequestProcessingStream(
    httpClient: Client[IO],
    dynamoClient: DynamoDbAsyncClient,
    sqsClient: SqsAsyncClient,
    secretsManager: SecretsManager
  ): fs2.Stream[IO, Unit] =
    fs2.Stream.eval(secretsManager.secretFor("job-seeder-queue-url")).flatMap { queueUrl =>
      val consumer  = buildJobSeedRequestConsumer(httpClient, dynamoClient)
      val sqsConfig = SqsConfig(queueUrl, 20.seconds, 5.minutes, 1.minute, 10.seconds, 1.minute, 5)
      new SqsProcessingStream[IO](sqsClient, sqsConfig).startStream(consumer)
    }

  private def buildJobSeedRequestConsumer(httpClient: Client[IO], dynamoClient: DynamoDbAsyncClient) = {
    val jobStore           = DynamoJobStore[IO](dynamoClient)
    val jobSederConfig     = JobSeederConfig.default
    val rightmoveApiClient = RightmoveApiClient(httpClient, Uri.unsafeFromString("https://api.rightmove.co.uk"))
    val jobSeeder          = JobSeeder[IO](rightmoveApiClient, jobStore, jobSederConfig)
    JobSeedRequestConsumer(jobSeeder)
  }

  private def resources: Resource[IO, Resources] =
    for {
      httpClient           <- EmberClientBuilder.default[IO].build
      dynamoClient         <- Resource.fromAutoCloseable[IO, DynamoDbAsyncClient](IO(DynamoDbAsyncClient.builder().build()))
      sqsClient            <- Resource.fromAutoCloseable[IO, SqsAsyncClient](IO(SqsAsyncClient.builder().build()))
      secretsManagerClient <- Resource.fromAutoCloseable[IO, SecretsManagerClient](IO(SecretsManagerClient.builder().build()))
    } yield Resources(httpClient, dynamoClient, sqsClient, secretsManagerClient)
}
