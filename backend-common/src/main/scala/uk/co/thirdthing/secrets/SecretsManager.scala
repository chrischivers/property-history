package uk.co.thirdthing.secrets

import cats.effect.kernel.Sync
import cats.effect.{IO, Resource}
import io.circe.Json
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import io.circe.parser._
import software.amazon.awssdk.http.apache.ApacheHttpClient
import cats.syntax.all._

trait SecretsManager[F[_]] {
  def secretFor(keyName: String): F[String]
  def jsonSecretFor(keyName: String): F[Json]
}

object AmazonSecretsManager {
  def apply[F[_]: Sync](secretsManagerClient: SecretsManagerClient) = new SecretsManager[F] {
    override def secretFor(keyName: String): F[String] = {
      val getSecretValueRequest = GetSecretValueRequest
        .builder()
        .secretId(keyName)
        .build()

      Sync[F].blocking(secretsManagerClient.getSecretValue(getSecretValueRequest).secretString())
    }

    override def jsonSecretFor(keyName: String): F[Json] = secretFor(keyName).flatMap(str => Sync[F].fromEither(parse(str)))
  }
}
