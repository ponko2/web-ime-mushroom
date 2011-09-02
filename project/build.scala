import sbt._

import Keys._
import AndroidKeys._

object General {
  val settings = Defaults.defaultSettings ++ Seq (
    name := "Web IME Mushroom",
    version := "0.4.0",
    scalaVersion := "2.9.0-1",
    platformName in Android := "android-7"
  )

  lazy val fullAndroidSettings =
    General.settings ++
    AndroidProject.androidSettings ++
    TypedResources.settings ++
    AndroidMarketPublish.settings ++ Seq (
      keyalias in Android := "ponko2",
      libraryDependencies ++= Seq("org.scalatest"  %% "scalatest" % "1.6.1" % "test",
                                  "net.databinder" %% "dispatch-http" % "0.7.8",
                                  "net.databinder" %% "dispatch-lift-json" % "0.7.8")
    )
}

object AndroidBuild extends Build {
  lazy val main = Project (
    "Web IME Mushroom",
    file("."),
    settings = General.fullAndroidSettings
  )

  lazy val tests = Project (
    "tests",
    file("tests"),
    settings = General.settings ++ AndroidTest.androidSettings
  ) dependsOn main
}
