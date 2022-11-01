import sbt.librarymanagement.Configurations.{IntegrationTest, Test}

lazy val common = (project in file("common"))
  .settings(CompilerSettings.settings)
  .settings(libraryDependencies ++= Dependencies.commonDependencies)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings))
  .settings(Defaults.itSettings)
  .settings(IntegrationTest / internalDependencyClasspath += Attributed.blank((Test / classDirectory).value))

lazy val publicApi = (project in file("public-api"))
  .settings(CompilerSettings.settings)
  .settings(libraryDependencies ++= Dependencies.publicApiDependencies)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings))
  .settings(Defaults.itSettings)
  .settings(IntegrationTest / internalDependencyClasspath += Attributed.blank((Test / classDirectory).value))
  .dependsOn(common % "test->test;compile->compile")
  .aggregate(common)

lazy val crawler = (project in file("crawler"))
  .settings(CompilerSettings.settings)
  .settings(libraryDependencies ++= Dependencies.crawlerDependencies)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings))
  .settings(Defaults.itSettings)
  .settings(IntegrationTest / internalDependencyClasspath += Attributed.blank((Test / classDirectory).value))
  .dependsOn(common % "test->test;compile->compile")
  .aggregate(common)
  .enablePlugins(UniversalPlugin, JavaServerAppPackaging, SystemdPlugin)

