package scripts

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.syntax.all._
import meteor.api.hi.CompositeTable
import meteor.codec.{Decoder, Encoder}
import meteor.errors.DecoderError
import meteor.syntax._
import meteor.{Client, DynamoDbType, KeyDef}
import natchez.Trace.Implicits.noop
import scripts.BackfillFromDynamo.DynamoItem
import skunk.{Session, SqlState}
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.secrets.{AmazonSecretsManager, SecretsManager}
import uk.co.thirdthing.store.{PostgresPropertyStore, PropertyStore}

import java.time.Instant
import scala.concurrent.duration._

object BackfillFromDynamo extends IOApp {

  import Codecs._

  private[scripts] case class DynamoItem(
    listingId: ListingId,
    lastChange: LastChange,
    dateAdded: DateAdded,
    details: PropertyDetails,
    propertyId: PropertyId
  )

  override def run(args: List[String]): IO[ExitCode] =
    buildSecretsManager.use { secretsManager =>
      (dynamoDbClient, databaseSessionPool(secretsManager)).tupled.use { case (dynamo, pool) =>
        val propertyStore = PostgresPropertyStore[IO](pool)
        val dynamoClient  = Client[IO](dynamo)
        val listingHistoryDynamoTable = CompositeTable[IO, ListingId, LastChange](
          "listing-history",
          KeyDef("listingId", DynamoDbType.N),
          KeyDef("lastChange", DynamoDbType.N),
          dynamo
        )

        dynamoClient
          .scan[DynamoItem]("listing-history", consistentRead = false, 10)
          .zipWithIndex
          .evalMap {
            case (item, idx) if idx % 10000 == 0 => IO.println(s"Processing item $idx").as(item)
            case (item, _) => item.pure[IO]
          }
//            .evalTap(item => IO.println(s"Processing item $item"))
          .map(withValidTimestamp)
          .parEvalMap(10)(item => putPostgresListingSnapshot(propertyStore)(item).as(item))
          .map(item => (item.listingId, item.lastChange))
          .broadcastThrough(listingHistoryDynamoTable.batchDelete(60.seconds, 10, BackoffStrategy.defaultStrategy()))
          .compile
          .drain
          .as(ExitCode.Success)

      }
    }

  private def withValidTimestamp(item: DynamoItem) =
    if (item.dateAdded.value.toEpochMilli < 0) item.copy(dateAdded = DateAdded(Instant.ofEpochMilli(0L)))
    else item

  private def putPostgresListingSnapshot(propertyStore: PropertyStore[IO])(item: DynamoItem) =
    propertyStore
      .putListingSnapshot(ListingSnapshot(item.listingId, item.lastChange, item.propertyId, item.dateAdded, item.details, None))
      .recoverWith { case SqlState.UniqueViolation(ex) => IO.println(s"Exception for ${item.listingId.value}. $ex") }

  private def dynamoDbClient =
    Resource.fromAutoCloseable(IO(DynamoDbAsyncClient.builder().build()))

  private def buildSecretsManager: Resource[IO, SecretsManager] =
    Resource
      .fromAutoCloseable[IO, SecretsManagerClient](
        IO(SecretsManagerClient.builder().build())
      )
      .map(AmazonSecretsManager(_))

  private def databaseSessionPool(secretsManager: SecretsManager): Resource[IO, Resource[IO, Session[IO]]] = {
    val secrets = for {
      host     <- secretsManager.secretFor("postgres-host")
      username <- secretsManager.secretFor("postgres-user")
      password <- secretsManager.secretFor("postgres-password")
    } yield (host, username, password)

    Resource.eval(secrets).flatMap { case (host, username, password) =>
      Session.pooled[IO](
        host = host,
        port = 5432,
        user = username,
        database = "propertyhistory",
        password = Some(password),
        max = 10
      )
    }
  }

}

object Codecs {
  implicit val listingIdEncoder: Encoder[ListingId]   = Encoder.instance(_.value.asAttributeValue)
  implicit val lastChangeEncoder: Encoder[LastChange] = Encoder.instance(_.value.asAttributeValue)

  implicit val propertyDetailsDecoder: Decoder[PropertyDetails] = Decoder.instance { av =>
    for {
      price <- av.getAs[Int]("price").map(Price(_))
      transactionTypeId <- av
        .getAs[Int]("transactionTypeId")
        .flatMap(id => TransactionType.withValueEither(id).leftMap(err => DecoderError(s"cannot map $id to transaction type", err.some)))
      visible <- av.getAs[Boolean]("visible")
      status <- av
        .getAs[String]("status")
        .flatMap(status =>
          ListingStatus
            .withValueEither(status)
            .leftMap(err => DecoderError(s"cannot map $status to listing status type"))
        )
      rentFrequency <- av.getOpt[String]("rentFrequency")
      latitude      <- av.getOpt[Double]("latitude")
      longitude     <- av.getOpt[Double]("longitude")
    } yield PropertyDetails(price.some, transactionTypeId.some, visible.some, status.some, rentFrequency, latitude, longitude)
  }

  implicit val dynamoItemDecoder: Decoder[DynamoItem] = Decoder.instance { item =>
    for {
      listingId  <- item.getAs[Long]("listingId").map(ListingId(_))
      lastChange <- item.getAs[Instant]("lastChange").map(LastChange(_))
      propertyId <- item.getAs[Long]("propertyId").map(PropertyId(_))
      dateAdded  <- item.getAs[Instant]("dateAdded").map(DateAdded(_))
      details    <- item.getOpt[PropertyDetails]("details")
    } yield DynamoItem(listingId, lastChange, dateAdded, details.getOrElse(PropertyDetails.Empty), propertyId)
  }
}
