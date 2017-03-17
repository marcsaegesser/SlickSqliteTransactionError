lazy val sqliteSlick = (project in file("."))
  .settings (
    name := "SqliteSlickDemo",
    organization := "org.fubar",
    version := "1.0.0-SNAPSHOT",
    scalaVersion in ThisBuild := "2.11.8",
    scalacOptions in ThisBuild ++= Seq(
      "-feature",
      "-deprecation",
      "-Yno-adapted-args",
      "-Ywarn-value-discard",
      "-Ywarn-numeric-widen",
      "-Ywarn-dead-code",
      "-Xlint",
      "-Xfatal-warnings",
      "-unchecked",
      "-language:implicitConversions"
    ),

    // This initializes SLF4J before running tests. See http://stackoverflow.com/a/12095245
    testOptions in Test += Tests.Setup(classLoader =>
      classLoader
        .loadClass("org.slf4j.LoggerFactory")
        .getMethod("getLogger", classLoader.loadClass("java.lang.String"))
        .invoke(null, "ROOT")
    ),

    resolvers += "AMI Release" at "http://build05.eng.amientertainment.net:8081/nexus/content/repositories/releases",
    resolvers += "AMI Snapshot" at "http://build05.eng.amientertainment.net:8081/nexus/content/repositories/snapshots",

    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging-slf4j"            % "2.1.2",
      "com.typesafe.slick"         %% "slick"                          % "3.1.1",
      "org.xerial"                 %  "sqlite-jdbc"                    % "3.16.1",
      "ch.qos.logback"             %  "logback-classic"                % "1.1.2",
      "com.h2database"             %  "h2"                             % "1.4.187" % "test",
      "org.scalatest"              %% "scalatest"                      % "3.0.0"   % "test",
      "org.scalacheck"             %% "scalacheck"                     % "1.13.1"  % "test"
    ),

    initialCommands := ""
  )
