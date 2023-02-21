package uk.co.thirdthing.sqs

import cats.effect.{Async, Sync}
import io.circe.{Decoder, Encoder}
import io.circe.syntax.*
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

import scala.jdk.FutureConverters.*
import cats.syntax.all.*
import uk.co.thirdthing.sqs.SqsConfig.QueueUrl

class SqsPublisher[F[_]: Async, A: Encoder](client: SqsAsyncClient)(queueUrl: QueueUrl):
  def publish(msg: A): F[Unit] =
    Async[F]
      .fromFuture(
        Sync[F].delay(
          client
            .sendMessage(SendMessageRequest.builder().queueUrl(queueUrl.value).messageBody(msg.asJson.noSpaces).build())
            .asScala
        )
      )
      .void
