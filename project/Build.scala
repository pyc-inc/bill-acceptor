import sbt._
import sbt.Keys._

object ProjectBuild extends Build {
  
  import BuildSettings._
    
  /** Aggregates tasks for all projects */
  lazy val root = Project("bill-acceptor-all", file("."))
  	.settings(appSettings: _*)
    .aggregate(core, apex)
    
    
  lazy val core = module("core")
    .settings(
        name := "bill-acceptor-core"
     )
  	.settings(libraryDependencies ++=
      Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.3.6",
        "com.github.jodersky" %% "flow" % "2.0.3",
        "ch.qos.logback" % "logback-classic" % "1.0.13" % "compile",
        "org.scalatest" %% "scalatest" % "2.2.1" % "test",
        "com.typesafe.akka" %% "akka-testkit" % "2.3.6" % "test"
      )
    )
    .dependsOn(currency)
    
  
  lazy val apex = module("apex")
    .settings(
        name := "bill-acceptor-apex"
     )
    .dependsOn(core)
    .dependsOn(core % "test->test")    
 
  lazy val currency = ProjectRef(uri("../currency-lib"), "currency-lib")   
}
