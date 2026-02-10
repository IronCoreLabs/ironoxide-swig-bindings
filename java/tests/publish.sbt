licenses := Seq("AGPL-3.0" -> url("https://www.gnu.org/licenses/agpl-3.0.txt"))
homepage := Some(url("http://github.com/ironcorelabs/ironoxide-java"))

publishArtifact in Test := false
pomIncludeRepository := { _ => false }

