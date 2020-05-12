package ironrust

import java.util.Calendar
import org.scalatest.CancelAfterFailure
import com.ironcorelabs.sdk._
import scala.util.Try
import scodec.bits.ByteVector

class FullIntegrationTest extends DudeSuite with CancelAfterFailure {
  def checkEqualsAndHashDeclared[A](objClass: java.lang.Class[A]) = {
    val hashCodeClass = objClass.getMethod("hashCode").getDeclaringClass
    val equalsClass = objClass.getMethod("equals", classOf[java.lang.Object]).getDeclaringClass
    assert(
      hashCodeClass == objClass,
      s"""\nThe function `hashCode()` was not implemented for $objClass, but was instead inherited from $hashCodeClass"""
    )
    assert(
      equalsClass == objClass,
      s"""\nThe function `equals()` was not implemented for $objClass, but was instead inherited from $equalsClass"""
    )
  }

  // Generates a random user ID and password to use for this full integration test
  val primaryTestUserId = Try(UserId.validate(java.util.UUID.randomUUID.toString)).toEither.value
  val primaryTestUserPassword = java.util.UUID.randomUUID.toString

  // Stores record of integration test users device context parts that are then used to initialize the SDK
  var primaryTestUserSegmentId = 0L
  var primaryTestUserPrivateDeviceKeyBytes: Array[Byte] = null
  var primaryTestUserSigningKeysBytes: Array[Byte] = null

  val secondaryTestUserId = Try(UserId.validate(java.util.UUID.randomUUID.toString)).toEither.value
  val secondaryTestUserPassword = java.util.UUID.randomUUID.toString
  var secondaryTestUserSegmentId = 0L
  var secondaryTestUserPrivateDeviceKeyBytes: Array[Byte] = null
  var secondaryTestUserSigningKeysBytes: Array[Byte] = null

  var validGroupId: GroupId = null
  var validDocumentId: DocumentId = null

  val defaultPolicyCaching = new PolicyCachingConfig

  val shortTimeout = Duration.fromMillis(5)
  val defaultTimeout = Duration.fromSecs(30)
  val longTimeout = Duration.fromSecs(30)

  val shortConfig = new IronOxideConfig(defaultPolicyCaching, shortTimeout)
  val defaultConfig = new IronOxideConfig

  var deviceContext: DeviceContext = null
  var secondaryDeviceContext: DeviceContext = null

  var sdk: IronOxide = null

  "Almost all classes" should {
    "implement equals() and hashcode()" in {
      val regex = "(.*).java".r
      val rustSwigExclude = List("InternalPointerMarker", "JNIReachabilityFence")
      // any class that we can't implement equals and hashCode for must be in this list
      val iclExclude = List("AssociationType", "IronOxide", "BlindIndexSearch")
      val currentPath = java.nio.file.Paths.get("").toAbsolutePath.getParent.toString
      val fileFilter = new java.io.FileFilter {
        override def accept(pathname: java.io.File): Boolean =
          pathname.getName.startsWith("ironoxide-java") && pathname.listFiles.map(file => file.getName).contains("out")
      }
      val ironoxideJavaFolder = new java.io.File(s"$currentPath/../target/debug/build/").listFiles(fileFilter)
      assert(
        ironoxideJavaFolder.length > 0,
        "Unable to find Java source files in OUT_DIR"
      )
      assert(
        ironoxideJavaFolder.length < 2,
        "Too many Java source directories found in OUT_DIR. Try running `cargo clean` and re-compiling."
      )
      val javaFiles = new java.io.File(s"${ironoxideJavaFolder.head}/out/java/com/ironcorelabs/sdk").listFiles
      val classNames =
        javaFiles
          .flatMap(file => regex.findFirstMatchIn(file.getName))
          .map(_.group(1))
          .filterNot(rustSwigExclude.contains(_))
          .filterNot(iclExclude.contains(_))
      classNames.length should be > 0
      classNames
        .foreach(className => checkEqualsAndHashDeclared(Class.forName(s"com.ironcorelabs.sdk.$className")))
    }
  }

  "User Create" should {
    "successfully create a new user" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val resp =
        Try(IronOxide.userCreate(jwt, primaryTestUserPassword, new UserCreateOpts(true), defaultTimeout)).toEither
      val createResult = resp.value

      createResult.getUserPublicKey.asBytes should have length 64
      createResult.getNeedsRotation shouldBe true
    }

    "successfully create a 2nd new user" in {
      val jwt = JwtHelper.generateValidJwt(secondaryTestUserId.getId)
      val resp = Try(IronOxide.userCreate(jwt, secondaryTestUserPassword, new UserCreateOpts(true), null)).toEither
      val createResult = resp.value

      createResult.getUserPublicKey.asBytes should have length 64
      createResult.getNeedsRotation shouldBe true
    }

    "fail with short timeout" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val resp =
        Try(IronOxide.userCreate(jwt, primaryTestUserPassword, new UserCreateOpts(true), shortTimeout)).toEither

      resp.isLeft shouldBe true
    }
  }

  "User Verify" should {
    "fails for user that does not exist" in {
      val jwt = JwtHelper.generateValidJwt("not a real user")
      val resp = Try(IronOxide.userVerify(jwt, null)).toEither
      val verifyResult = resp.value

      verifyResult.isPresent shouldBe false
    }

    "successfully verify existing user" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val resp = Try(IronOxide.userVerify(jwt, defaultTimeout)).toEither
      val verifyResult = resp.value

      verifyResult.isPresent shouldBe true
      verifyResult.get.getAccountId shouldBe primaryTestUserId
      verifyResult.get.getSegmentId shouldBe 713
      verifyResult.get.getNeedsRotation shouldBe true
    }
  }

  "User Device Generate" should {
    "fail for bad user password" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val expectedException =
        Try(IronOxide.generateNewDevice(jwt, "BAD PASSWORD", new DeviceCreateOpts, null)).toEither
      expectedException.leftValue.getMessage should include("AesError")
    }

    "succeed for valid user" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val jwt2 = JwtHelper.generateValidJwt(secondaryTestUserId.getId)
      val deviceName = Try(DeviceName.validate("myDevice")).toEither.value
      val newDeviceResult = Try(
        IronOxide.generateNewDevice(jwt, primaryTestUserPassword, new DeviceCreateOpts(deviceName), null)
      ).toEither.value
      val secondDeviceResult = Try(
        IronOxide.generateNewDevice(jwt2, secondaryTestUserPassword, new DeviceCreateOpts(deviceName), null)
      ).toEither.value

      newDeviceResult.getCreated shouldBe newDeviceResult.getLastUpdated
      newDeviceResult.getName.isPresent shouldBe true
      newDeviceResult.getName.get shouldBe deviceName

      //Store off the device component parts as raw values so we can use them to reconstruct
      //a DeviceContext instance to initialize the SDK.
      primaryTestUserSegmentId = newDeviceResult.getSegmentId
      primaryTestUserPrivateDeviceKeyBytes = newDeviceResult.getDevicePrivateKey.asBytes
      primaryTestUserSigningKeysBytes = newDeviceResult.getSigningPrivateKey.asBytes

      secondaryTestUserSegmentId = secondDeviceResult.getSegmentId
      secondaryTestUserPrivateDeviceKeyBytes = secondDeviceResult.getDevicePrivateKey.asBytes
      secondaryTestUserSigningKeysBytes = secondDeviceResult.getSigningPrivateKey.asBytes

      newDeviceResult.getSigningPrivateKey.asBytes should have size 64
      newDeviceResult.getDevicePrivateKey.asBytes should have size 32
      newDeviceResult.getAccountId shouldBe primaryTestUserId

      deviceContext = new DeviceContext(
        primaryTestUserId,
        primaryTestUserSegmentId,
        PrivateKey.validate(primaryTestUserPrivateDeviceKeyBytes),
        DeviceSigningKeyPair.validate(primaryTestUserSigningKeysBytes)
      )

      secondaryDeviceContext = new DeviceContext(
        secondaryTestUserId,
        secondaryTestUserSegmentId,
        PrivateKey.validate(secondaryTestUserPrivateDeviceKeyBytes),
        DeviceSigningKeyPair.validate(secondaryTestUserSigningKeysBytes)
      )

      sdk = IronOxide.initialize(deviceContext, defaultConfig)
      val deviceList = sdk.userListDevices.getResult

      deviceList.length shouldBe 1
      deviceList.head.getId.getId shouldBe a[java.lang.Long]
      deviceList.head.getName.isPresent shouldBe true
      deviceList.head.getName.get shouldBe deviceName
      sdk.clearPolicyCache shouldBe 0
    }
  }

  "Initialize" should {
    "fail with short timeout" in {
      val maybeSdk = Try(IronOxide.initialize(deviceContext, shortConfig)).toEither
      maybeSdk.isLeft shouldBe true
    }
  }

  "DeviceContext" should {
    "Successfully serialize/deserialize as JSON" in {
      val jwt = JwtHelper.generateValidJwt(secondaryTestUserId.getId)
      val deviceName = DeviceName.validate("device")
      val deviceContext =
        new DeviceContext(
          IronOxide.generateNewDevice(jwt, secondaryTestUserPassword, new DeviceCreateOpts(deviceName), null)
        )
      val json = deviceContext.toJsonString
      val accountId = deviceContext.getAccountId.getId
      val segmentId = deviceContext.getSegmentId
      val signingPrivateKeyBase64 = ByteVector.view(deviceContext.getSigningPrivateKey.asBytes).toBase64
      val devicePrivateKeyBase64 = ByteVector.view(deviceContext.getDevicePrivateKey.asBytes).toBase64
      val expectJson =
        s"""{"accountId":"$accountId","segmentId":$segmentId,"signingPrivateKey":"$signingPrivateKeyBase64","devicePrivateKey":"$devicePrivateKeyBase64"}"""
      val result = DeviceContext.fromJsonString(json)

      json shouldBe expectJson
      result.getAccountId.getId shouldBe deviceContext.getAccountId.getId
      result.getDevicePrivateKey.asBytes shouldBe deviceContext.getDevicePrivateKey.asBytes
      result.getSegmentId shouldBe deviceContext.getSegmentId
      result.getSigningPrivateKey.asBytes shouldBe deviceContext.getSigningPrivateKey.asBytes
    }

    "Fail to deserialize invalid json" in {
      val result = Try(DeviceContext.fromJsonString("aaaa")).toEither
      result.leftValue.getMessage shouldBe "jsonString was not a valid JSON representation of a DeviceContext."
    }
  }

  "User Device" should {
    var secondaryDeviceId: DeviceId = null
    var tertiaryDevice: DeviceContext = null

    "List return 3 good devices" in {
      val now = Calendar.getInstance.getTimeInMillis
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)

      // a second device
      Try(IronOxide.generateNewDevice(jwt, primaryTestUserPassword, new DeviceCreateOpts(null), null)).toEither

      // a third device
      val deviceResp2 =
        Try(IronOxide.generateNewDevice(jwt, primaryTestUserPassword, new DeviceCreateOpts, null)).toEither
      val dev3 = new DeviceContext(deviceResp2.value)

      val deviceList = Try(sdk.userListDevices).toEither.value.getResult

      deviceList.length shouldBe 3 // We have the primary device as well as secondary and tertiary devices generated above
      deviceList.head.getId.getId shouldBe a[java.lang.Long]
      deviceList.head.getName.isPresent shouldBe true //Our first device (createDeviceContext) from above does have a name

      // save away the secondary device id so we can delete it later
      secondaryDeviceId = deviceList(1).getId
      // and a tertiary device we can use
      tertiaryDevice = dev3

      // make sure the created timestamp is within the last hour
      val createdTs1 = deviceList(0).getCreated.getTime;
      createdTs1 should be > (now - 3600000)
      createdTs1 should be < (now + 3600000)

      deviceList(0).getLastUpdated shouldBe a[java.util.Date]

      //can only have one current device
      deviceList.map(_.isCurrentDevice).toList shouldBe List(true, false, false)
    }

    "Delete valid device" in {
      val result = Try(sdk.userDeleteDevice(secondaryDeviceId)).toEither

      result.value shouldBe secondaryDeviceId

      // confirm that the second device was actually deleted. First and third devices should remain.
      val deviceList = Try(sdk.userListDevices).toEither.value.getResult
      deviceList.length shouldBe 2
      deviceList should not contain secondaryDeviceId
    }

    "Error for other user's device" in {
      val deviceId = Try(DeviceId.validate(42)).toEither.value
      val result = Try(sdk.userDeleteDevice(deviceId)).toEither

      result.leftValue.getMessage should include("403")
    }

    "Delete current device" in {
      val tertiarySdk = IronOxide.initialize(tertiaryDevice, defaultConfig)
      tertiarySdk.userDeleteDevice(null)

      // Need to use a new SDK object since I just deleted the device of the old one
      // confirm that the third device was deleted. Only primary should remain.
      val deviceList = sdk.userListDevices.getResult
      deviceList.length shouldBe 1
    }
  }

  "User Get PublicKey" should {
    "Return empty for ids that don't exist" in {
      val badUserId = Try(UserId.validate("not-a-user")).toEither.value
      val result = Try(sdk.userGetPublicKey(List(badUserId).toArray)).toEither
      result.value.toList shouldBe Nil
    }

    "Return both for ids that do exist" in {
      val result = Try(sdk.userGetPublicKey(List(primaryTestUserId, secondaryTestUserId).toArray)).toEither

      //Sort the values just to make sure the assertion doesn't fail due to ordering being off.
      result.value.toList
        .map(_.getUser.getId)
        .sorted shouldBe List(primaryTestUserId.getId, secondaryTestUserId.getId).sorted
    }
  }

  "Group Create" should {
    "Create valid group" in {
      val groupName = Try(GroupName.validate("a name")).toEither.value
      val groupCreateResult =
        sdk.groupCreate(new GroupCreateOpts(null, groupName, true, true, null, Array(), Array(), true))

      groupCreateResult.getId.getId.length shouldBe 32 //gooid
      groupCreateResult.getName.get shouldBe groupName
      groupCreateResult.isAdmin shouldBe true
      groupCreateResult.isMember shouldBe true
      groupCreateResult.getCreated should not be null
      groupCreateResult.getLastUpdated shouldBe groupCreateResult.getCreated
      groupCreateResult.getAdminList.getList should have length 1
      groupCreateResult.getAdminList.getList.head shouldBe primaryTestUserId
      groupCreateResult.getMemberList.getList should have length 1
      groupCreateResult.getMemberList.getList.head shouldBe primaryTestUserId
      groupCreateResult.getNeedsRotation.get.getBoolean shouldBe true

      validGroupId = groupCreateResult.getId
    }
    "Create default group" in {
      val groupCreateResult = sdk.groupCreate(new GroupCreateOpts)

      groupCreateResult.getId.getId.length shouldBe 32 //gooid
      groupCreateResult.isAdmin shouldBe true
      groupCreateResult.isMember shouldBe true
      groupCreateResult.getCreated should not be null
      groupCreateResult.getLastUpdated shouldBe groupCreateResult.getCreated
      groupCreateResult.getNeedsRotation.get.getBoolean shouldBe false
    }
    "Create group without members" in {
      val groupName = Try(GroupName.validate("no member")).toEither.value
      val groupCreateResult =
        sdk.groupCreate(
          new GroupCreateOpts(null, groupName, true, false, null, Array(), Array(), false)
        )

      groupCreateResult.getId.getId.length shouldBe 32 //gooid
      groupCreateResult.isAdmin shouldBe true
      groupCreateResult.isMember shouldBe false
      groupCreateResult.getCreated should not be null
      groupCreateResult.getLastUpdated shouldBe groupCreateResult.getCreated
      groupCreateResult.getAdminList.getList should have length 1
      groupCreateResult.getAdminList.getList.head shouldBe primaryTestUserId
      groupCreateResult.getMemberList.getList should have length 0
      groupCreateResult.getNeedsRotation.get.getBoolean shouldBe false
    }
  }

  "Group Private Key Rotate" should {
    "Successfully rotate a valid group" in {
      val rotateResult = sdk.groupRotatePrivateKey(validGroupId)
      rotateResult.getNeedsRotation shouldBe false
      rotateResult.getId shouldBe validGroupId
    }
    "Fail for non-admin" in {
      val sdk = IronOxide.initialize(secondaryDeviceContext, defaultConfig)
      val rotateResult = Try(sdk.groupRotatePrivateKey(validGroupId)).toEither
      rotateResult.isLeft shouldBe true
    }
    "Fail for invalid group" in {
      val rotateResult = Try(sdk.groupRotatePrivateKey(GroupId.validate("7584"))).toEither
      rotateResult.isLeft shouldBe true
    }
  }

  "Group List" should {
    "Return previously created group" in {
      val groupResult = sdk.groupList.getResult

      groupResult.length shouldBe 3
      groupResult.head.getId.getId.length shouldBe 32 //gooid
      groupResult.head.getName.get.getName shouldBe "a name"
      groupResult.head.isAdmin shouldBe true
      groupResult.head.isMember shouldBe true
      groupResult.head.getCreated should not be null
      groupResult.head.getLastUpdated should be > groupResult.head.getCreated
    }
  }

  "Group Get Metadata" should {
    "Return an error when retrieving a group that doesn't exist" in {
      val groupId = Try(GroupId.validate("not-a-group=ID-that-exists=")).toEither.value
      val resp = Try(sdk.groupGetMetadata(groupId)).toEither
      resp.leftValue.getMessage should include("404")
      resp.leftValue.getMessage should include("Requested resource was not found")
    }

    "Succeed for valid group ID" in {
      val resp = Try(sdk.groupGetMetadata(validGroupId)).toEither
      val group = resp.value

      group.getId.getId.length shouldBe 32
      group.getId shouldBe validGroupId
      group.getName.get.getName shouldBe "a name"
      group.getGroupMasterPublicKey.asBytes should have length 64
      group.isAdmin shouldBe true
      group.isMember shouldBe true
      group.getCreated should not be null
      group.getLastUpdated should be > group.getCreated
      group.getAdminList.isPresent shouldBe true
      group.getMemberList.isPresent shouldBe true
      group.getAdminList.get.getList should have length 1
      group.getAdminList.get.getList.head shouldBe primaryTestUserId
      group.getMemberList.get.getList should have length 1
      group.getMemberList.get.getList.head shouldBe primaryTestUserId
      group.getNeedsRotation.get.getBoolean shouldBe false
    }

    "succeed for a non-member" in {
      val nonMemberSdk = IronOxide.initialize(secondaryDeviceContext, defaultConfig)
      val resp = Try(nonMemberSdk.groupGetMetadata(validGroupId)).toEither
      val group = resp.value

      group.getId.getId.length shouldBe 32
      group.getId shouldBe validGroupId
      group.getName.get.getName shouldBe "a name"
      group.getGroupMasterPublicKey.asBytes should have length 64
      group.isAdmin shouldBe false
      group.isMember shouldBe false
      group.getCreated should not be null
      group.getNeedsRotation.isPresent shouldBe false
      group.getLastUpdated should be > group.getCreated
      group.getAdminList.isPresent shouldBe false
      group.getMemberList.isPresent shouldBe false
    }

    "provide public key to users out of the group" in {
      val jwt = JwtHelper.generateValidJwt(secondaryTestUserId.getId)
      val secondaryUserDevice =
        Try(IronOxide.generateNewDevice(jwt, secondaryTestUserPassword, new DeviceCreateOpts, null)).toEither.value

      val sdk = IronOxide.initialize(secondaryDeviceContext, defaultConfig)
      val resp = Try(sdk.groupGetMetadata(validGroupId)).toEither
      val group = resp.value

      group.getId.getId.length shouldBe 32
      group.getId shouldBe validGroupId
      group.getName.get.getName shouldBe "a name"
      group.getGroupMasterPublicKey.asBytes should have length 64
      group.isAdmin shouldBe false
      group.isMember shouldBe false
      group.getCreated should not be null
      group.getLastUpdated should be > group.getCreated
      group.getAdminList.isPresent shouldBe false
      group.getMemberList.isPresent shouldBe false
    }
  }

  "Group update name" should {
    "change name of group" in {
      val newGroupName = Try(GroupName.validate("new name")).toEither.value

      val updateResp = Try(sdk.groupUpdateName(validGroupId, newGroupName)).toEither
      val updatedGroup = updateResp.value

      updatedGroup.getId shouldBe validGroupId
      updatedGroup.getName.get shouldBe newGroupName
      updatedGroup.getCreated should not be null
      updatedGroup.isAdmin shouldBe true
      updatedGroup.isMember shouldBe true
      updatedGroup.getLastUpdated should not be null
      updatedGroup.getLastUpdated should not be updatedGroup.getCreated
    }

    "clear out the group name" in {
      val clearResp = Try(sdk.groupUpdateName(validGroupId, null)).toEither
      val clearedGroup = clearResp.value

      clearedGroup.getName.isPresent shouldBe false
    }
  }

  "Group remove member" should {
    "remove current user from group and fail for unknown user" in {
      val randomUser = Try(UserId.validate("not-a-real-user")).toEither.value
      val removeMemberResp =
        Try(sdk.groupRemoveMembers(validGroupId, List(primaryTestUserId, randomUser).toArray)).toEither

      val removeMember = removeMemberResp.value

      removeMember.getSucceeded.toList should have length 1
      removeMember.getSucceeded.toList.head shouldBe primaryTestUserId
      removeMember.getFailed.toList should have length 1
      removeMember.getFailed.toList.head.getUser shouldBe randomUser
      removeMember.getFailed.toList.head.getError should include(randomUser.getId)
    }
  }

  "Group add member" should {
    "succeed and add user back to group" in {
      val addMemberResp =
        Try(sdk.groupAddMembers(validGroupId, List(primaryTestUserId, secondaryTestUserId).toArray)).toEither

      val addMember = addMemberResp.value
      addMember.getFailed.toList should have length 0
      addMember.getSucceeded.toList should have length 2
      addMember.getSucceeded.toList.head shouldBe primaryTestUserId
    }

    "fail to add a user who is already in the group" in {
      val addMemberResp = Try(sdk.groupAddMembers(validGroupId, List(primaryTestUserId).toArray)).toEither

      val addMember = addMemberResp.value

      addMember.getFailed.toList should have length 1
      addMember.getSucceeded.toList should have length 0
    }
  }

  // Now that the secondary user has been added as a member, re-verify the metadata it gets back
  "Group Get Metadata" should {
    "succeed for a member" in {
      val memberSdk = IronOxide.initialize(secondaryDeviceContext, defaultConfig)
      val resp = Try(memberSdk.groupGetMetadata(validGroupId)).toEither
      val group = resp.value

      group.getId.getId.length shouldBe 32
      group.getId shouldBe validGroupId
      group.getName.isPresent shouldBe false
      group.getGroupMasterPublicKey.asBytes should have length 64
      group.isAdmin shouldBe false
      group.isMember shouldBe true
      group.getCreated should not be null
      group.getLastUpdated should not be null
      group.getAdminList.isPresent shouldBe true
      group.getMemberList.isPresent shouldBe true
      group.getAdminList.get.getList should have length 1
      group.getAdminList.get.getList.head shouldBe primaryTestUserId
      group.getMemberList.get.getList should have length 2
      group.getMemberList.get.getList.head shouldBe primaryTestUserId
      group.getNeedsRotation.isPresent shouldBe false
    }
  }

  "Group add admin" should {
    "succeed and add secondary user as an admin" in {
      val addAdminResp = Try(sdk.groupAddAdmins(validGroupId, List(secondaryTestUserId).toArray)).toEither

      val addMember = addAdminResp.value
      addMember.getFailed.toList should have length 0
      addMember.getSucceeded.toList should have length 1
      addMember.getSucceeded.toList.head shouldBe secondaryTestUserId
    }

    "fail to add a user who is already in an admin of the group" in {
      val addAdminsResp = Try(sdk.groupAddMembers(validGroupId, List(primaryTestUserId).toArray)).toEither

      val addMember = addAdminsResp.value
      addMember.getFailed.toList should have length 1
      addMember.getSucceeded.toList should have length 0
    }
  }

  "Group remove admin" should {
    "Succeed at removing a secondary user" in {
      val removeMemberResp = Try(sdk.groupRemoveAdmins(validGroupId, List(secondaryTestUserId).toArray)).toEither

      val addMember = removeMemberResp.value
      addMember.getFailed.toList should have length 0
      addMember.getSucceeded.toList should have length 1
      addMember.getSucceeded.toList.head shouldBe secondaryTestUserId
    }
  }

  "Encrypted search" should {
    "tokenize a query correctly" in {
      val encryptedBlindIndexSalt = sdk.createBlindIndex(validGroupId)
      val encryptedBlindIndexSalt2 =
        new EncryptedBlindIndexSalt(
          encryptedBlindIndexSalt.getEncryptedDeks,
          encryptedBlindIndexSalt.getEncryptedSaltBytes
        )
      encryptedBlindIndexSalt shouldBe encryptedBlindIndexSalt2
      val blindIndexSearch = encryptedBlindIndexSalt.initializeSearch(sdk)
      val queryResult1 = blindIndexSearch.tokenizeQuery("ironcore labs", "").toList
      val dataResult = blindIndexSearch.tokenizeData("ironcore labs", "").toList
      val queryResult2 = blindIndexSearch.tokenizeQuery("ironcore labs", "red").toList

      dataResult should contain allElementsOf queryResult1
      dataResult.length should be > 8
      queryResult1.length shouldBe 8
      queryResult2.length shouldBe 8
      queryResult1 should not be queryResult2
    }
  }

  "Document encrypt/decrypt" should {
    "succeed for good name and data" in {
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val docName = Try(DocumentName.validate("name")).toEither.value
      val maybeResult =
        Try(sdk.documentEncrypt(data, new DocumentEncryptOpts(null, docName, true, Array(), Array(), null))).toEither
      val result = maybeResult.value

      result.getName.get shouldBe docName
      result.getId.getId.length shouldBe 32
    }

    "roundtrip for single level transform for no name and good data" in {
      val data: Array[Byte] = List(10, 2, 3).map(_.toByte).toArray
      val maybeResult = Try(sdk.documentEncrypt(data, new DocumentEncryptOpts)).toEither
      val result = maybeResult.value

      result.getId.getId.length shouldBe 32
      result.getName.isPresent shouldBe false

      validDocumentId = result.getId

      //Now try to decrypt
      val maybeDecrypt = Try(sdk.documentDecrypt(result.getEncryptedData)).toEither

      val decryptedResult = maybeDecrypt.value

      decryptedResult.getId.getId.length shouldBe 32
      decryptedResult.getName.isPresent shouldBe false
      decryptedResult.getDecryptedData shouldBe data
      decryptedResult.getCreated shouldBe result.getCreated
      decryptedResult.getLastUpdated shouldBe result.getLastUpdated
    }

    "grant to specified users" in {
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val maybeResult = Try(
        sdk.documentEncrypt(
          data,
          new DocumentEncryptOpts(null, null, true, Array(secondaryTestUserId), Array(), null)
        )
      ).toEither
      val result = maybeResult.value
      result.getChanged.getUsers should have length 2
      result.getChanged.getUsers.head.getId shouldEqual primaryTestUserId.getId
      result.getChanged.getUsers()(1).getId shouldEqual secondaryTestUserId.getId
      result.getChanged.getGroups should have length 0
    }

    "grant to specified groups and INTERNAL/PII policy" in {
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val maybeResult = Try(
        sdk.documentEncrypt(
          data,
          new DocumentEncryptOpts(
            null,
            null,
            true,
            Array(),
            Array(validGroupId),
            new PolicyGrant(Category.validate("PII"), Sensitivity.validate("INTERNAL"), null, null)
          )
        )
      ).toEither
      val result = maybeResult.value

      result.getChanged.getUsers should have length 1
      result.getChanged.getUsers.head.getId shouldEqual primaryTestUserId.getId
      result.getChanged.getGroups should have length 1
      result.getChanged.getGroups.head.getId shouldEqual validGroupId.getId
      result.getErrors.getUsers should have length 1
      result.getErrors.getUsers.head.getId.getId shouldBe "baduserid_frompolicy"
      result.getErrors.getGroups should have length 2
      result.getErrors.getGroups.head.getId.getId shouldBe "badgroupid_frompolicy"
      result.getErrors.getGroups.tail.head.getId.getId shouldBe s"data_recovery_${primaryTestUserId.getId}"
    }

    "clear an empty policy" in {
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      // this ID matches the policy's group of "data_recovery_%LOGGED_IN_USER%"
      val id = GroupId.validate(s"data_recovery_${primaryTestUserId.getId}")
      val group = sdk.groupCreate(
        new GroupCreateOpts(id, null, true, true, null, Array(), Array(), false)
      )
      val result = Try(
        sdk.documentEncrypt(
          data,
          new DocumentEncryptOpts(
            null,
            null,
            true,
            Array(),
            Array(),
            new PolicyGrant()
          )
        )
      ).toEither.value
      sdk.groupDelete(id) // don't want to disrupt other policy tests by leaving this group
      sdk.clearPolicyCache shouldBe 1
      result.getChanged.getUsers.length shouldBe 1
    }

    "return failures for bad users and groups" in {
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val notAUser = Try(UserId.validate(java.util.UUID.randomUUID.toString)).toEither.value
      val notAGroup = Try(GroupId.validate(java.util.UUID.randomUUID.toString)).toEither.value
      val maybeResult = Try(
        sdk.documentEncrypt(data, new DocumentEncryptOpts(null, null, true, Array(notAUser), Array(notAGroup), null))
      ).toEither
      val result = maybeResult.value

      // what was valid should go through
      result.getChanged.getUsers should have length 1
      result.getChanged.getUsers.head.getId shouldBe primaryTestUserId.getId
      result.getChanged.getGroups should have length 0

      // the invalid stuff should have errored
      result.getErrors.getUsers should have length 1
      result.getErrors.getUsers.head.getId.getId shouldBe notAUser.getId
      result.getErrors.getUsers.head.getErr shouldBe "User could not be found"
      result.getErrors.getGroups should have length 1
      result.getErrors.getGroups.head.getId.getId shouldBe notAGroup.getId
      result.getErrors.getGroups.head.getErr shouldBe "Group could not be found"
    }
  }

  "User Private Key Rotation" should {
    "successfully rotate a private key for good password" in {
      val originalPublicKey = sdk.userGetPublicKey(Array(primaryTestUserId))(0).getPublicKey.asBytes
      val data: Array[Byte] = List(10, 2, 3).map(_.toByte).toArray
      val encryptResult = Try(sdk.documentEncrypt(data, new DocumentEncryptOpts)).toEither.value
      val decryptResult = Try(sdk.documentDecrypt(encryptResult.getEncryptedData)).toEither.value
      val rotateResult = Try(sdk.userRotatePrivateKey(primaryTestUserPassword)).toEither.value
      val rotatedPublicKey = sdk.userGetPublicKey(Array(primaryTestUserId))(0).getPublicKey.asBytes
      val rotatedDecryptResult = Try(sdk.documentDecrypt(encryptResult.getEncryptedData)).toEither.value

      rotatedPublicKey shouldBe originalPublicKey
      rotateResult.getNeedsRotation shouldBe false
      rotatedDecryptResult.getDecryptedData shouldBe decryptResult.getDecryptedData
      rotatedDecryptResult.getId shouldBe decryptResult.getId
      rotatedDecryptResult.getName shouldBe decryptResult.getName
    }
    "create a new device after rotation" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val deviceName = Try(DeviceName.validate("newdevice")).toEither.value
      val newDeviceResult = Try(
        IronOxide.generateNewDevice(jwt, primaryTestUserPassword, new DeviceCreateOpts(deviceName), null)
      ).toEither.value
      newDeviceResult.getAccountId.getId shouldBe primaryTestUserId.getId
    }
    "fail for wrong password" in {
      val maybeRotationResult = Try(sdk.userRotatePrivateKey("wrong password")).toEither

      maybeRotationResult.leftValue.getMessage should include("AesError")
    }
    "rotate with initializeAndRotate for second user" in {
      val sdk = IronOxide.initialize(secondaryDeviceContext, defaultConfig)
      val groupName = Try(GroupName.validate("a name")).toEither.value
      val groupCreateResult =
        sdk.groupCreate(new GroupCreateOpts(null, groupName, true, true, null, Array(), Array(), true))
      val originalPublicKey = sdk.userGetPublicKey(Array(secondaryTestUserId))(0).getPublicKey.asBytes
      val data: Array[Byte] = List(3, 1, 4).map(_.toByte).toArray
      val encryptResult = Try(sdk.documentEncrypt(data, new DocumentEncryptOpts)).toEither.value
      val decryptResult = Try(sdk.documentDecrypt(encryptResult.getEncryptedData)).toEither.value
      // try to rotate everything within 5ms and fail
      val failedSdk = Try(
        IronOxide
          .initializeAndRotate(secondaryDeviceContext, secondaryTestUserPassword, defaultConfig, shortTimeout)
      ).toEither
      failedSdk.isLeft shouldBe true
      // rotate the private key using initializeAndRotate, but ignore the duplicate sdk returned
      IronOxide.initializeAndRotate(secondaryDeviceContext, secondaryTestUserPassword, defaultConfig, longTimeout)
      val rotatedPublicKey = sdk.userGetPublicKey(Array(secondaryTestUserId))(0).getPublicKey.asBytes
      val rotatedDecryptResult = Try(sdk.documentDecrypt(encryptResult.getEncryptedData)).toEither.value
      val groupGetResult = sdk.groupGetMetadata(groupCreateResult.getId)

      // need to call user verify to check the needsRotation
      val jwt = JwtHelper.generateValidJwt(secondaryTestUserId.getId)
      val resp = Try(IronOxide.userVerify(jwt, null)).toEither.value.get

      rotatedPublicKey shouldBe originalPublicKey
      resp.getNeedsRotation shouldBe false
      rotatedDecryptResult.getDecryptedData shouldBe decryptResult.getDecryptedData
      rotatedDecryptResult.getId shouldBe decryptResult.getId
      rotatedDecryptResult.getName shouldBe decryptResult.getName
      groupGetResult.getNeedsRotation.get.getBoolean shouldBe false
    }

  }

  "Document unmanaged encrypt/decrypt" should {
    "roundtrip through a user" in {
      val data: Array[Byte] = List(10, 2, 3).map(_.toByte).toArray
      val maybeResult = Try(sdk.advancedDocumentEncryptUnmanaged(data, new DocumentEncryptOpts)).toEither
      val result = maybeResult.value

      result.getId.getId.length shouldBe 32

      val maybeDecrypt =
        Try(sdk.advancedDocumentDecryptUnmanaged(result.getEncryptedData, result.getEncryptedDeks)).toEither
      val decryptedResult = maybeDecrypt.value

      decryptedResult.getId.getId shouldBe result.getId.getId
      decryptedResult.getDecryptedData shouldBe data
      decryptedResult.getAccessViaUserOrGroup.getId shouldBe primaryTestUserId.getId
      decryptedResult.getAccessViaUserOrGroup.isUser shouldBe true
      decryptedResult.getAccessViaUserOrGroup.isGroup shouldBe false
    }

    "roundtrip through a group" in {
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val maybeResult = Try(
        sdk.advancedDocumentEncryptUnmanaged(
          data,
          new DocumentEncryptOpts(null, null, false, Array(), Array(validGroupId), null)
        )
      ).toEither
      val result = maybeResult.value
      result.getChanged.getGroups should have length 1
      result.getChanged.getGroups.head.getId shouldEqual validGroupId.getId
      result.getEncryptedDeks.isEmpty shouldBe false

      val maybeDecrypt =
        Try(sdk.advancedDocumentDecryptUnmanaged(result.getEncryptedData, result.getEncryptedDeks)).toEither
      val decryptedResult = maybeDecrypt.value

      decryptedResult.getId.getId shouldBe result.getId.getId
      decryptedResult.getDecryptedData shouldBe data
      decryptedResult.getAccessViaUserOrGroup.getId shouldBe validGroupId.getId
      decryptedResult.getAccessViaUserOrGroup.isUser shouldBe false
      decryptedResult.getAccessViaUserOrGroup.isGroup shouldBe true
    }

    "grant to specified users" in {
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val maybeResult = Try(
        sdk.advancedDocumentEncryptUnmanaged(
          data,
          new DocumentEncryptOpts(null, null, true, Array(secondaryTestUserId), Array(), null)
        )
      ).toEither
      val result = maybeResult.value
      result.getChanged.getUsers should have length 2
      result.getChanged.getUsers.head.getId shouldEqual primaryTestUserId.getId
      result.getChanged.getUsers()(1).getId shouldEqual secondaryTestUserId.getId
      result.getChanged.getGroups should have length 0
      result.getEncryptedDeks.isEmpty shouldBe false
    }

    "grant to specified groups and INTERNAL/PII policy" in {
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val maybeResult = Try(
        sdk.advancedDocumentEncryptUnmanaged(
          data,
          new DocumentEncryptOpts(
            null,
            null,
            true,
            Array(),
            Array(validGroupId),
            new PolicyGrant(Category.validate("PII"), Sensitivity.validate("INTERNAL"), null, null)
          )
        )
      ).toEither
      val result = maybeResult.value
      result.getChanged.getUsers should have length 1
      result.getChanged.getUsers.head.getId shouldEqual primaryTestUserId.getId
      result.getChanged.getGroups should have length 1
      result.getChanged.getGroups.head.getId shouldEqual validGroupId.getId
      result.getErrors.getUsers should have length 1
      result.getErrors.getUsers.head.getId.getId shouldBe "baduserid_frompolicy"
      result.getErrors.getGroups should have length 2
      result.getErrors.getGroups.head.getId.getId shouldBe "badgroupid_frompolicy"
      result.getErrors.getGroups.tail.head.getId.getId shouldBe s"data_recovery_${primaryTestUserId.getId}"
      result.getEncryptedDeks.isEmpty shouldBe false
    }

    "return failures for bad users and groups" in {
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val notAUser = Try(UserId.validate(java.util.UUID.randomUUID.toString)).toEither.value
      val notAGroup = Try(GroupId.validate(java.util.UUID.randomUUID.toString)).toEither.value
      val maybeResult = Try(
        sdk.advancedDocumentEncryptUnmanaged(
          data,
          new DocumentEncryptOpts(null, null, true, Array(notAUser), Array(notAGroup), null)
        )
      ).toEither
      val result = maybeResult.value

      // what was valid should go through
      result.getChanged.getUsers should have length 1
      result.getChanged.getUsers.head.getId shouldBe primaryTestUserId.getId
      result.getChanged.getGroups should have length 0
      result.getEncryptedDeks.isEmpty shouldBe false

      // the invalid stuff should have errored
      result.getErrors.getUsers should have length 1
      result.getErrors.getUsers.head.getId.getId shouldBe notAUser.getId
      result.getErrors.getUsers.head.getErr shouldBe "User could not be found"
      result.getErrors.getGroups should have length 1
      result.getErrors.getGroups.head.getId.getId shouldBe notAGroup.getId
      result.getErrors.getGroups.head.getErr shouldBe "Group could not be found"
    }
  }

  "Document update name" should {
    "successfully update to new name" in {
      val newDocName = Try(DocumentName.validate("new name")).toEither.value

      val maybeUpdate = Try(sdk.documentUpdateName(validDocumentId, newDocName)).toEither

      val result = maybeUpdate.value

      result.getName.isPresent shouldBe true
      result.getName.get shouldBe newDocName
    }

    "successfully clear name" in {
      val maybeUpdate = Try(sdk.documentUpdateName(validDocumentId, null)).toEither

      val result = maybeUpdate.value

      result.getName.isPresent shouldBe false
    }
  }

  "Document List" should {
    "Return previously created documents" in {
      sdk.documentList.getResult should have length 7
    }
  }

  "Document Get Metadata" should {
    "Return an error when retrieving a document that doesn't exist" in {
      val docID = Try(DocumentId.validate("not-a-document-ID-that-exists=/")).toEither.value
      val resp = Try(sdk.documentGetMetadata(docID)).toEither
      resp.leftValue.getMessage should include("404")
      resp.leftValue.getMessage should include("Requested resource was not found")
    }

    "Return expected details about document" in {
      val doc = Try(sdk.documentGetMetadata(validDocumentId)).toEither.value
      // we don't have equals and hashCode on our foreign_enums, so we'll make the call twice
      // and make sure that the inherited implementation compares value correctly
      val doc2 = Try(sdk.documentGetMetadata(validDocumentId)).toEither.value

      doc.getId shouldBe validDocumentId
      doc.getName.isPresent shouldBe false
      doc.getAssociationType shouldBe AssociationType.Owner
      doc.getAssociationType shouldBe doc2.getAssociationType
      doc.getAssociationType.hashCode shouldBe doc2.getAssociationType.hashCode
      doc.getVisibleToUsers should have length 1
      doc.getVisibleToUsers.head.getId shouldBe primaryTestUserId
      doc.getVisibleToGroups should have length 0
    }
    "Return details when encrypted to group" in {
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val groupCreate = sdk.groupCreate(new GroupCreateOpts(null, null, true, true, null, Array(), Array(), false))
      val encryptResult =
        sdk.documentEncrypt(data, new DocumentEncryptOpts(null, null, false, Array(), Array(groupCreate.getId), null))
      val getResult = sdk.documentGetMetadata(encryptResult.getId)
      sdk.groupDelete(groupCreate.getId)

      getResult.getVisibleToGroups.map(_.getId) shouldBe Array(groupCreate.getId)
      getResult.getId shouldBe encryptResult.getId
    }
  }

  "Document update bytes" should {
    "Update encrypted bytes with existing AES key but still be decryptable" in {
      val newData: Array[Byte] = List(10, 20, 30).map(_.toByte).toArray
      val maybeResult = Try(sdk.documentUpdateBytes(validDocumentId, newData)).toEither
      val updatedDoc = maybeResult.value

      updatedDoc.getId shouldBe validDocumentId

      val maybeDecrypted = Try(sdk.documentDecrypt(updatedDoc.getEncryptedData)).toEither

      val decrypted = maybeDecrypted.value

      updatedDoc.getId shouldBe validDocumentId
      decrypted.getDecryptedData shouldBe newData
    }
  }

  "Document grant access" should {
    "succeed for good doc and user/group and fail for bad user/group" in {
      val badUserId = Try(UserId.validate("bad-user-id")).toEither.value
      val badGroupId = Try(GroupId.validate("bad-group-id")).toEither.value

      val maybeResult = Try(
        sdk.documentGrantAccess(
          validDocumentId,
          List(secondaryTestUserId, badUserId).toArray,
          List(validGroupId, badGroupId).toArray
        )
      ).toEither
      val grantResult = maybeResult.value
      val success = grantResult.getChanged

      success.getUsers should have length 1
      success.getGroups should have length 1
      grantResult.getErrors.isEmpty shouldBe false
      grantResult.getErrors.getUsers should have length 1
      grantResult.getErrors.getGroups should have length 1
    }
  }

  "Document revoke access" should {
    "succeed for good doc and user/group and fail for bad user/group" in {
      val badUserId = Try(UserId.validate("bad-user-id")).toEither.value
      val badGroupId = Try(GroupId.validate("bad-group-id")).toEither.value

      val maybeResult = Try(
        sdk.documentRevokeAccess(
          validDocumentId,
          List(secondaryTestUserId, badUserId).toArray,
          List(validGroupId, badGroupId).toArray
        )
      ).toEither
      val revokeResult = maybeResult.value
      val success = revokeResult.getChanged
      success.getUsers should have length 1
      success.getGroups should have length 1
      revokeResult.getErrors.isEmpty shouldBe false
      revokeResult.getErrors.getUsers should have length 1
      revokeResult.getErrors.getGroups should have length 1
    }
  }

  // group delete needs to be close to the bottom of these tests as previous tests depend on it still being available
  "Group Delete" should {
    "successfully delete valid group" in {
      sdk.groupDelete(validGroupId) shouldBe validGroupId

      sdk.groupList.getResult.length shouldBe 2
    }

    "fail to delete non-existent group" in {
      val badGroupId = Try(GroupId.validate("bad-group-id")).toEither.value
      val resp = Try(sdk.groupDelete(badGroupId)).toEither
      resp.leftValue.getMessage should include("404")
    }
  }
}
