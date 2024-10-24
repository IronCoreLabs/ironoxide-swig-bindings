package ironoxide

import scala.util.Try
import com.ironcorelabs.sdk._
import java.util.OptionalLong
import java.util.Optional

class UserTests extends TestSuite {
  "Jwt" should {
    "return error for invalid jwt" in {
      val badJwt = Try(Jwt.validate("foo")).toEither
      badJwt.isLeft shouldBe true
    }

    //{
    // "sub": "abcABC012_.$#|@/:;=+'-d1226d1b-4c39-49da-933c-642e23ac1945",
    // "pid": 438,
    // "sid": "ironoxide-dev1",
    // "kid": 593,
    // "iat": 1591901740,
    // "exp": 1591901860
    // }
    "accept valid jwt" in {
      val jwt = Try(
        Jwt.validate(
          "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJhYmNBQkMwMTJfLiQjfEAvOjs9KyctZDEyMjZkMWItNGMzOS00OWRhLTkzM2MtNjQyZTIzYWMxOTQ1IiwicGlkIjo0MzgsInNpZCI6Imlyb25veGlkZS1kZXYxIiwia2lkIjo1OTMsImlhdCI6MTU5MTkwMTc0MCwiZXhwIjoxNTkxOTAxODYwfQ.wgs_tnh89SlKnIkoQHdlC0REjkxTl1P8qtDSQwWTFKwo8KQKXUQdpp4BfwqUqLcxA0BW6_XfVRlqMX5zcvCc6w"
        )
      ).toEither.value
      jwt.getAlgorithm shouldBe "ES256"
      val claims = jwt.getClaims
      claims.getSub shouldBe "abcABC012_.$#|@/:;=+'-d1226d1b-4c39-49da-933c-642e23ac1945"
      claims.getPid shouldBe OptionalLong.of(438)
      claims.getPrefixedPid shouldBe OptionalLong.empty
      claims.getSid shouldBe Optional.of("ironoxide-dev1")
      claims.getPrefixedSid shouldBe Optional.empty
      claims.getKid shouldBe OptionalLong.of(593)
      claims.getPrefixedKid shouldBe OptionalLong.empty
      claims.getUid shouldBe Optional.empty
      claims.getPrefixedUid shouldBe Optional.empty
      claims.getIat shouldBe 1591901740
      claims.getExp shouldBe 1591901860
    }

    //{
    // "sub": "abcABC012_.$#|@/:;=+'-d1226d1b-4c39-49da-933c-642e23ac1945",
    // "http://ironcore/pid": 438,
    // "http://ironcore/sid": "ironoxide-dev1",
    // "http://ironcore/kid": 593,
    // "iat": 1591901740,
    // "exp": 1591901860
    // }
    "accept valid jwt with prefixed claims" in {
      val jwt = Try(
        Jwt.validate(
          "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhYmNBQkMwMTJfLiQjfEAvOjs9KyctZDEyMjZkMWItNGMzOS00OWRhLTkzM2MtNjQyZTIzYWMxOTQ1IiwiaHR0cDovL2lyb25jb3JlL3BpZCI6NDM4LCJodHRwOi8vaXJvbmNvcmUvc2lkIjoiaXJvbm94aWRlLWRldjEiLCJodHRwOi8vaXJvbmNvcmUva2lkIjo1OTMsImlhdCI6MTU5MTkwMTc0MCwiZXhwIjoxNTkxOTAxODYwfQ.bCIDkN6bXaz85pl9s55MoAByzm0LPlMPlT5WqjT-R6F80EKFO0gqGT1m7330gxnN-LWtxonBVv1IoK9tl-NEvg"
        )
      ).toEither.value
      jwt.getAlgorithm shouldBe "ES256"
      val claims = jwt.getClaims
      claims.getSub shouldBe "abcABC012_.$#|@/:;=+'-d1226d1b-4c39-49da-933c-642e23ac1945"
      claims.getPid shouldBe OptionalLong.empty
      claims.getPrefixedPid shouldBe OptionalLong.of(438)
      claims.getSid shouldBe Optional.empty
      claims.getPrefixedSid shouldBe Optional.of("ironoxide-dev1")
      claims.getKid shouldBe OptionalLong.empty
      claims.getPrefixedKid shouldBe OptionalLong.of(593)
      claims.getUid shouldBe Optional.empty
      claims.getPrefixedUid shouldBe Optional.empty
      claims.getIat shouldBe 1591901740
      claims.getExp shouldBe 1591901860
    }

  }

  "User Create" should {
    "return error for outdated jwt" in {
      val jwtString =
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE1NTA3NzE4MjMsImlhdCI6MTU1MDc3MTcwMywia2lkIjo1NTEsInBpZCI6MTAxMiwic2lkIjoidGVzdC1zZWdtZW50Iiwic3ViIjoiYTAzYjhlNTYtMTVkMi00Y2Y3LTk0MWYtYzYwMWU1NzUxNjNiIn0.vlqt0da5ltA2dYEK9i_pfRxPd3K2uexnkbAbzmbjW65XNcWlBOIbcdmmQLnSIZkRyTORD3DLXOIPYbGlApaTCR5WbaR3oPiSsR9IqdhgMEZxCcarqGg7b_zzwTP98fDcALGZNGsJL1hIrl3EEXdPoYjsOJ5LMF1H57NZiteBDAsm1zfXgOgCtvCdt7PQFSCpM5GyE3und9VnEgjtcQ6HAZYdutqjI79vaTnjt2A1X38pbHcnfvSanzJoeU3szwtBiVlB3cfXbROvBC7Kz8KvbWJzImJcJiRT-KyI4kk3l8wAs2FUjSRco8AQ1nIX21QHlRI0vVr_vdOd_pTXOUU51g"
      val jwt = Try(Jwt.validate(jwtString)).toEither.value
      val resp = Try(IronOxide.userCreate(jwt, "foo", new UserCreateOpts(true), null)).toEither
      resp.leftValue.getMessage should include("ServerError { message: \"\'jwt ey")
    }
  }

  "User Verify" should {
    "fail for non-user" in {
      val verifyResult = IronOxide.userVerify(generateValidJwt("fakeUser123"), null)
      verifyResult.isEmpty shouldBe true
    }
    "succeed for valid user" in {
      val dc = createUserAndDevice()
      val verifyResult = IronOxide.userVerify(generateValidJwt(dc.getAccountId.getId), null)
      verifyResult.isPresent shouldBe true
      val user = verifyResult.get
      user.getAccountId shouldBe dc.getAccountId
      user.getNeedsRotation shouldBe true
    }
    "return error for outdated jwt" in {
      val jwtString =
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE1NTA3NzE4MjMsImlhdCI6MTU1MDc3MTcwMywia2lkIjo1NTEsInBpZCI6MTAxMiwic2lkIjoidGVzdC1zZWdtZW50Iiwic3ViIjoiYTAzYjhlNTYtMTVkMi00Y2Y3LTk0MWYtYzYwMWU1NzUxNjNiIn0.vlqt0da5ltA2dYEK9i_pfRxPd3K2uexnkbAbzmbjW65XNcWlBOIbcdmmQLnSIZkRyTORD3DLXOIPYbGlApaTCR5WbaR3oPiSsR9IqdhgMEZxCcarqGg7b_zzwTP98fDcALGZNGsJL1hIrl3EEXdPoYjsOJ5LMF1H57NZiteBDAsm1zfXgOgCtvCdt7PQFSCpM5GyE3und9VnEgjtcQ6HAZYdutqjI79vaTnjt2A1X38pbHcnfvSanzJoeU3szwtBiVlB3cfXbROvBC7Kz8KvbWJzImJcJiRT-KyI4kk3l8wAs2FUjSRco8AQ1nIX21QHlRI0vVr_vdOd_pTXOUU51g"
      val jwt = Try(Jwt.validate(jwtString)).toEither.value
      val resp = Try(IronOxide.userVerify(jwt, null))
      resp.isFailure shouldBe true
      resp.failed.get.getMessage should include("was an invalid authorization token")
    }
    "fail with short timeout" in {
      val jwt = generateValidJwt(primaryUser.getId)
      val resp =
        Try(IronOxide.userCreate(jwt, testUsersPassword, new UserCreateOpts(true), Duration.fromMillis(5)))
      resp.isFailure shouldBe true
    }
  }

  "User Rotate Private Key" should {
    "successfully rotate a user" in {
      val dc = createUserAndDevice()
      val sdk = IronOxide.initialize(dc, new IronOxideConfig)
      val rotationResult1 = sdk.userRotatePrivateKey(testUsersPassword)
      rotationResult1.getNeedsRotation shouldBe false
      val rotationResult2 = sdk.userRotatePrivateKey(testUsersPassword)
      rotationResult1.getUserMasterPrivateKey.asBytes should not be rotationResult2.getUserMasterPrivateKey.asBytes
    }
    "fail for wrong password" in {
      val rotationResult = Try(primarySdk.userRotatePrivateKey("wrong password"))
      rotationResult.isFailure shouldBe true
    }

  }

  "Generate Device" should {
    "fail with short timeout" in {
      val jwt = generateValidJwt(primaryUser.getId)
      // call will fail because the duration is too short
      val generate =
        Try(IronOxide.generateNewDevice(jwt, testUsersPassword, new DeviceCreateOpts, Duration.fromMillis(5)))
      generate.isFailure shouldBe true
    }
    "fail for bad user password" in {
      val jwt = generateValidJwt(primaryUser.getId)
      val expectedException =
        Try(IronOxide.generateNewDevice(jwt, "BAD PASSWORD", new DeviceCreateOpts, null)).toEither
      expectedException.leftValue.getMessage should include("AesError")
    }
  }

  "Initialize and rotate" should {
    "rotate a user on init" in {
      val dc = createUserAndDevice()
      val jwt = generateValidJwt(dc.getAccountId.getId)
      val verifyResult1 = IronOxide.userVerify(jwt, null).get
      verifyResult1.getNeedsRotation shouldBe true
      IronOxide.initializeAndRotate(dc, testUsersPassword, new IronOxideConfig, null)
      val verifyResult2 = IronOxide.userVerify(jwt, null).get
      verifyResult2.getNeedsRotation shouldBe false
    }
  }

  "Get Public Key" should {
    "get public keys for valid users" in {
      val realUsers = Array(primaryUser, secondaryUser).sortBy(_.getId)
      val fakeUsers = Array(UserId.validate(java.util.UUID.randomUUID.toString))
      val keys = Try(primarySdk.userGetPublicKey(realUsers ++ fakeUsers)).toEither.value.map(_.getUser).sortBy(_.getId)
      keys shouldBe realUsers
    }
  }

  "List Devices" should {
    "return the primary user's device" in {
      val devices = Try(primarySdk.userListDevices).toEither.value.getResult
      devices.length should be > 0
      devices(0).isCurrentDevice shouldBe true
      devices(0).getName.isEmpty shouldBe true
      devices(0).getCreated shouldBe devices(0).getLastUpdated
    }
  }

  "Device Delete" should {
    "delete a user's device" in {
      val jwt = generateValidJwt()
      val _ = IronOxide.userCreate(jwt, testUsersPassword, new UserCreateOpts, null)
      val device = Try(IronOxide.generateNewDevice(jwt, testUsersPassword, new DeviceCreateOpts, null)).toEither.value
      val sdk = Try(IronOxide.initialize(device, new IronOxideConfig)).toEither.value
      val deleteResult = Try(sdk.userDeleteDevice(dar.getDeviceId)).toEither.value
      dar.getDeviceId shouldBe deleteResult
      // this call will fail because the device in `sdk` was deleted
      val groupList = Try(sdk.groupList)
      groupList.isFailure shouldBe true
    }
  }
}
