import sbt.CrossVersion
import sbt.Keys.{scalaVersion, scalacOptions}

object CompilerSettings {

  def scalacOptionsVersion(scalaVersion: String): Seq[String] =
    (CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, _)) =>
        Seq(
          "-language:implicitConversions",
          "-java-output-version",
          "8",
          "-Yretain-trees"
        )
      case Some((2, n)) if n >= 13 =>
        Seq(
          "-encoding",
          "UTF-8",
          "-deprecation",
          "-feature",
          "-language:higherKinds",
          "-language:implicitConversions",
          "-Xfatal-warnings"
        )
    })

  val settings = Seq(
    scalacOptions := Seq(
//      "-target:8",
      "-encoding",
      "UTF-8",
      "-deprecation",
      "-feature",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-Xfatal-warnings"
//      "-Ywarn-value-discard"
//      "-Ywarn-unused:imports",
//      "-Ymacro-annotations"
    )
  )
}
