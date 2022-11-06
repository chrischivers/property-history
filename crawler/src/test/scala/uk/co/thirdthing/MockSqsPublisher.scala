package uk.co.thirdthing

import cats.effect.{IO, Ref}
import io.circe.Encoder
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.co.thirdthing.sqs.SqsPublisher

object MockSqsPublisher {

  private val fakeSqsClient = new SqsAsyncClient {
    override def serviceName(): String = ???
    override def close(): Unit = ???
  }

  def apply[A: Encoder](messagesRef: Ref[IO, List[A]]) = new SqsPublisher[IO, A](fakeSqsClient)("blah") {
    override def publish(msg: A): IO[Unit] = messagesRef.update(_ :+ msg)
  }
}
