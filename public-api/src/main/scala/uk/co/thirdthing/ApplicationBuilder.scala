package uk.co.thirdthing

import cats.effect.{IO, Resource}
import org.http4s.HttpApp
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.defaults.HttpPort
import skunk.Session
import uk.co.thirdthing.routes.{ApiRoute, MetaRoute, StaticRoutes}
import uk.co.thirdthing.secrets.{AmazonSecretsManager, SecretsManager}
import uk.co.thirdthing.service.HistoryService
import natchez.Trace.Implicits.noop
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import uk.co.thirdthing.store.{PostgresPropertyStore, PropertyStore}

object ApplicationBuilder:
  def build: Resource[IO, Unit] =
    for {
      secretsManager <- buildSecretsManager
      dbPool         <- databaseSessionPool(secretsManager)
      propertyStore  = PostgresPropertyStore.apply[IO](dbPool)
      historyService <- Resource.pure[IO, HistoryService[IO]](HistoryService.apply[IO](propertyStore))
      httpApp        = router(historyService)
      _              <- serverResource(httpApp)
    } yield ()

  private def buildSecretsManager: Resource[IO, SecretsManager] =
    Resource.fromAutoCloseable[IO, SecretsManagerClient](IO(SecretsManagerClient.builder().build())).map(AmazonSecretsManager(_))

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
          max = 16
        )
    }

  }

  def router(historyService: HistoryService[IO]) =
    Router(
      "/api"  -> ApiRoute.routes[IO](historyService),
      "/meta" -> MetaRoute.routes[IO],
      "/"     -> StaticRoutes.routes[IO]
    ).orNotFound

  def serverResource(httpApp: HttpApp[IO]) =
    BlazeServerBuilder
      .apply[IO]
      .withHttpApp(httpApp)
      .bindHttp(HttpPort, "0.0.0.0")
      .resource
