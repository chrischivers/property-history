package uk.co.thirdthing

import cats.effect.{IO, Ref}
import io.circe.Encoder
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import uk.co.thirdthing.sqs.SqsPublisher
import uk.co.thirdthing.sqs.SqsConfig.*

object MockSqsPublisher:

  private val fakeSqsClient = new SqsAsyncClient:
    override def serviceName(): String = ???
    override def close(): Unit         = ???

  def apply[A: Encoder](messagesRef: Ref[IO, List[A]]) = new SqsPublisher[IO, A](fakeSqsClient)(QueueUrl("blah")):
    override def publish(msg: A): IO[Unit] = messagesRef.update(_ :+ msg)
