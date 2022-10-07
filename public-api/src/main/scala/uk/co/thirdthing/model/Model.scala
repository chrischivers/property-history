package uk.co.thirdthing.model
import io.circe.generic.semiauto._
import io.circe.{Decoder, Encoder}
import uk.co.thirdthing.Rightmove.{DateAdded, ListingId}

object Model {


  final case class RightmoveListing(id: ListingId, url: String, dateAdded: DateAdded)

  object RightmoveListing {
    implicit val encoder: Encoder[RightmoveListing] = deriveEncoder
    implicit val decoder: Decoder[RightmoveListing] = deriveDecoder
  }

}