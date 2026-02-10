licenses := Seq("AGPL-3.0" -> url("https://www.gnu.org/licenses/agpl-3.0.txt"))

homepage := Some(url("http://github.com/ironcorelabs/ironoxide-java"))
publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { _ => false }

pomExtra := (
  <scm>
      <url>git@github.com:IronCoreLabs/ironoxide-java.git</url>
      <connection>scm:git@github.com:IronCoreLabs/ironoxide-java.git</connection>
  </scm>
  <developers>
    {
      Seq(
        ("bobwall23", "Bob Wall"),
        ("coltfred", "Colt Frederickson"),
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
