import sbt.librarymanagement.Configurations.{IntegrationTest, Test}
import sbt.file
import Dependencies._

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val common = projectMatrix
  .in(file("common"))
  .jsPlatform(Seq(Versions.scala2))
  .jvmPlatform(Seq(Versions.scala2, Versions.scala3))
  .settings(CompilerSettings.settings)
  .settings(
    scalacOptions := CompilerSettings.scalacOptionsVersion(scalaVersion.value)
  )
  .settings(libraryDependencies ++= commonDependencies)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings))
  .settings(Defaults.itSettings)
  .settings(IntegrationTest / internalDependencyClasspath += Attributed.blank((Test / classDirectory).value))
  .settings(IntegrationTest / parallelExecution := false)

lazy val backendCommon = projectMatrix
  .in(file("backend-common"))
  .jvmPlatform(Seq(Versions.scala2, Versions.scala3))
  .settings(CompilerSettings.settings)
  .settings(libraryDependencies ++= backendCommonDependencies)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings))
  .settings(Defaults.itSettings)
  .settings(IntegrationTest / internalDependencyClasspath += Attributed.blank((Test / classDirectory).value))
  .settings(IntegrationTest / parallelExecution := false)
  .dependsOn(common % "test->test;it->test;it->it;compile->compile")
  .aggregate(common)

lazy val publicApi = projectMatrix
  .in(file("public-api"))
  .jvmPlatform(Seq(Versions.scala3))
  .settings(CompilerSettings.settings)
  .settings(libraryDependencies ++= publicApiDependencies)
  .enablePlugins(Smithy4sCodegenPlugin)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings))
  .settings(Defaults.itSettings)
  .settings(IntegrationTest / internalDependencyClasspath += Attributed.blank((Test / classDirectory).value))
  .settings(IntegrationTest / parallelExecution := false)
  .settings(run / fork := true)
  .settings(
    Compile / resourceGenerators += {
      Def.task[Seq[File]] {
        val _        = (frontend.js(Versions.scala2) / Compile / fastLinkJS).value
        val location = (frontend.js(Versions.scala2) / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value

        val outDir = (Compile / resourceManaged).value / "assets"
        IO.listFiles(location).toList.map { file =>
          val (name, ext) = file.baseAndExt
          val out         = outDir / (name + "." + ext)
          IO.copyFile(file, out)
          out
        }
      }
    },
    run / baseDirectory     := (ThisBuild / baseDirectory).value,
    reStart / baseDirectory := (ThisBuild / baseDirectory).value
  )
  .dependsOn(backendCommon % "test->test;it->test;it->it;compile->compile")
  .aggregate(backendCommon)
  .enablePlugins(UniversalPlugin, JavaServerAppPackaging, SystemdPlugin)

lazy val crawler = projectMatrix
  .in(file("crawler"))
  .jvmPlatform(Seq(Versions.scala2))
  .settings(CompilerSettings.settings)
  .settings(libraryDependencies ++= crawlerDependencies)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings))
  .settings(Defaults.itSettings)
  .settings(IntegrationTest / internalDependencyClasspath += Attributed.blank((Test / classDirectory).value))
  .settings(IntegrationTest / parallelExecution := false)
  .settings(Compile / mainClass := Some("uk.co.thirdthing.Main"))
  .dependsOn(backendCommon % "test->test;it->test;it->it;compile->compile")
  .aggregate(backendCommon)
  .enablePlugins(UniversalPlugin, JavaServerAppPackaging, SystemdPlugin)

lazy val frontend =
  projectMatrix
    .in(file("frontend"))
    .enablePlugins(ScalaJSPlugin)
    .jsPlatform(Seq(Versions.scala2))
    .settings(Seq(scalaVersion := Versions.scala2))
    .settings(
      scalaJSUseMainModuleInitializer := true,
      libraryDependencies ++= Seq(
        "com.raquo"                    %%% "laminar"              % Versions.laminar,
        "com.raquo"                    %%% "waypoint"             % Versions.wayPoint,
        "org.http4s"                   %%% "http4s-dom"           % Versions.http4sDom,
        "org.http4s"                   %%% "http4s-circe"         % Versions.http4s,
        "com.github.japgolly.scalacss" %%% "core"                 % Versions.scalaCss,
        "com.beachape"                 %%% "enumeratum"           % Versions.enumeration,
        "com.beachape"                 %%% "enumeratum-circe"     % Versions.enumeration,
        "io.circe"                     %%% "circe-refined"        % Versions.circe,
        "io.circe"                     %%% "circe-generic"        % Versions.circe,
        "io.circe"                     %%% "circe-parser"         % Versions.circe,
        "io.monix"                     %%% "newtypes-core"        % Versions.newtype,
        "io.monix"                     %%% "newtypes-circe-v0-14" % Versions.newtype
      )
    )
    .dependsOn(common % "compile->compile")
    .aggregate(common)

lazy val defaults =
  Seq(VirtualAxis.scalaABIVersion(Versions.scala3), VirtualAxis.jvm)
