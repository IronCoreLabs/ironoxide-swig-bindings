package ironrust

import java.util.Calendar
import org.scalatest.CancelAfterFailure
import com.ironcorelabs.sdk._
import scala.util.Try
import scodec.bits.ByteVector

class FullIntegrationTest extends DudeSuite with CancelAfterFailure {
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

  "User Create" should {
    "successfully create a new user" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val resp =
        Try(IronOxide.userCreate(jwt, primaryTestUserPassword, UserCreateOpts.create(true), defaultTimeout)).toEither
      val createResult = resp.value

      createResult.equals(createResult) shouldBe true
      createResult.getUserPublicKey.asBytes should have length 64
      createResult.getNeedsRotation shouldBe true
      createResult.getUserPublicKey.equals(createResult.getUserPublicKey) shouldBe true
    }

    "successfully create a 2nd new user" in {
      val jwt = JwtHelper.generateValidJwt(secondaryTestUserId.getId)
      val resp = Try(IronOxide.userCreate(jwt, secondaryTestUserPassword, UserCreateOpts.create(true), null)).toEither
      val createResult = resp.value

      createResult.getUserPublicKey.asBytes should have length 64
      createResult.getNeedsRotation shouldBe true
    }

    "fail with short timeout" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val resp =
        Try(IronOxide.userCreate(jwt, primaryTestUserPassword, UserCreateOpts.create(true), shortTimeout)).toEither

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
      verifyResult.equals(verifyResult) shouldBe true
      verifyResult.get.getAccountId shouldBe primaryTestUserId
      verifyResult.get.getSegmentId shouldBe 2013
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
        IronOxide.generateNewDevice(jwt, primaryTestUserPassword, DeviceCreateOpts.create(deviceName), null)
      ).toEither.value
      val secondDeviceResult = Try(
        IronOxide.generateNewDevice(jwt2, secondaryTestUserPassword, DeviceCreateOpts.create(deviceName), null)
      ).toEither.value

      newDeviceResult.equals(newDeviceResult) shouldBe true
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

      newDeviceResult.getDevicePrivateKey.equals(newDeviceResult.getDevicePrivateKey) shouldBe true
      deviceContext.equals(deviceContext) shouldBe true
      deviceList.length shouldBe 1
      deviceList.head.getId.getId shouldBe a[java.lang.Long]
      deviceList.head.getName.isPresent shouldBe true
      deviceList.head.getName.get shouldBe deviceName
    }
  }

  "Class equality" should {
    "work for Category" in {
      val cat1 = Category.validate("test")
      val cat2 = Category.validate("test")
      cat1 shouldBe cat2
      cat1.hashCode shouldBe cat2.hashCode
    }
    "work for DataSubject" in {
      val ds1 = DataSubject.validate("test")
      val ds2 = DataSubject.validate("test")
      ds1 shouldBe ds2
      ds1.hashCode shouldBe ds2.hashCode
    }
    "work for DeviceId" in {
      val id1 = DeviceId.validate(42)
      val id2 = DeviceId.validate(42)
      id1 shouldBe id2
      id1.hashCode shouldBe id2.hashCode
    }
    "work for DeviceName" in {
      val name1 = DeviceName.validate("test")
      val name2 = DeviceName.validate("test")
      name1 shouldBe name2
      name1.hashCode shouldBe name2.hashCode
    }
    "work for DeviceSigningKeyPair" in {
      val key1 = DeviceSigningKeyPair.validate(primaryTestUserSigningKeysBytes)
      val key2 = DeviceSigningKeyPair.validate(primaryTestUserSigningKeysBytes)
      key1 shouldBe key2
      key1.hashCode shouldBe key2.hashCode
    }
    "work for DocumentId" in {
      val id1 = DocumentId.validate("test")
      val id2 = DocumentId.validate("test")
      id1 shouldBe id2
      id1.hashCode shouldBe id2.hashCode
    }
    "work for DocumentName" in {
      val name1 = DocumentName.validate("test")
      val name2 = DocumentName.validate("test")
      name1 shouldBe name2
      name1.hashCode shouldBe name2.hashCode
    }
    "work for Duration" in {
      val d1 = Duration.fromSecs(5)
      val d2 = Duration.fromMillis(5000)
      d1 shouldBe d2
      d1.hashCode shouldBe d2.hashCode
    }
    "work for GroupId" in {
      val id1 = GroupId.validate("test")
      val id2 = GroupId.validate("test")
      id1 shouldBe id2
      id1.hashCode shouldBe id2.hashCode
    }
    "work for GroupName" in {
      val name1 = GroupName.validate("test")
      val name2 = GroupName.validate("test")
      name1 shouldBe name2
      name1.hashCode shouldBe name2.hashCode
    }
    "work for IronOxideConfig" in {
      val config1 = new IronOxideConfig(defaultPolicyCaching, defaultTimeout)
      val config2 = new IronOxideConfig(defaultPolicyCaching, defaultTimeout)
      config1 shouldBe config2
      config1.hashCode shouldBe config2.hashCode
    }
    "work for PolicyCachingConfig" in {
      val policy1 = new PolicyCachingConfig(123)
      val policy2 = new PolicyCachingConfig(123)
      policy1 shouldBe policy2
      policy1.hashCode shouldBe policy2.hashCode
    }
    "work for Sensitivity" in {
      val sen1 = Sensitivity.validate("test")
      val sen2 = Sensitivity.validate("test")
      sen1 shouldBe sen2
      sen1.hashCode shouldBe sen2.hashCode
    }
    "work for UserId" in {
      val id1 = UserId.validate("test")
      val id2 = UserId.validate("test")
      id1 shouldBe id2
      id1.hashCode shouldBe id2.hashCode
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
          IronOxide.generateNewDevice(jwt, secondaryTestUserPassword, DeviceCreateOpts.create(deviceName), null)
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
      Try(IronOxide.generateNewDevice(jwt, primaryTestUserPassword, DeviceCreateOpts.create(null), null)).toEither

      // a third device
      val deviceResp2 =
        Try(IronOxide.generateNewDevice(jwt, primaryTestUserPassword, new DeviceCreateOpts, null)).toEither
      val dev3 = new DeviceContext(deviceResp2.value)

      val deviceListResult = Try(sdk.userListDevices).toEither.value
      val deviceList = deviceListResult.getResult

      deviceListResult.equals(deviceListResult) shouldBe true
      deviceList.equals(deviceList) shouldBe true
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
      val result = Try(sdk.userGetPublicKey(List(primaryTestUserId, secondaryTestUserId).toArray)).toEither.value

      result.head.equals(result.head)
      //Sort the values just to make sure the assertion doesn't fail due to ordering being off.
      result.toList
        .map(_.getUser.getId)
        .sorted shouldBe List(primaryTestUserId.getId, secondaryTestUserId.getId).sorted
    }
  }

  "Group Create" should {
    "Create valid group" in {
      val groupName = Try(GroupName.validate("a name")).toEither.value
      val groupCreateResult =
        sdk.groupCreate(GroupCreateOpts.create(null, groupName, true, true, null, Array(), Array(), true))

      groupCreateResult.equals(groupCreateResult) shouldBe true
      groupCreateResult.getId.getId.length shouldBe 32 //gooid
      groupCreateResult.getName.get shouldBe groupName
      groupCreateResult.isAdmin shouldBe true
      groupCreateResult.isMember shouldBe true
      groupCreateResult.getCreated should not be null
      groupCreateResult.getLastUpdated shouldBe groupCreateResult.getCreated
      groupCreateResult.getAdminList.equals(groupCreateResult.getAdminList)
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
          GroupCreateOpts.create(null, groupName, true, false, null, Array(), Array(), false)
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
      rotateResult.equals(rotateResult) shouldBe true
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

      groupResult.equals(groupResult) shouldBe true
      groupResult.length shouldBe 3
      groupResult.head.getId.getId.length shouldBe 32 //gooid
      groupResult.head.getName.get.getName shouldBe "a name"
      groupResult.head.isAdmin shouldBe true
      groupResult.head.isMember shouldBe true
      groupResult.head.getCreated should not be null
      groupResult.head.getLastUpdated should be > groupResult.head.getCreated
      groupResult.head.equals(groupResult.head) shouldBe true
    }
  }

  "Group Get Metadata" should {
    "Return an error when retrieving a group that doesnt exist" in {
      val groupId = Try(GroupId.validate("not-a-group=ID-that-exists=")).toEither.value
      val resp = Try(sdk.groupGetMetadata(groupId)).toEither
      resp.leftValue.getMessage should include("404")
      resp.leftValue.getMessage should include("Requested resource was not found")
    }

    "Succeed for valid group ID" in {
      val resp = Try(sdk.groupGetMetadata(validGroupId)).toEither
      val group = resp.value

      group.equals(group) shouldBe true
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

      updatedGroup.getNeedsRotation.equals(updatedGroup.getNeedsRotation) shouldBe true
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
      removeMember.getSucceeded.head.equals(removeMember.getSucceeded.head) shouldBe true
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

      addMember.equals(addMember) shouldBe true
      addMember.getFailed.toList should have length 1
      addMember.getFailed.head.equals(addMember.getFailed.head) shouldBe true
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

  "Document encrypt/decrypt" should {
    "succeed for good name and data" in {
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val docName = Try(DocumentName.validate("name")).toEither.value
      val maybeResult =
        Try(sdk.documentEncrypt(data, DocumentEncryptOpts.create(null, docName, true, Array(), Array(), null))).toEither
      val result = maybeResult.value

      result.equals(result) shouldBe true
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

      decryptedResult.equals(decryptedResult) shouldBe true
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
          DocumentEncryptOpts.create(null, null, true, Array(secondaryTestUserId), Array(), null)
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
          DocumentEncryptOpts.create(
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

    "return failures for bad users and groups" in {
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val notAUser = Try(UserId.validate(java.util.UUID.randomUUID.toString)).toEither.value
      val notAGroup = Try(GroupId.validate(java.util.UUID.randomUUID.toString)).toEither.value
      val maybeResult = Try(
        sdk.documentEncrypt(data, DocumentEncryptOpts.create(null, null, true, Array(notAUser), Array(notAGroup), null))
      ).toEither
      val result = maybeResult.value

      // what was valid should go through
      result.getChanged.getUsers should have length 1
      result.getChanged.getUsers.head.getId shouldBe primaryTestUserId.getId
      result.getChanged.getGroups should have length 0

      // the invalid stuff should have errored
      result.getErrors.equals(result.getErrors) shouldBe true
      result.getErrors.getUsers should have length 1
      result.getErrors.getUsers.head.equals(result.getErrors.getUsers.head) shouldBe true
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
      rotateResult.equals(rotateResult) shouldBe true
      rotateResult.getNeedsRotation shouldBe false
      rotatedDecryptResult.getDecryptedData shouldBe decryptResult.getDecryptedData
      rotatedDecryptResult.getId shouldBe decryptResult.getId
      rotatedDecryptResult.getName shouldBe decryptResult.getName
      rotateResult.getUserMasterPrivateKey.equals(rotateResult.getUserMasterPrivateKey) shouldBe true
      rotateResult.equals(rotateResult) shouldBe true
    }
    "create a new device after rotation" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val deviceName = Try(DeviceName.validate("newdevice")).toEither.value
      val newDeviceResult = Try(
        IronOxide.generateNewDevice(jwt, primaryTestUserPassword, DeviceCreateOpts.create(deviceName), null)
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
        sdk.groupCreate(GroupCreateOpts.create(null, groupName, true, true, null, Array(), Array(), true))
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

      result.equals(result) shouldBe true
      result.getId.getId.length shouldBe 32

      val maybeDecrypt =
        Try(sdk.advancedDocumentDecryptUnmanaged(result.getEncryptedData, result.getEncryptedDeks)).toEither
      val decryptedResult = maybeDecrypt.value

      decryptedResult.getAccessViaUserOrGroup.equals(decryptedResult.getAccessViaUserOrGroup)
      decryptedResult.equals(decryptedResult) shouldBe true
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
          DocumentEncryptOpts.create(null, null, false, Array(), Array(validGroupId), null)
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
          DocumentEncryptOpts.create(null, null, true, Array(secondaryTestUserId), Array(), null)
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
          DocumentEncryptOpts.create(
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
          DocumentEncryptOpts.create(null, null, true, Array(notAUser), Array(notAGroup), null)
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
      val listResult = sdk.documentList
      val listMeta = listResult.getResult

      listMeta.equals(listMeta) shouldBe true
      listResult.equals(listResult) shouldBe true
      listMeta should have length 6
    }
  }

  "Document Get Metadata" should {
    "Return an error when retrieving a document that doesnt exist" in {
      val docID = Try(DocumentId.validate("not-a-document-ID-that-exists=/")).toEither.value
      val resp = Try(sdk.documentGetMetadata(docID)).toEither
      resp.leftValue.getMessage should include("404")
      resp.leftValue.getMessage should include("Requested resource was not found")
    }

    "Return expected details about document" in {
      val maybeDoc = Try(sdk.documentGetMetadata(validDocumentId)).toEither

      val doc = maybeDoc.value

      doc.equals(doc) shouldBe true
      doc.getId shouldBe validDocumentId
      doc.getName.isPresent shouldBe false
      doc.getAssociationType shouldBe AssociationType.Owner
      doc.getAssociationType.equals(doc.getAssociationType) shouldBe true
      doc.getVisibleToUsers should have length 1
      doc.getVisibleToUsers.head.equals(doc.getVisibleToUsers.head) shouldBe true
      doc.getVisibleToUsers.head.getId shouldBe primaryTestUserId
      doc.getVisibleToGroups should have length 0
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
      grantResult.equals(grantResult) shouldBe true
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
