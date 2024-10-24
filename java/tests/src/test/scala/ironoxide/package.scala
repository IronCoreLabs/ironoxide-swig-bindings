import com.ironcorelabs.sdk._
import scala.util.Try
import cats.scalatest.EitherValues
import org.bouncycastle.jce.provider.BouncyCastleProvider;

package object ironoxide extends EitherValues {
  def createUserAndDevice(maybeUserId: Option[UserId] = None): BlockingDeviceContext = {
    val userId = maybeUserId.getOrElse(UserId.validate(java.util.UUID.randomUUID.toString))
    val jwt = generateValidJwt(userId.getId)
    IronOxide.userCreate(jwt, testUsersPassword, new UserCreateOpts(true), null)
    val dar = IronOxide.generateNewDevice(jwt, testUsersPassword, new DeviceCreateOpts, null)
    new BlockingDeviceContext(new DeviceContext(dar))
  }

  def generateValidJwt(accountId: String = java.util.UUID.randomUUID.toString, expiresInSec: Long = 120): Jwt = {
    java.security.Security.addProvider(new BouncyCastleProvider)
    val projectId = 431
    val segmentId = "ironoxide-java"
    val iakId = 597
    val key =
      "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgNdz7Kffzhn1PnAy1XI2YFUEnDC9TWbzctzIGvWqNv82hRANCAAQG3CAlUWHmBLRRkbwB4zSierd8BRbbyUlczZoNOiXKwbia9CZ9DJ/Dh72c8yFNNVr+hKdvV4Vhj2MQGbMF7Qo3"
    val currentTime = System.currentTimeMillis() / 1000
    val claim = pdi.jwt
      .JwtClaim({ raw"""{"pid": $projectId,"sid": "$segmentId","kid": $iakId}""" })
      .about(accountId)
      .issuedAt(currentTime)
      .expiresAt(currentTime + expiresInSec)
    val jwtString = pdi.jwt.Jwt.encode(claim, key, pdi.jwt.JwtAlgorithm.ES256)
    Jwt.validate(jwtString)
  }

  val testUsersPassword = java.util.UUID.randomUUID.toString

  lazy val (primaryUser, primaryUserDevice, primarySdk) = {
    val primaryUser = Try(UserId.validate(java.util.UUID.randomUUID.toString)).toEither.value
    val jwt = generateValidJwt(primaryUser.getId)
    IronOxide.userCreate(jwt, testUsersPassword, new UserCreateOpts, null)
    val dar = Try(IronOxide.generateNewDevice(jwt, testUsersPassword, new DeviceCreateOpts, null)).toEither.value
    val primaryUserDevice = new BlockingDeviceContext(new DeviceContext(dar))
    val primarySdk = IronOxide.initialize(primaryUserDevice, new IronOxideConfig)
    (primaryUser, primaryUserDevice, primarySdk)
  }

  lazy val (secondaryUser, secondaryUserDevice, secondarySdk) = {
    val secondaryUser = Try(UserId.validate(java.util.UUID.randomUUID.toString)).toEither.value
    val jwt = generateValidJwt(secondaryUser.getId)
    IronOxide.userCreate(jwt, testUsersPassword, new UserCreateOpts, null)
    val dar = Try(IronOxide.generateNewDevice(jwt, testUsersPassword, new DeviceCreateOpts, null)).toEither.value
    val secondaryUserDevice = new DeviceContext(dar)
    val secondarySdk = IronOxide.initialize(secondaryUserDevice, new IronOxideConfig)
    (secondaryUser, secondaryUserDevice, secondarySdk)
  }
}
