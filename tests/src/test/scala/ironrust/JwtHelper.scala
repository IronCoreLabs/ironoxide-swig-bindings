package ironrust

import pdi.jwt.{Jwt, JwtClaim, JwtAlgorithm};
import java.security.PrivateKey

object JwtHelper{
  //Needed so that BC can create PrivateKeys from the PEM file base64
  java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

  val config = KeyConfig.fromTestConfig.getOrElse(throw new Exception("Couldn't read unit test config file with JWT details."))

  def generateValidJwt(accountId: String, expiresInSec: Long = 120) = {
    val claim = JwtClaim({raw"""{"pid": ${config.projectId},"sid": "${config.segmentId}","kid": ${config.serviceKeyId}}"""})
      .about(accountId)
      .issuedAt(System.currentTimeMillis() / 1000)
      .expiresIn(expiresInSec)

    Jwt.encode(claim, stringToPrivateKey(config.pemFileBytes), JwtAlgorithm.ES256)
  }

  def stringToPrivateKey(pemfile: String): java.security.PrivateKey = {
    val privateKeyBytes = scodec.bits.ByteVector.fromBase64(pemfile).getOrElse(throw new Exception("'$pemfile' was not base64"))
    val pKey = org.bouncycastle.asn1.sec.ECPrivateKey.getInstance(privateKeyBytes.toArray)
    new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter()
      .getPrivateKey(new org.bouncycastle.asn1.pkcs.PrivateKeyInfo(
        new org.bouncycastle.asn1.x509.AlgorithmIdentifier(
            org.bouncycastle.asn1.x9.X9ObjectIdentifiers.id_ecPublicKey,
            pKey.getParameters()
        ), pKey))
  }
}