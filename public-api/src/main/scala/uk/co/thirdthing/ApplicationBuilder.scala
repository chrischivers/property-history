package uk.co.thirdthing

import cats.effect.{IO, Resource}
import com.comcast.ip4s.{Host, Port}
import org.http4s.HttpApp
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.defaults.HttpPort
import skunk.Session
import uk.co.thirdthing.routes._
import uk.co.thirdthing.secrets.{AmazonSecretsManager, SecretsManager}
import uk.co.thirdthing.service.HistoryService
import natchez.Trace.Implicits.noop
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import uk.co.thirdthing.store.{PostgresPropertyStore, PropertyStore}
import smithy4s.http4s.SimpleRestJsonBuilder

object ApplicationBuilder:
  def build: Resource[IO, Unit] =
    for {
      secretsManager <- buildSecretsManager
      dbPool         <- databaseSessionPool(secretsManager)
      propertyStore = PostgresPropertyStore.apply[IO](dbPool)
      historyService <- Resource.pure[IO, HistoryService[IO]](HistoryService.apply[IO](propertyStore))
      httpApp <- router(historyService)
      _ <- serverResource(httpApp)
    } yield ()

  private def buildSecretsManager: Resource[IO, SecretsManager[IO]] =
    Resource.fromAutoCloseable[IO, SecretsManagerClient](IO(SecretsManagerClient.builder().build())).map(AmazonSecretsManager(_))

  private def databaseSessionPool(secretsManager: SecretsManager[IO]): Resource[IO, Resource[IO, Session[IO]]] = {
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
        max = 16
      )
    }

  }

  private def router(historyService: HistoryService[IO]): Resource[IO, HttpApp[IO]] =
    SimpleRestJsonBuilder.routes(ApiRouteSmithy(historyService)).resource.map { apiRoutesSmithy =>
      Router(
        "/api/v1" -> ApiRoute.routes[IO](historyService),
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
