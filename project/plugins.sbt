resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"
resolvers += MavenRepository("HMRC-open-artefacts-maven2", "https://open.artefacts.tax.service.gov.uk/maven2")
resolvers += Resolver.url("HMRC-open-artefacts-ivy", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)
resolvers += Resolver.jcenterRepo

libraryDependencies += "io.monix" %% "monix" % "2.3.0" pomOnly()

addSbtPlugin("uk.gov.hmrc"          %  "sbt-auto-build"          % "3.5.0")
addSbtPlugin("uk.gov.hmrc"          %  "sbt-distributables"      % "2.1.0")
addSbtPlugin("com.typesafe.play"    %  "sbt-plugin"              % "2.8.8")
addSbtPlugin("uk.gov.hmrc"          %  "sbt-settings"            % "4.8.0")
addSbtPlugin("org.scoverage"        %  "sbt-scoverage"           % "1.9.2")
addSbtPlugin("org.scalastyle"       %% "scalastyle-sbt-plugin"   % "1.0.0")
addSbtPlugin("net.ground5hark.sbt"  %  "sbt-concat"              % "0.2.0")
addSbtPlugin("com.typesafe.sbt"     %  "sbt-uglify"              % "2.0.0")
addSbtPlugin("com.lucidchart"       %  "sbt-scalafmt"            % "1.16")
addSbtPlugin("org.irundaia.sbt"     %  "sbt-sassify"             % "1.5.1")
