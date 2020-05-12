organization := "com.ironcorelabs"
name := "ironoxide-java"
scalaVersion := "2.12.11"

//We're using sbt to test, but this is a pure java library for now so we don't want scala version
//in the paths and we don't want the scala lib in the dependencies.
crossPaths := false
autoScalaLibrary := false

scalacOptions := Seq(
  "-deprecation",
  "-encoding",
  "UTF-8", // yes, this is 2 args
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Xfuture",
  "-language:higherKinds"
)

javacOptions in (Compile, doc) ++= Seq("-Xdoclint")
javacOptions in (Compile, compile) ++= Seq("-source", "1.8", "-target", "1.8")

libraryDependencies ++= Seq(
  "com.pauldijou" %% "jwt-core" % "4.3.0",
  "org.scalatest" %% "scalatest" % "3.1.2",
  "org.scodec" %% "scodec-bits" % "1.1.14",
  "com.github.melrief" %% "pureconfig" % "0.7.0",
  "com.ironcorelabs" %% "cats-scalatest" % "3.0.5",
  "org.bouncycastle" % "bcpkix-jdk15on" % "1.65"
).map(_ % "test")
// Include the generated java as part of the source directories
unmanagedSourceDirectories in Compile ++= javaSources(baseDirectory.value)
// HACK: without these lines, the console is basically unusable,
// since all imports are reported as being unused (and then become
// fatal errors).
scalacOptions in (Compile, console) ~= {
  _.filterNot(_.startsWith("-Xlint")).filterNot(_.startsWith("-Ywarn"))
}
scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value

javaOptions in Test += s"-Djava.library.path=../../target/debug/"
fork in Test := true
envVars in Test := Map("IRONCORE_ENV" -> "stage")

def javaSources(base: File): Seq[File] = {
  val finder: PathFinder = base / ".." / ".." / "target" / "debug" / "build" * "ironoxide-java*" / "out"
  val files = finder.get
  if (files.length == 0) throw new Exception("Unable to find Java source files in OUT_DIR")
  else if (files.length > 1)
    throw new Exception(
      "Too many Java source directories found in OUT_DIR. Try running `cargo clean` and re-compiling."
    )
  files
}
