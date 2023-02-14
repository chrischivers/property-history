package uk.co.thirdthing

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import com.comcast.ip4s.{Host, Port}
import org.http4s.{HttpApp, Uri}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.defaults.HttpPort
import skunk.Session
import uk.co.thirdthing.routes.*
import uk.co.thirdthing.secrets.{AmazonSecretsManager, SecretsManager}
import uk.co.thirdthing.service.{HistoryService, ThumbnailService}
import natchez.Trace.Implicits.noop
import org.http4s.blaze.client.BlazeClientBuilder
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import uk.co.thirdthing.store.{PostgresInitializer, PostgresPropertyStore, PropertyStore}
import uk.co.thirdthing.clients.RightmoveApiClient
import smithy4s.http4s.SimpleRestJsonBuilder

import scala.concurrent.duration.*

object ApplicationBuilder:
  def build: Resource[IO, Unit] =
    for {
      secretsManager <- buildSecretsManager
      dbPool         <- databaseSessionPool(secretsManager)
      _              <- Resource.eval(PostgresInitializer.createPropertiesTableIfNotExisting[IO](dbPool))
      propertyStore = PostgresPropertyStore.apply[IO](dbPool)
      httpClient <- BlazeClientBuilder[IO].withMaxTotalConnections(30).withRequestTimeout(20.seconds).withMaxWaitQueueLimit(1500).resource
      rightmoveApiClient = RightmoveApiClient.apply[IO](httpClient, Uri.unsafeFromString("https://api.rightmove.co.uk"))
      historyService   <- Resource.pure[IO, HistoryService[IO]](HistoryService.apply[IO](propertyStore))
      thumbnailService <- Resource.pure[IO, ThumbnailService[IO]](ThumbnailService.apply[IO](rightmoveApiClient, httpClient))
      httpApp          <- router(historyService, thumbnailService)
      _                <- serverResource(httpApp)
    } yield ()

  private def buildSecretsManager: Resource[IO, SecretsManager[IO]] =
    Resource.fromAutoCloseable[IO, SecretsManagerClient](IO(SecretsManagerClient.builder().build())).map(AmazonSecretsManager(_))

  private def envOrSecretsManager(key: String, secretsManager: SecretsManager[IO]) =
    val envKey = key.toUpperCase.replace("-", "_")
    IO.delay(sys.env.get(envKey)).flatMap {
      case None    => secretsManager.secretFor(key)
      case Some(v) => v.pure[IO]
    }

  private def databaseSessionPool(secretsManager: SecretsManager[IO]): Resource[IO, Resource[IO, Session[IO]]] = {
    val secrets = for {
      host     <- envOrSecretsManager("postgres-host", secretsManager)
      username <- envOrSecretsManager("postgres-user", secretsManager)
      password <- envOrSecretsManager("postgres-password", secretsManager)
    } yield (host, username, password)

    Resource.eval(secrets).flatMap { case (host, username, password) =>
      Session.pooled[IO](
        host = host,
        port = 5432,
        user = username,
        database = "propertyhistory",
        password = Some(password),
        max = 16
      )
    }
  }

  private def router(historyService: HistoryService[IO], thumbnailService: ThumbnailService[IO]): Resource[IO, HttpApp[IO]] =
    SimpleRestJsonBuilder.routes(ApiRouteSmithy(historyService)).resource.map { apiRoutesSmithy =>
      Router(
        "/api/v1" -> ApiRoute.routes[IO](historyService, thumbnailService),
        "/api/v2" -> apiRoutesSmithy,
        "/meta"   -> MetaRoute.routes[IO],
        "/"       -> StaticRoutes.routes[IO]
      ).orNotFound
    }

  private def serverResource(httpApp: HttpApp[IO]) =
    EmberServerBuilder
      .default[IO]
      .withHttpApp(httpApp)
      .withPort(Port.fromInt(8080).get)
      .withHost(Host.fromString("0.0.0.0").get)
      .build
