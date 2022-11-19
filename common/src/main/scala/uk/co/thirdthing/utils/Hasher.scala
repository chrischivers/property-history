package uk.co.thirdthing.utils

import cats.effect.Sync
import cats.syntax.all._
import io.circe.Encoder
import monix.newtypes.NewtypeWrapped
import monix.newtypes.integrations.DerivedCirceCodec
import io.circe.syntax._

import java.math.BigInteger
import java.security.MessageDigest

object Hasher {

  type Hash = Hash.Type
  object Hash extends NewtypeWrapped[String] with DerivedCirceCodec

  def hash[F[_]: Sync, A: Encoder](a: A): F[Hash] =
    Sync[F]
      .blocking {
        val s      = a.asJson.noSpacesSortKeys
        val md     = MessageDigest.getInstance("MD5")
        val digest = md.digest(s.getBytes)
        val bigInt = new BigInteger(1, digest)
        bigInt.toString(16)
      }
      .map(Hash(_))

}
