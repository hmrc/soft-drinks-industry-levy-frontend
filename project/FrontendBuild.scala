import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

object FrontendBuild extends Build with MicroService {

  val appName = "soft-drinks-industry-levy-frontend"

  override lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "frontend-bootstrap" % "7.22.0",
    "uk.gov.hmrc" %% "play-partials" % "5.3.0",
    "uk.gov.hmrc" %% "play-authorised-frontend" % "6.3.0",
    "uk.gov.hmrc" %% "play-config" % "4.3.0",
    "uk.gov.hmrc" %% "logback-json-logger" % "3.1.0",
    "uk.gov.hmrc" %% "govuk-template" % "5.2.0",
    "uk.gov.hmrc" %% "play-health" % "2.1.0",
    "uk.gov.hmrc" %% "play-ui" % "7.2.0",
    "com.typesafe.play" % "play-json_2.11" % "2.5.12",
    "org.pegdown" % "pegdown" % "1.4.2",
    "net.lightbody.bmp" % "browsermob-core" % "2.1.1",
    "org.scalatest" %% "scalatest" % "3.0.1",
    "org.scalactic" %% "scalactic" % "3.0.1",
    "com.fasterxml.jackson.core" % "jackson-annotations" % "2.7.0",
    "com.fasterxml.jackson.core" % "jackson-databind" % "2.7.2",
    "com.fasterxml.jackson.core" % "jackson-core" % "2.7.4",
    "com.fasterxml.jackson.module" % "jackson-module-scala_2.10" % "2.7.2"
  )

  def test(scope: String = "test") = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "2.3.0" % scope,
    "org.scalatest" %% "scalatest" % "2.2.6" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "org.jsoup" % "jsoup" % "1.8.1" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope
  )

}
