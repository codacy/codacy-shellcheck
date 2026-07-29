organization := "com.codacy"

name := "codacy-shellcheck"

scalaVersion := "2.13.16"

libraryDependencies ++= Seq(
  "com.codacy" %% "codacy-engine-scala-seed" % "6.1.4",
  "com.github.pathikrit" %% "better-files" % "3.9.2"
)

enablePlugins(GraalVMNativeImagePlugin)

val graalVersion = "java11-21.1.0"

graalVMNativeImageGraalVersion := Some(graalVersion)
graalVMNativeImageOptions ++= Seq(
  "-O1",
  "-H:+ReportExceptionStackTraces",
  "--no-fallback",
  "--no-server",
  "--initialize-at-build-time",
  "--report-unsupported-elements-at-runtime",
  "--static"
)
