import play.core.PlayVersion
import play.routes.compiler.StaticRoutesGenerator
import play.sbt.PlayImport._
import play.sbt.routes.RoutesKeys.routesGenerator
import sbt.Keys._
import sbt._
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning
import com.typesafe.sbt.web.Import._
import net.ground5hark.sbt.concat.Import._
import com.typesafe.sbt.uglify.Import._
import com.typesafe.sbt.web.SbtWeb

object FrontendBuild extends Build {

  lazy val scoverageSettings = {
    Seq(
      // Semicolon-separated list of regexs matching classes to exclude
      ScoverageKeys.coverageExcludedPackages :=
        """
          |<empty>;
          |app.*;
          |views.*;
          |uk.gov.hmrc.*;
          |prod.*;
          |sdil.config.*;
          |sdil.connectors.*;
          |sdil.models.*;
          |sdil.controllers.Routes;
          |sdil.controllers.RoutesPrefix;
          |testOnlyDoNotUseInAppConf.*;
          |sdil.controllers.test.*;
          |sdil.connectors.TestConnector""".stripMargin,
      ScoverageKeys.coverageMinimum := 80,
      ScoverageKeys.coverageFailOnMinimum := false,
      ScoverageKeys.coverageHighlighting := true
    )
  }

  lazy val microservice = Project("soft-drinks-industry-levy-frontend", file("."))
    .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
    .settings(scalaSettings: _*)
    .settings(publishingSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(
      scoverageSettings,
      libraryDependencies ++= Seq(
        ws,
        "uk.gov.hmrc" %% "bootstrap-play-25" % "1.3.0",
        "uk.gov.hmrc" %% "govuk-template" % "5.17.0",
        "uk.gov.hmrc" %% "play-ui" % "7.13.0",
        "uk.gov.hmrc" %% "play-partials" % "6.1.0",
        "com.typesafe.play" %% "play-json" % "2.5.18",
        "org.scalactic" %% "scalactic" % "3.0.4",
        "uk.gov.hmrc" %% "auth-client" % "2.5.0",
        "uk.gov.hmrc" %% "http-caching-client" % "7.1.0",
        "uk.gov.hmrc" %% "play-conditional-form-mapping" % "0.2.0",
        "com.softwaremill.macwire" %% "macros" % "2.3.0" % "provided",
        "com.softwaremill.macwire" %% "macrosakka" % "2.3.0" % "provided",
        "com.softwaremill.macwire" %% "util" % "2.3.0",
        "com.softwaremill.macwire" %% "proxy" % "2.3.0",
        "org.typelevel" %% "cats-core" % "1.0.1",

        // test dependencies
        "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % "test",
        "org.scalatest" %% "scalatest" % "3.0.1" % "test",
        "org.pegdown" % "pegdown" % "1.6.0" % "test",
        "org.jsoup" % "jsoup" % "1.11.2" % "test",
        "com.typesafe.play" %% "play-test" % PlayVersion.current % "test",
        "org.mockito" % "mockito-core" % "2.13.0" % "test",
        "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % "test"
      ),
      retrieveManaged := true,
      resolvers ++= Seq(
        Resolver.bintrayRepo("hmrc", "releases"),
        Resolver.jcenterRepo
      )
    )
    .settings(PlayKeys.playDefaultPort := 8700)
    .settings(
      // concatenate js
      Concat.groups := Seq(
        "javascripts/sdil-frontend-app.js" -> group(Seq(
          "javascripts/application.js",
          "javascripts/details.polyfill.js"
        ))
      ),
      // below line required to force asset pipeline to operate in dev rather than only prod
      pipelineStages in Assets := Seq(concat)
    )
    .configs(IntegrationTest)
    .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
}
