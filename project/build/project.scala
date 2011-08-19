import sbt._

trait Defaults {
  def androidPlatformName = "android-7"
}
class Parent(info: ProjectInfo) extends ParentProject(info) {
  override def shouldCheckOutputDirectories = false
  override def updateAction = task { None }

  lazy val main  = project(".", "Web IME Mushroom", new MainProject(_))
  lazy val tests = project("tests",  "tests", new TestProject(_), main)

  class MainProject(info: ProjectInfo) extends AndroidProject(info) with Defaults with MarketPublish with TypedResources {
    val keyalias         = "ponko2"
    val scalatest        = "org.scalatest"  % "scalatest_2.9.0"            % "1.6.1" % "test"
    val dispatchHttp     = "net.databinder" % "dispatch-http_2.9.0-1"      % "0.7.8"
    val dispatchLiftJson = "net.databinder" % "dispatch-lift-json_2.9.0-1" % "0.7.8"
  }

  class TestProject(info: ProjectInfo) extends AndroidTestProject(info) with Defaults
}
