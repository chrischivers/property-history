package uk.co.thirdthing

import cats.effect.{IO, Resource}
import org.http4s.HttpApp
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.defaults.HttpPort
import uk.co.thirdthing.routes.{ApiRoute, MetaRoute}
import uk.co.thirdthing.service.HistoryService
import uk.co.thirdthing.store.DynamoPropertyStore

object ApplicationBuilder {
  def build: Resource[IO, Unit] =
    for {
      dynamoClient         <- DynamoClient.resource[IO]
      propertyStore      = DynamoPropertyStore[IO](dynamoClient)
      historyService       <- Resource.pure[IO, HistoryService[IO]](HistoryService.apply[IO](propertyStore))
      httpApp              = router(historyService)
      _                    <- serverResource(httpApp)
    } yield ()

  def router(historyService: HistoryService[IO]) =
    Router(
      "/api"  -> ApiRoute.routes[IO](historyService),
      "/meta" -> MetaRoute.routes[IO]
    ).orNotFound

  def serverResource(httpApp: HttpApp[IO]) =
    BlazeServerBuilder
      .apply[IO]
      .withHttpApp(httpApp)
      .bindHttp(HttpPort, "0.0.0.0")
      .resource
}
