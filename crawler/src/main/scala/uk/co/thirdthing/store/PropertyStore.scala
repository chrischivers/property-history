package uk.co.thirdthing.store

import cats.effect.Async
import cats.implicits.{catsSyntaxOptionId, toBifunctorOps}
import io.circe.syntax.EncoderOps
import meteor.api.hi._
import meteor.codec.Codec.dynamoCodecFromEncoderAndDecoder
import meteor.codec._
import meteor.errors.DecoderError
import meteor.syntax._
import meteor.{DynamoDbType, KeyDef}
import org.http4s.Uri
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId, Price, PropertyId}
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import uk.co.thirdthing.model.Model.{ListingStatus, TransactionType}
import uk.co.thirdthing.store.DynamoPropertyStore.Property

import java.time.Instant

trait PropertyStore[F[_]] {
  def store(property: Property): F[Unit]
}

object DynamoPropertyStore {

  implicit private val decoder: Decoder[PropertyId] = Decoder.instance(_.getAs[Long]("propertyId").map(PropertyId.apply))

  case class Property(listingId: ListingId, propertyId: PropertyId, dateAdded: DateAdded, details: PropertyDetails)

  object Property {
    implicit val encoder: Encoder[Property] = Encoder.instance { property =>
      Map(
        "listingId"  -> property.listingId.value.asAttributeValue,
        "propertyId" -> property.propertyId.value.asAttributeValue,
        "dateAdded"  -> property.dateAdded.value.asAttributeValue,
        "details"    -> property.details.asAttributeValue
      ).asAttributeValue
    }

    implicit val decoder: Decoder[Property] = Decoder.instance { av =>
      for {
        listingId  <- av.getAs[Long]("listingId").map(ListingId(_))
        propertyId <- av.getAs[Long]("propertyId").map(PropertyId(_))
        dateAdded  <- av.getAs[Instant]("dateAdded").map(DateAdded(_))
        details    <- av.getAs[PropertyDetails]("details")
      } yield Property(listingId, propertyId, dateAdded, details)
    }
  }

  case class PropertyDetails(
    price: Price,
    transactionTypeId: TransactionType,
    visible: Boolean,
    status: Option[ListingStatus],
    rentFrequency: Option[String],
    latitude: Double,
    longitude: Double
  )

  object PropertyDetails {
    implicit val encoder: Encoder[PropertyDetails] = Encoder.instance { details =>
      Map(
        "price"             -> details.price.value.asAttributeValue,
        "transactionTypeId" -> details.transactionTypeId.value.asAttributeValue,
        "visible"           -> details.visible.asAttributeValue,
        "status"            -> details.status.map(_.value).asAttributeValue,
        "rentFrequency"     -> details.rentFrequency.asAttributeValue,
        "latitude"          -> details.latitude.asAttributeValue,
        "longitude"         -> details.longitude.asAttributeValue
      ).asAttributeValue
    }

    implicit val decoder: Decoder[PropertyDetails] = Decoder.instance { av =>
      for {
        price <- av.getAs[Int]("price").map(Price(_))
        transactionTypeId <- av
                              .getAs[Int]("transactionTypeId")
                              .flatMap(id =>
                                TransactionType.withValueEither(id).leftMap(err => DecoderError(s"cannot map $id to transaction type", err.some))
                              )
        visible <- av.getAs[Boolean]("visible")
        status <- av
                   .getOpt[String]("status")
                   .flatMap(idOpt =>
                     idOpt.fold[Either[DecoderError, Option[ListingStatus]]](Right(None))(id =>
                       ListingStatus
                         .withValueEither(id)
                         .bimap(err => DecoderError(s"cannot map $id to transaction type", err.some), _.some)
                     )
                   )
        rentFrequency <- av.getOpt[String]("rentFrequency")
        latitude      <- av.getAs[Double]("latitude")
        longitude     <- av.getAs[Double]("longitude")
      } yield PropertyDetails(price, transactionTypeId, visible, status, rentFrequency, latitude, longitude)
    }
  }

  def apply[F[_]: Async](client: DynamoDbAsyncClient): PropertyStore[F] = {
    implicit val listingIdEncoder: Encoder[ListingId] = Encoder.instance(_.value.asAttributeValue)
    val table                                         = SimpleTable[F, ListingId]("properties", KeyDef[ListingId]("listingId", DynamoDbType.N), client)
    new PropertyStore[F] {
      override def store(property: Property): F[Unit] =
        table.put[Property](property)

    }
  }
}
