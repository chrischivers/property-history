import sbt.librarymanagement.Configurations.{IntegrationTest, Test}
import sbt.file

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val common = projectMatrix
  .in(file("common"))
  .jsPlatform(Seq(Dependencies.Versions.scala))
  .jvmPlatform(Seq(Dependencies.Versions.scala))
  .settings(CompilerSettings.settings)
  .settings(libraryDependencies ++= Dependencies.commonDependencies)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings))
  .settings(Defaults.itSettings)
  .settings(IntegrationTest / internalDependencyClasspath += Attributed.blank((Test / classDirectory).value))
  .settings(IntegrationTest / parallelExecution := false)

lazy val backendCommon = projectMatrix
  .in(file("backend-common"))
  .jvmPlatform(Seq(Dependencies.Versions.scala))
  .settings(CompilerSettings.settings)
  .settings(libraryDependencies ++= Dependencies.backendCommonDependencies)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings))
  .settings(Defaults.itSettings)
  .settings(IntegrationTest / internalDependencyClasspath += Attributed.blank((Test / classDirectory).value))
  .settings(IntegrationTest / parallelExecution := false)
  .dependsOn(common % "test->test;it->test;it->it;compile->compile")
  .aggregate(common)

lazy val publicApi = projectMatrix
  .in(file("public-api"))
  .jvmPlatform(Seq(Dependencies.Versions.scala))
  .settings(CompilerSettings.settings)
  .settings(libraryDependencies ++= Dependencies.publicApiDependencies)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings))
  .settings(Defaults.itSettings)
  .settings(IntegrationTest / internalDependencyClasspath += Attributed.blank((Test / classDirectory).value))
  .settings(IntegrationTest / parallelExecution := false)
  .settings(run / fork := true)
  .settings(
    Compile / resourceGenerators += {
      Def.task[Seq[File]] {
        val _        = (frontend.js(Dependencies.Versions.scala) / Compile / fastLinkJS).value
        val location = (frontend.js(Dependencies.Versions.scala) / Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value

        val outDir = (Compile / resourceManaged).value / "assets"
        IO.listFiles(location).toList.map { file =>
          val (name, ext) = file.baseAndExt
          val out         = outDir / (name + "." + ext)
          IO.copyFile(file, out)
          out
        }
      }
    },
    run / baseDirectory := (ThisBuild / baseDirectory).value,
    reStart / baseDirectory := (ThisBuild / baseDirectory).value
  )
  .dependsOn(backendCommon % "test->test;it->test;it->it;compile->compile")
  .aggregate(backendCommon)
  .enablePlugins(UniversalPlugin, JavaServerAppPackaging, SystemdPlugin)

lazy val crawler = projectMatrix
  .in(file("crawler"))
  .jvmPlatform(Seq(Dependencies.Versions.scala))
  .settings(CompilerSettings.settings)
  .settings(libraryDependencies ++= Dependencies.crawlerDependencies)
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
    .jsPlatform(Seq(Dependencies.Versions.scala))
    .settings(Seq(scalaVersion := Dependencies.Versions.scala))
    .settings(
      scalaJSUseMainModuleInitializer := true,
      libraryDependencies ++= Seq(
        "com.raquo"                    %%% "laminar"              % Dependencies.Versions.laminar,
        "com.raquo"                    %%% "waypoint"             % Dependencies.Versions.wayPoint,
        "org.http4s"                   %%% "http4s-dom"           % Dependencies.Versions.http4sDom,
        "org.http4s"                   %%% "http4s-circe"         % Dependencies.Versions.http4s,
        "com.github.japgolly.scalacss" %%% "core"                 % Dependencies.Versions.scalaCss,
        "com.beachape"                 %%% "enumeratum"           % Dependencies.Versions.enumeration,
        "com.beachape"                 %%% "enumeratum-circe"     % Dependencies.Versions.enumeration,
        "io.circe"                     %%% "circe-refined"        % Dependencies.Versions.circe,
        "io.circe"                     %%% "circe-generic"        % Dependencies.Versions.circe,
        "io.circe"                     %%% "circe-parser"         % Dependencies.Versions.circe,
        "io.monix"                     %%% "newtypes-core"        % Dependencies.Versions.newtype,
        "io.monix"                     %%% "newtypes-circe-v0-14" % Dependencies.Versions.newtype
      )
    )
    .dependsOn(common % "compile->compile")
    .aggregate(common)
