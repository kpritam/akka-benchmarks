name := "akka-benchmarks"

version := "1.0"

scalaVersion := "2.13.3"

lazy val akka2_6_9 = "2.6.9"
lazy val akka2_5_25 = "2.5.25"

def akka(version: String) =
  Seq(
    "com.typesafe.akka" %% "akka-actor-typed" % version,
    "com.typesafe.akka" %% "akka-stream-typed" % version,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % version % Test
  )

val common = Seq(
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.scalatest" %% "scalatest" % "3.1.0" % Test
)

lazy val `akka-benchmarks` = project.in(file("."))

lazy val `akka-benchmarks-25` =
  project
    .in(file("akka-benchmarks-25"))
    .settings(
      libraryDependencies ++= common ++ akka(akka2_5_25)
    )

lazy val `akka-benchmarks-26` =
  project
    .in(file("akka-benchmarks-26"))
    .settings(
      libraryDependencies ++= common ++ akka(akka2_6_9)
    )
