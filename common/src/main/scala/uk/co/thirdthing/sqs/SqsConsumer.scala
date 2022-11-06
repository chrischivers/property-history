package uk.co.thirdthing.sqs

import cats.effect.{Async, Sync}
import cats.syntax.all._
import io.circe
import io.circe.Decoder
import io.circe.parser._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.{
  ChangeMessageVisibilityRequest,
  DeleteMessageRequest,
  Message,
  QueueAttributeName,
  ReceiveMessageRequest
}

import scala.concurrent.duration.{FiniteDuration, _}
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.util.control.NonFatal

abstract class SqsConsumer[F[_], A: Decoder] {
  def handle(msg: A): F[Unit]
}

class SqsProcessingStream[F[_]: Async](sqsClient: SqsAsyncClient, sqsConfig: SqsConfig, consumerName: String) {

  private implicit val logger = Slf4jLogger.getLogger[F]

  private val request = ReceiveMessageRequest.builder
    .queueUrl(sqsConfig.queueUrl)
    .waitTimeSeconds(sqsConfig.waitTime.toSeconds.toInt)
    .visibilityTimeout(sqsConfig.visibilityTimeout.toSeconds.toInt)
    .attributeNames(List(QueueAttributeName.ALL).asJavaCollection)
    .messageAttributeNames(List("ALL").asJavaCollection)
    .maxNumberOfMessages(10)
    .build()

  def startStream[A: Decoder](consumer: SqsConsumer[F, A]): fs2.Stream[F, Unit] =
    fs2.Stream.eval(logger.info(s"Consumer $consumerName started")) >>
      messageStream
        .evalMap(msg => Sync[F].fromEither(decodeMessage(msg)).map(_ -> msg.receiptHandle()))
        .parEvalMap(sqsConfig.processingParallelism) {
          case (decodedMessage, receiptHandle) =>
            Sync[F].race(consumer.handle(decodedMessage), updateVisibilityTimeoutStream(receiptHandle)).void *>
              deleteMesage(receiptHandle).void
        }

  private def decodeMessage[A: Decoder](msg: Message): Either[circe.Error, A] =
    parse(msg.body()).flatMap(_.as[A])

  private def messageStream: fs2.Stream[F, Message] =
    fs2.Stream
      .repeatEval(poll)
      .evalTap(msgs => if (msgs.nonEmpty) logger.info(s"$consumerName Retrieved ${msgs.size} messages") else ().pure[F])
      .metered(sqsConfig.streamThrottlingRate)
      .flatMap(fs2.Stream.emits)

  private def updateVisibilityTimeoutStream(messageReceiptHandle: String) =
    fs2.Stream
      .awakeEvery(sqsConfig.heartbeatInterval)
      .evalMap(_ => updateVisibilityTimeout(messageReceiptHandle, sqsConfig.visibilityTimeout).attempt.void)
      .compile
      .drain

  private def deleteMesage(messageReceiptHandle: String) = {
    val request = DeleteMessageRequest
      .builder()
      .queueUrl(sqsConfig.queueUrl)
      .receiptHandle(messageReceiptHandle)
      .build()
    Async[F].fromFuture(Sync[F].delay(sqsClient.deleteMessage(request).asScala))
  }

  private def updateVisibilityTimeout(messageReceiptHandle: String, timeout: FiniteDuration) = {
    val request = ChangeMessageVisibilityRequest
      .builder()
      .queueUrl(sqsConfig.queueUrl)
      .receiptHandle(messageReceiptHandle)
      .visibilityTimeout(timeout.toSeconds.toInt)
      .build()
    Async[F].fromFuture(Sync[F].delay(sqsClient.changeMessageVisibility(request).asScala))
  }

  private def poll: F[List[Message]] =
    logger.debug(s"$consumerName polling for sqs messages") *>
      Async[F]
        .fromFuture(Sync[F].delay(sqsClient.receiveMessage(request).asScala))
        .map(_.messages().asScala.toList)
        .recoverWith {
          case NonFatal(t) =>
            logger.error(t)(s"$consumerName got error polling queue ${sqsConfig.queueUrl} - sleeping for ${sqsConfig.retrySleepTime}") *>
              Sync[F].sleep(sqsConfig.retrySleepTime).as(List.empty)
        }
}
