package uk.co.thirdthing.secrets

import cats.effect.{IO, Resource}
import io.circe.Json
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import io.circe.parser._
import software.amazon.awssdk.http.apache.ApacheHttpClient

trait SecretsManager {
  def secretFor(keyName: String): IO[String]
  def jsonSecretFor(keyName: String): IO[Json]
}

object AmazonSecretsManager {
  def apply(secretsManagerClient: SecretsManagerClient) = new SecretsManager {
    override def secretFor(keyName: String): IO[String] = {
      val getSecretValueRequest = GetSecretValueRequest
        .builder()
        .secretId(keyName)
        .build()

      IO.blocking(secretsManagerClient.getSecretValue(getSecretValueRequest).secretString())
    }

    override def jsonSecretFor(keyName: String): IO[Json] = secretFor(keyName).flatMap(str => IO.fromEither(parse(str)))
  }

  def from(region: Region): Resource[IO, SecretsManager] =
    Resource.make(IO(ApacheHttpClient.builder.build))(c => IO(c.close())).map { httpClient =>
      apply(SecretsManagerClient.builder().region(region).httpClient(httpClient).build())

    }
}
