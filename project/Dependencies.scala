import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._

object Dependencies {

  object Versions {
    val scala3                = "3.3.1-RC1-bin-20230218-7c9c72a-NIGHTLY"
    val http4s                = "0.23.18"
    val http4sBlaze                = "0.23.13"
    val munit                 = "0.7.29"
    val munitCatsEffect       = "1.0.7"
    val munitScalaCheckEffect = "1.0.4"
    val http4sMunit           = "0.15.0"
    val refined               = "0.10.1"
    val circe                 = "0.14.4"
    val typesafeLogging       = "3.9.5"
    val log4cats              = "2.4.0"
    val logbackClassic        = "1.4.5"
    val skunk                 = "0.5.1"
    val newtype               = "0.2.3"
    val enumeration           = "1.7.2"
    val aws                   = "2.20.8"
    val diffx                 = "0.8.2"
    val apacheCommonsLang     = "3.12.0"
    val laminar               = "0.14.2"
    val wayPoint              = "0.5.0"
    val scalaCss              = "1.0.0"
    val http4sDom             = "0.2.3"
    val smithy4s              = "0.17.1"
    val coursier              = "2.1.0-RC4"
  }

  private val munit = Seq(
    "org.scalameta"       %% "munit"                   % Versions.munit,
    "org.scalameta"       %% "munit-scalacheck"        % Versions.munit,
    "org.typelevel"       %% "munit-cats-effect-3"     % Versions.munitCatsEffect,
    "org.typelevel"       %% "scalacheck-effect-munit" % Versions.munitScalaCheckEffect,
    "eu.timepit"          %% "refined-scalacheck"      % Versions.refined,
    "com.alejandrohdezma" %% "http4s-munit"            % Versions.http4sMunit
  )

  private val http4s = Seq(
    "org.http4s" %% "http4s-dsl"          % Versions.http4s,
    "org.http4s" %% "http4s-circe"        % Versions.http4s,
    "org.http4s" %% "http4s-ember-server" % Versions.http4s,
    "org.http4s" %% "http4s-blaze-client" % Versions.http4sBlaze
  )

  private val smithy4s = Seq(
    "com.disneystreaming.smithy4s" %% "smithy4s-http4s"         % Versions.smithy4s,
    "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % Versions.smithy4s
  )

  private val refined = Seq(
    "eu.timepit" %% "refined"      % Versions.refined,
    "eu.timepit" %% "refined-cats" % Versions.refined
  )

  private val circe = Seq(
    "io.circe" %% "circe-refined" % Versions.circe,
    "io.circe" %% "circe-generic" % Versions.circe,
    "io.circe" %% "circe-parser"  % Versions.circe
  )

  private val logging = Seq(
    "com.typesafe.scala-logging" %% "scala-logging"   % Versions.typesafeLogging,
    "org.typelevel"              %% "log4cats-slf4j"  % Versions.log4cats,
    "ch.qos.logback"              % "logback-classic" % Versions.logbackClassic
  )

  private val skunk = Seq(
    "org.tpolecat" %% "skunk-core"  % Versions.skunk,
    "org.tpolecat" %% "skunk-circe" % Versions.skunk
  )

  private val newtype = Seq(
    "io.monix" %% "newtypes-core"        % Versions.newtype,
    "io.monix" %% "newtypes-circe-v0-14" % Versions.newtype
  )

  private val enumeration = Seq(
    "com.beachape" %% "enumeratum"       % Versions.enumeration,
    "com.beachape" %% "enumeratum-circe" % Versions.enumeration
  )
  private val awsSqs = Seq(
    "software.amazon.awssdk" % "sqs" % Versions.aws
  )

  private val awsSecretsManager = Seq(
    "software.amazon.awssdk" % "secretsmanager" % Versions.aws
  )

  private val awsCloudwatch = Seq(
    "software.amazon.awssdk" % "cloudwatch" % Versions.aws
  )

  private val diffx = Seq(
    "com.softwaremill.diffx" %% "diffx-cats" % Versions.diffx
  )

  private val apacheCommonsLang = Seq(
    "org.apache.commons" % "commons-lang3" % Versions.apacheCommonsLang
  )

  private val test = munit.map(_ % "test,it")

  val commonDependencies = enumeration ++ circe ++ newtype
  val backendCommonDependencies: Seq[ModuleID] =
    http4s ++ refined ++ circe ++ logging ++ skunk ++ newtype ++ awsSecretsManager ++ awsCloudwatch ++ apacheCommonsLang ++ enumeration ++ logging ++ test
  val publicApiDependencies: Seq[ModuleID] = http4s ++ refined ++ circe ++ logging ++ newtype ++ smithy4s ++ test
  val crawlerDependencies: Seq[ModuleID] =
    http4s ++ refined ++ circe ++ newtype ++ diffx ++ awsSqs ++ test
}
