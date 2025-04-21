val scala3Version = "2.13.10"

lazy val root = project
  .in(file("."))
  .settings(
    name := "messaging-server",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % "2.13.16",
      "com.google.protobuf" % "protobuf-java" % "3.25.0",
      "org.xerial" % "sqlite-jdbc" % "3.49.1.0",
      "org.scalameta" %% "munit" % "1.0.4" % Test
    )
  )
