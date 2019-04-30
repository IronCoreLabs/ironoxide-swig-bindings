licenses := ("AGPL-3.0", new URL("https://www.gnu.org/licenses/agpl-3.0.txt"))
// Add the default sonatype repository setting
publishTo := sonatypePublishTo.value

homepage := Some(url("http://github.com/ironcorelabs/ironoxide-java"))

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

useGpg := true

usePgpKeyHex("E84BBF42")

pomExtra := (
    <scm>
      <url>git@github.com:IronCoreLabs/ironoxide-java.git</url>
      <connection>scm:git@github.com:IronCoreLabs/ironoxide-java.git</connection>
    </scm>
    <developers>
      {
      Seq(
        ("coltfred", "Colt Frederickson"),
        ("clintfred", "Clint Frederickson"),
        ("skeet70", "Murph Murphy"),
        ("ernieturner", "Ernie Turner"),
        ("bobwall23", "Bob Wall")
      ).map {
        case (id, name) =>
          <developer>
            <id>{id}</id>
            <name>{name}</name>
            <url>http://github.com/{id}</url>
          </developer>
      }
    }
    </developers>
  )

import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _), enableCrossBuild = true),
  setNextVersion,
  commitNextVersion,
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _), enableCrossBuild = true),
  pushChanges
)
