import play.core.PlayVersion
import play.routes.compiler.StaticRoutesGenerator
import play.sbt.PlayImport._
import play.sbt.routes.RoutesKeys.routesGenerator
import sbt.Keys._
import sbt.Tests.{SubProcess, Group}
import sbt._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.{SbtBuildInfo, ShellPrompt, SbtAutoBuildPlugin}
import DefaultBuildSettings._

object FrontendBuild extends Build {

  lazy val microservice = Project("soft-drinks-industry-levy-frontend", file("."))
    .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
    .settings(scalaSettings: _*)
    .settings(publishingSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(
      libraryDependencies ++= Seq(
        ws,
        "uk.gov.hmrc" %% "frontend-bootstrap" % "8.11.0",
        "uk.gov.hmrc" %% "play-partials" % "6.1.0",
        "com.typesafe.play" %% "play-json" % "2.5.12",
        "org.pegdown" % "pegdown" % "1.4.2",
        "net.lightbody.bmp" % "browsermob-core" % "2.1.1",
        "org.scalatest" %% "scalatest" % "3.0.1",
        "org.scalactic" %% "scalactic" % "3.0.1",
        "com.fasterxml.jackson.core" % "jackson-annotations" % "2.7.0",
        "com.fasterxml.jackson.core" % "jackson-databind" % "2.7.2",
        "com.fasterxml.jackson.core" % "jackson-core" % "2.7.4",
        "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.7.2",
        "uk.gov.hmrc" %% "http-caching-client" % "7.0.0",
        
        // test dependencies
        "uk.gov.hmrc" %% "hmrctest" % "2.3.0" % "test",
        "org.scalatest" %% "scalatest" % "2.2.6" % "test",
        "org.pegdown" % "pegdown" % "1.6.0" % "test",
        "org.jsoup" % "jsoup" % "1.8.1" % "test",
        "com.typesafe.play" %% "play-test" % PlayVersion.current % "test",
        "org.mockito" % "mockito-core" % "2.7.22" % "test",
        "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % "test"
      ),
      retrieveManaged := true,
      routesGenerator := StaticRoutesGenerator,
      resolvers ++= Seq(
        Resolver.bintrayRepo("hmrc", "releases"),
        Resolver.jcenterRepo
      )
    )
    .settings(PlayKeys.playDefaultPort := 8700)
    .configs(IntegrationTest)
    .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)

}
