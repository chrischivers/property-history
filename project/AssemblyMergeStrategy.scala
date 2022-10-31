import sbt.Def.settings
import sbtassembly.AssemblyKeys.assemblyMergeStrategy
import sbtassembly.AssemblyPlugin.autoImport
import sbtassembly.AssemblyPlugin.autoImport.{MergeStrategy, assembly}
import sbtassembly.PathList

object AssemblyMergeStrategy {

  def default = {
    settings(assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
      case x                                                    => (assembly / assemblyMergeStrategy).value(x)
    })
  }

}
