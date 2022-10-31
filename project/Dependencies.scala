import sbt._

object Dependencies {

  object Versions {
    val http4s                = "0.23.13"
    val munit                 = "0.7.29"
    val munitCatsEffect       = "1.0.7"
    val munitScalaCheckEffect = "1.0.4"
    val http4sMunit           = "0.11.0"
    val refined               = "0.9.29"
    val circe                 = "0.14.1"
    val typesafeLogging       = "3.9.5"
    val log4cats              = "2.4.0"
    val logbackClassic        = "1.2.11"
    val meteor                = "1.0.18"
    val newtype               = "0.2.3"
    val enumeration           = "1.7.0"
    val awsSqs                = "2.18.5"
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
    "org.http4s" %% "http4s-ember-client" % Versions.http4s
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
    "com.typesafe.scala-logging" %% "scala-logging"  % Versions.typesafeLogging,
    "org.typelevel"              %% "log4cats-slf4j" % Versions.log4cats,
    "ch.qos.logback"             % "logback-classic" % Versions.logbackClassic
  )

  private val metor = Seq(
    "io.github.d2a4u" %% "meteor-awssdk" % Versions.meteor
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
    "software.amazon.awssdk" % "sqs" % Versions.awsSqs
  )
  private val test = munit.map(_ % "test,it")

  val commonDependencies: Seq[ModuleID]    = http4s ++ refined ++ circe ++ logging ++ metor ++ newtype ++ awsSqs ++ test
  val publicApiDependencies: Seq[ModuleID] = http4s ++ refined ++ circe ++ logging ++ metor ++ newtype ++ test
  val crawlerDependencies: Seq[ModuleID]   = http4s ++ refined ++ circe ++ logging ++ metor ++ newtype ++ enumeration ++ test
}
