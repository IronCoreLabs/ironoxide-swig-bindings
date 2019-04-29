package ironrust

import pureconfig._
import java.io.File
import scala.util.Try

case class KeyConfig(pemFileBytes: String, projectId: Int, segmentId: String, serviceKeyId: Int)

object KeyConfig {
  def fromTestConfig(): Try[KeyConfig] =
    loadConfigFromFiles[KeyConfig](List(new File("src/test/resources/service-keys.conf")))
}