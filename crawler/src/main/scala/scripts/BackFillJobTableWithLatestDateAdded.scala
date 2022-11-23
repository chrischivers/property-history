package scripts

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.syntax.all._
import natchez.Trace.Implicits.noop
import skunk.Session
import skunk.codec.all._
import skunk.implicits._
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import uk.co.thirdthing.model.Model.JobState
import uk.co.thirdthing.model.Types._
import uk.co.thirdthing.secrets.{AmazonSecretsManager, SecretsManager}
import uk.co.thirdthing.store.DynamoJobStore
import uk.co.thirdthing.utils.TimeUtils.LocalDateTimeOps

object BackFillJobTableWithLatestDateAdded extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    buildSecretsManager.use { secretsManager =>
      (dynamoDbClient, databaseSessionPool(secretsManager)).tupled.use {
        case (dynamo, pool) =>
          val dynamoJobStore = DynamoJobStore[IO](dynamo)

          dynamoJobStore.getStream
            .filter(_.latestDateAdded.isEmpty)
            .filter(_.state == JobState.Completed)
            .zipWithIndex
            .evalMap {
              case (job, idx) if idx % 100 == 0 => IO.println(s"Processing job $idx").as(job)
              case (job, _) => job.pure[IO]
            }
            .evalMap(job => getLatestDateAddedBetweenListings(pool)(job.from, job.to).map(job -> _))
            .collect { case (job, Some(date)) => job.copy(latestDateAdded = date.some) }
            .evalMap(dynamoJobStore.put)
            .compile
            .drain
            .as(ExitCode.Success)

      }
    }

  private val selectMaxDateAddedQuery =
    sql"""
      SELECT MAX(dateadded)
      FROM properties
      WHERE listingid >= $int8 AND listingid <= $int8""".query(timestamp)

  private def getLatestDateAddedBetweenListings(pool: Resource[IO, Session[IO]])(from: ListingId, to: ListingId) =
    pool
      .use(session => session.prepare(selectMaxDateAddedQuery).use(query => query.option(from.value, to.value)))
      .map(_.map(r => DateAdded(r.asInstant)))

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

    Resource.eval(secrets).flatMap {
      case (host, username, password) =>
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
