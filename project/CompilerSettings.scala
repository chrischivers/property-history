import sbt.Keys.{scalaVersion, scalacOptions}
import sbt.addCompilerPlugin
import sbt.Keys._
import sbt._


object CompilerSettings {

  val settings = Seq(
    scalaVersion                      := "2.13.8",
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
