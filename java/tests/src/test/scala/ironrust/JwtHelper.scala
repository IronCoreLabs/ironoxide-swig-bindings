package ironrust

import pdi.jwt.{Jwt, JwtClaim, JwtAlgorithm};
import org.bouncycastle.jce.provider.BouncyCastleProvider;

object JwtHelper {
  val projectId = 431
  val segmentId = "ironoxide-java"
  val iakId = 597
  val key = """MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgNdz7Kffzhn1PnAy1
XI2YFUEnDC9TWbzctzIGvWqNv82hRANCAAQG3CAlUWHmBLRRkbwB4zSierd8BRbb
yUlczZoNOiXKwbia9CZ9DJ/Dh72c8yFNNVr+hKdvV4Vhj2MQGbMF7Qo3"""

  java.security.Security.addProvider(new BouncyCastleProvider)

  def generateValidJwt(accountId: String, expiresInSec: Long = 120) = {
    val currentTime = System.currentTimeMillis() / 1000
    val claim = JwtClaim({ raw"""{"pid": $projectId,"sid": "$segmentId","kid": $iakId}""" })
      .about(accountId)
      .issuedAt(currentTime)
      .expiresAt(currentTime + expiresInSec)
    Jwt.encode(claim, key, JwtAlgorithm.ES256)
  }
}
