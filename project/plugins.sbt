addSbtPlugin("org.scalameta"                % "sbt-scalafmt"         % "2.4.6")
addSbtPlugin("com.github.sbt"               % "sbt-native-packager"  % "1.9.16")
addSbtPlugin("io.spray"                     % "sbt-revolver"         % "0.9.1")
addSbtPlugin("com.eed3si9n"                 % "sbt-projectmatrix"    % "0.9.0")
addSbtPlugin("org.scala-js"                 % "sbt-scalajs"          % "1.13.0")
addSbtPlugin("com.disneystreaming.smithy4s" % "smithy4s-sbt-codegen" % "0.17.1")
addSbtPlugin("io.github.davidgregory084"    % "sbt-tpolecat"         % "0.4.2")
addSbtPlugin("com.timushev.sbt"             % "sbt-updates"          % "0.6.4")

ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)
