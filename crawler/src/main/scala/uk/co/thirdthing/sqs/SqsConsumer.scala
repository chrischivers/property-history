package uk.co.thirdthing.sqs

import cats.Parallel
import cats.effect.{Async, Sync}
import cats.syntax.all.*
import io.circe
import io.circe.Decoder
import io.circe.parser.*
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{
  ReceiveMessageRequest => SqsReceiveMessageRequest,
  Message,
  DeleteMessageRequest,
  ChangeMessageVisibilityRequest,
  QueueAttributeName
}

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters.*
import scala.jdk.FutureConverters.*
import scala.util.control.NonFatal
import monix.newtypes.NewtypeWrapped
import uk.co.thirdthing.sqs.SqsConsumer.ConsumerName

abstract class SqsConsumer[F[_], A: Decoder]:
  def handle(msg: A): F[Unit]

object SqsConsumer:
  type ConsumerName = ConsumerName.Type
  object ConsumerName extends NewtypeWrapped[String]

class SqsProcessingStream[F[_]: Async: Parallel](
  client: SqsAsyncClient,
  sqsConfig: SqsConfig,
  consumerName: ConsumerName
):

  private implicit val logger: SelfAwareStructuredLogger[F] = Slf4jLogger.getLogger[F]

  def startStream[A: Decoder](consumer: SqsConsumer[F, A]): fs2.Stream[F, Unit] =
    fs2.Stream.eval(logger.info(s"Consumer ${consumerName.value} started")) *>
      messageStream
        .evalMap(msg => Sync[F].fromEither(decodeMessage(msg)).map(_ -> msg.receiptHandle()))
        .chunkN(sqsConfig.parallelism.value, allowFewer = true)
        .evalMap { chunks =>
          chunks.parTraverse { case (decodedMessage, receiptHandle) =>
            handleThenDelete(consumer, decodedMessage, receiptHandle).recoverWith { case err =>
              logger.error(err)(s"Error processing message with handle $receiptHandle")
            }
          }
        }
        .unchunks

  private def handleThenDelete[A](consumer: SqsConsumer[F, A], decodedMessage: A, receiptHandle: String) =
    Sync[F].race(consumer.handle(decodedMessage), updateVisibilityTimeoutStream(receiptHandle)).void *>
      deleteMesage(receiptHandle).void

  private def decodeMessage[A: Decoder](msg: Message): Either[circe.Error, A] =
    parse(msg.body()).flatMap(_.as[A])

  private def messageStream: fs2.Stream[F, Message] =
    fs2.Stream
      .repeatEval(poll)
      .meteredStartImmediately(sqsConfig.streamThrottlingRate.value)
      .evalTap(msgs =>
        if msgs.nonEmpty then logger.info(s"${consumerName.value} retrieved ${msgs.size} messages") else ().pure[F]
      )
      .flatMap(fs2.Stream.emits)

  private def updateVisibilityTimeoutStream(messageReceiptHandle: String) =
    fs2.Stream
      .awakeEvery(sqsConfig.heartbeatInterval.value)
      .evalMap(_ =>
        updateVisibilityTimeout(messageReceiptHandle, sqsConfig.visibilityTimeout.value).void.recoverWith { case err =>
          logger.error(err)("Error updating visibility timeout")
        }
      )
      .compile
      .drain

  private def deleteMesage(messageReceiptHandle: String) =
    val request = DeleteMessageRequest
      .builder()
      .queueUrl(sqsConfig.queueUrl.value)
      .receiptHandle(messageReceiptHandle)
      .build()
    Async[F].fromFuture(Sync[F].delay(client.deleteMessage(request).asScala))

  private def updateVisibilityTimeout(messageReceiptHandle: String, timeout: FiniteDuration) =
    val request = ChangeMessageVisibilityRequest
      .builder()
      .queueUrl(sqsConfig.queueUrl.value)
      .receiptHandle(messageReceiptHandle)
      .visibilityTimeout(timeout.toSeconds.toInt)
      .build()
    Async[F].fromFuture(Sync[F].delay(client.changeMessageVisibility(request).asScala))

  private val request = SqsReceiveMessageRequest.builder
    .queueUrl(sqsConfig.queueUrl.value)
    .waitTimeSeconds(sqsConfig.waitTime.value.toSeconds.toInt)
    .visibilityTimeout(sqsConfig.visibilityTimeout.value.toSeconds.toInt)
    .attributeNames(List(QueueAttributeName.ALL).asJavaCollection)
    .messageAttributeNames(List("ALL").asJavaCollection)
    .maxNumberOfMessages(sqsConfig.parallelism.value)
    .build()

  private def poll: F[List[Message]] =
    logger.debug(s"$consumerName polling for sqs messages") *>
      Async[F]
        .fromFuture(Sync[F].delay(client.receiveMessage(request).asScala))
        .map(_.messages().asScala.toList)
        .recoverWith { case NonFatal(t) =>
          logger.error(t)(
            s"$consumerName got error polling queue ${sqsConfig.queueUrl.value} - sleeping for ${sqsConfig.retrySleepTime.value}"
          ) *>
            Sync[F].sleep(sqsConfig.retrySleepTime.value).as(List.empty)
        }
