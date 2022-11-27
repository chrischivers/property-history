import sbt.Keys.{scalaVersion, scalacOptions}


object CompilerSettings {

  val settings = Seq(
    scalaVersion                      := Dependencies.Versions.scala,
    scalacOptions                     := Seq(
//      "-target:8",
      "-encoding",
      "UTF-8",
      "-deprecation",
      "-feature",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-Xfatal-warnings"
//      "-Ywarn-value-discard",
//      "-Ywarn-unused:imports",
//      "-Ymacro-annotations"
    )
  )
}
