package uk.co.thirdthing.sqs

import cats.effect.{Async, Sync}
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import scala.jdk.FutureConverters._
import cats.syntax.all._

class SqsPublisher[F[_]: Async, A: Encoder](client: SqsAsyncClient)(queueUrl: String) {
  def publish(msg: A): F[Unit] =
    Async[F]
      .fromFuture(
        Sync[F].delay(client.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(msg.asJson.noSpaces).build()).asScala)
      )
      .void
}
