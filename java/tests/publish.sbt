licenses := Seq("AGPL-3.0" -> url("https://www.gnu.org/licenses/agpl-3.0.txt"))
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
        ("bobwall23", "Bob Wall"),
        ("clintfred", "Clint Frederickson"),
        ("coltfred", "Colt Frederickson"),
        ("ernieturner", "Ernie Turner"),
        ("giarc3", "Craig Colegrove"),
        ("skeet70", "Murph Murphy"),
      ).map {
        case (id, name) =>
          <developer>
            <id>{id}</id>
            <name>{name}</name>
            <url>https://github.com/{id}</url>
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
