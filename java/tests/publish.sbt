licenses := Seq("AGPL-3.0" -> url("https://www.gnu.org/licenses/agpl-3.0.txt"))
// Add the default sonatype repository setting
publishTo := sonatypePublishTo.value

homepage := Some(url("http://github.com/ironcorelabs/ironoxide-java"))

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

useGpg := true

// This key is the last 4 bytes of the key ID of the signing subkey.
usePgpKeyHex("9FA43559")

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
  runClean,
  runTest,
  ReleaseStep(action = Command.process("publishSigned", _), enableCrossBuild = true),
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _), enableCrossBuild = true),
)
