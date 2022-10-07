package uk.co.thirdthing.model

import enumeratum.values.{IntCirceEnum, IntEnum, IntEnumEntry, StringCirceEnum, StringEnum, StringEnumEntry}
import io.circe.Decoder

object Model {

  sealed abstract class TransactionType(val value: Int) extends IntEnumEntry

  object TransactionType extends IntEnum[TransactionType] with IntCirceEnum[TransactionType] {
    case object Sale extends TransactionType(1)

    case object Rental extends TransactionType(2)

    override def values: IndexedSeq[TransactionType] = findValues
  }

  sealed abstract class ListingStatus(val value: String) extends StringEnumEntry

  object ListingStatus extends StringEnum[ListingStatus] with StringCirceEnum[ListingStatus] {
    case object SoldSTC extends ListingStatus("Sold STC")

    case object SoldSTCM extends ListingStatus("Sold STCM")

    case object LetAgreed extends ListingStatus("Under Offer")

    case object UnderOffer extends ListingStatus("Let Agreed")

    case object Reserved extends ListingStatus("Reserved")

    case object Other extends ListingStatus("Other")

    override def values: IndexedSeq[ListingStatus] = findValues

    override implicit val circeDecoder: Decoder[ListingStatus] = Decoder.instance { cursor =>
      cursor.as[String].map(c => valuesToEntriesMap.getOrElse(c, Other))
    }
  }

}
