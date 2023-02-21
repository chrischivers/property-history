import io.github.davidgregory084.ScalacOption
import io.github.davidgregory084.TpolecatPlugin.autoImport.{ScalacOptions, tpolecatScalacOptions}
import sbt.CrossVersion
import sbt.Keys.{scalaVersion, scalacOptions}

object CompilerSettings {

  val settings = Seq(
    tpolecatScalacOptions += ScalacOptions.other("-Yretain-trees")
  )
}
