package uk.co.thirdthing.sqs

import cats.effect.{IO, Ref, Resource}
import cats.syntax.all.*
import io.circe.Codec
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{CreateQueueRequest, DeleteQueueRequest, SendMessageRequest}
import uk.co.thirdthing.sqs.SqsConsumer.*
import uk.co.thirdthing.sqs.SqsConfig.*

import java.net.URI
import scala.concurrent.duration.*
import scala.jdk.FutureConverters.*
import scala.util.Random

class SqsConsumerTest extends munit.CatsEffectSuite:

  val queueName = "test"
  val config = SqsConfig(
    QueueUrl("http://localhost:4566/000000000000/test"),
    WaitTime(1.second),
    VisibilityTimeout(5.seconds),
    HeartbeatInterval(4.seconds),
    RetrySleepTime(2.seconds),
    StreamThrottlingRate(10.milliseconds),
    Parallelism(10)
  )
  case class TestMessage(message: String)
  given Codec[TestMessage] = deriveCodec

  val testMessage = TestMessage("Hi")

  test("It can consume a message from the queue") {

    val messagesConsumed = clientResource(queueName)
      .flatMap(client => Resource.eval(Ref.of[IO, List[TestMessage]](List.empty)).map(_ -> client))
      .use { case (consumedMessagesRef, client) =>
        val consumer = stubConsumer(consumedMessagesRef)
        val stream   = new SqsProcessingStream[IO](client, config, ConsumerName("name"))

        sendMessage(client, testMessage.asJson.spaces2) *>
          stream.startStream(consumer).compile.drain.timeout(10.seconds).attempt.void *>
          consumedMessagesRef.get

      }
    assertIO(messagesConsumed, List(testMessage))

  }

  test("It can consume multiple messages from the queue") {

    val testMessages = (0 to 100).toList.map(i => TestMessage(s"Hi - $i"))

    val messagesConsumed = clientResource(queueName)
      .flatMap(client => Resource.eval(Ref.of[IO, List[TestMessage]](List.empty)).map(_ -> client))
      .use { case (consumedMessagesRef, client) =>
        val consumer = stubConsumer(consumedMessagesRef)
        val stream   = new SqsProcessingStream[IO](client, config, ConsumerName("name"))

        testMessages.traverse(msg => sendMessage(client, msg.asJson.spaces2)) *>
          stream.startStream(consumer).compile.drain.timeout(5.seconds).attempt.void *>
          consumedMessagesRef.get

      }
    assertIO(messagesConsumed.map(_.length), testMessages.length)
    assertIO(messagesConsumed.map(_.toSet), testMessages.toSet)
  }

  test("The visibility timeout is updated for a long running job") {

    val messagesConsumed = clientResource(queueName)
      .flatMap(client => Resource.eval(Ref.of[IO, List[TestMessage]](List.empty)).map(_ -> client))
      .use { case (consumedMessagesRef, client) =>
        val consumer = stubConsumer(consumedMessagesRef, 10.seconds.some)
        val stream   = new SqsProcessingStream[IO](client, config, ConsumerName("name"))

        sendMessage(client, testMessage.asJson.spaces2) *>
          stream.startStream(consumer).compile.drain.timeout(12.seconds).attempt.void *>
          consumedMessagesRef.get

      }
    assertIO(messagesConsumed, List(testMessage))
  }

  test("Bulk processing test with random delays") {

    val testMessages = (1 to 30).toList.map(i => TestMessage(s"Hi - $i"))

    val messagesConsumed = clientResource(queueName)
      .flatMap(client => Resource.eval(Ref.of[IO, List[TestMessage]](List.empty)).map(_ -> client))
      .use { case (consumedMessagesRef, client) =>
        val randomDelayConsumer = new SqsConsumer[IO, TestMessage]:
          override def handle(msg: TestMessage): IO[Unit] =
            IO.sleep(Random.nextInt(1000).milliseconds) *> consumedMessagesRef.update(_ :+ msg)

        val stream = new SqsProcessingStream[IO](client, config, ConsumerName("name"))
        testMessages.parTraverse(msg => sendMessage(client, msg.asJson.spaces2)) *>
          stream.startStream(randomDelayConsumer).compile.drain.timeout(25.seconds).attempt.void *>
          consumedMessagesRef.get

      }
    assertIO(messagesConsumed.map(_.size), testMessages.size)
    assertIO(messagesConsumed.map(_.toSet), testMessages.toSet)
  }

  private def sendMessage(client: SqsAsyncClient, messageBody: String): IO[Unit] =
    IO.fromFuture(
      IO.delay(
        client
          .sendMessage(
            SendMessageRequest
              .builder()
              .queueUrl(config.queueUrl.value)
              .messageBody(messageBody)
              .build()
          )
          .asScala
      )
    ).void

  private def stubConsumer(consumedRef: Ref[IO, List[TestMessage]], processingDelay: Option[FiniteDuration] = None) =
    new SqsConsumer[IO, TestMessage]:
      override def handle(msg: TestMessage): IO[Unit] =
        processingDelay.fold(IO.unit)(IO.sleep) *> consumedRef.update(_ :+ msg)

  private def clientResource(queueName: String) =

    val dummyCreds = AwsBasicCredentials.create("dummy-access-key", "dummy-secret-key")

    Resource
      .fromAutoCloseable(
        IO(
          SqsAsyncClient
            .builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(dummyCreds))
            .endpointOverride(new URI("http://localhost:4566"))
            .build()
        )
      )
      .evalTap(client =>
        IO.fromFuture(
          IO.delay(client.deleteQueue(DeleteQueueRequest.builder().queueUrl(config.queueUrl.value).build()).asScala)
        ).attempt
          .void
      )
      .evalTap(client =>
        IO.fromFuture(IO.delay(client.createQueue(CreateQueueRequest.builder().queueName(queueName).build()).asScala))
      )
