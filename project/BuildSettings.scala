import sbt._
import sbt.Keys._

import sbtbuildinfo.Plugin._
import com.typesafe.sbteclipse.plugin.EclipsePlugin.EclipseKeys

object BuildSettings {
  val buildTime = SettingKey[String]("build-time")
  
  val defaultScalaVersion = "2.10.4"

  val basicSettings = Defaults.defaultSettings ++ Seq(
    name := "bill-acceptor",
    version := "0.1-SNAPSHOT",
    organization := "inc.pyc",
    scalaVersion := defaultScalaVersion,
    scalacOptions <<= scalaVersion map { sv: String =>
      if (sv.startsWith("2.10."))
        Seq("-deprecation", "-unchecked", "-feature", "-language:postfixOps", "-language:implicitConversions")
      else
        Seq("-deprecation", "-unchecked")
    },
    resolvers ++= Seq[Resolver](
        "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases"
    )
  )

  val appSettings = 
    basicSettings ++
    buildInfoSettings ++
    seq(
      buildTime := System.currentTimeMillis.toString,

      // build-info
      buildInfoKeys ++= Seq[BuildInfoKey](buildTime),
      buildInfoPackage := "inc.pyc",
      sourceGenerators in Compile <+= buildInfo,
      
      publishArtifact in Test := false,

      publish <<= publish dependsOn (test in Test),

      // eclipse
      EclipseKeys.withSource := true,

      publishMavenStyle := true
    )
  
  
  def module(name: String, settings: Seq[Def.Setting[_]] = Seq.empty) =
  	Project(name, file(name.replace("-", "")), settings = appSettings)
  	
}

