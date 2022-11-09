package uk.co.thirdthing

import cats.effect.{IO, Resource}
import org.http4s.HttpApp
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.implicits._
import org.http4s.server.Router
import uk.co.thirdthing.routes.ApiRoute
import uk.co.thirdthing.service.HistoryService
import uk.co.thirdthing.store.{DynamoPropertyIdStore, DynamoPropertyListingStore}

object ApplicationBuilder {
  def build: Resource[IO, Unit] =
    for {
      dynamoClient         <- DynamoClient.resource[IO]
      propertyIdStore      = DynamoPropertyIdStore[IO](dynamoClient)
      propertyListingStore = DynamoPropertyListingStore[IO](dynamoClient)
      historyService       <- Resource.pure[IO, HistoryService[IO]](HistoryService.apply[IO](propertyIdStore, propertyListingStore))
      httpApp              = router(historyService)
      _                    <- serverResource(httpApp)
    } yield ()

  def router(historyService: HistoryService[IO]) = Router("/api" -> ApiRoute.routes[IO](historyService)).orNotFound

  def serverResource(httpApp: HttpApp[IO]) =
    BlazeServerBuilder.apply[IO]
      .withHttpApp(httpApp)
      .resource
}
