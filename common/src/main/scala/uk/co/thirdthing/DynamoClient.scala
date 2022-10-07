package uk.co.thirdthing

import cats.effect.{Resource, Sync}
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

object DynamoClient {

  def resource[F[_]: Sync]: Resource[F, DynamoDbAsyncClient] =
    Resource.fromAutoCloseable[F, DynamoDbAsyncClient] {
      Sync[F].delay {
        val cred = DefaultCredentialsProvider.create()
        DynamoDbAsyncClient
          .builder()
          .credentialsProvider(cred)
          .region(Region.EU_WEST_1)
          .build()
      }
    }

}
