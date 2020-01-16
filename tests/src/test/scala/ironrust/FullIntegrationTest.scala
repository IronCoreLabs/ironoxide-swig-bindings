package ironrust

import java.util.Calendar
import org.scalatest.CancelAfterFailure
import com.ironcorelabs.sdk._
import scala.util.Try
import scodec.bits.ByteVector

class FullIntegrationTest extends DudeSuite with CancelAfterFailure {
  //Generates a random user ID and password to use for this full integration test
  val primaryTestUserId = Try(UserId.validate(java.util.UUID.randomUUID().toString())).toEither.value
  val primaryTestUserPassword = java.util.UUID.randomUUID().toString()

  //Stores record of integration test users device context parts that are then used to initialize the
  //SDK for each test
  var primaryTestUserSegmentId = 0L
  var primaryTestUserPrivateDeviceKeyBytes: Array[Byte] = null
  var primaryTestUserSigningKeysBytes: Array[Byte] = null

  val secondaryTestUserId = Try(UserId.validate(java.util.UUID.randomUUID().toString())).toEither.value
  val secondaryTestUserPassword = java.util.UUID.randomUUID().toString()
  var secondaryTestUserSegmentId = 0L
  var secondaryTestUserPrivateDeviceKeyBytes: Array[Byte] = null
  var secondaryTestUserSigningKeysBytes: Array[Byte] = null

  var validGroupId: GroupId = null
  var validDocumentId: DocumentId = null

  /**
    * Convenience function to create a new DeviceContext instance from the stored off components we need. Takes the
    * users account ID, segment ID, private device key bytes, and signing key bytes and returns a new DeviceContext
    * instance. This helps us prove that we can create this class instance from scratch.
    */
  def createDeviceContext = {
    new DeviceContext(
      primaryTestUserId,
      primaryTestUserSegmentId,
      PrivateKey.validate(primaryTestUserPrivateDeviceKeyBytes),
      DeviceSigningKeyPair.validate(primaryTestUserSigningKeysBytes)
    )
  }

  def createSecondaryDeviceContext = {
    new DeviceContext(
      secondaryTestUserId,
      secondaryTestUserSegmentId,
      PrivateKey.validate(secondaryTestUserPrivateDeviceKeyBytes),
      DeviceSigningKeyPair.validate(secondaryTestUserSigningKeysBytes)
    )
  }

  "User Create" should {
    "successfully create a new user" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val resp = Try(IronSdk.userCreate(jwt, primaryTestUserPassword, UserCreateOpts.create(true))).toEither
      val createResult = resp.value

      createResult.getUserPublicKey.asBytes should have length 64
      createResult.getNeedsRotation shouldBe true
    }

    "successfully create a 2nd new user" in {
      val jwt = JwtHelper.generateValidJwt(secondaryTestUserId.getId)
      val resp = Try(IronSdk.userCreate(jwt, secondaryTestUserPassword, UserCreateOpts.create(true))).toEither
      val createResult = resp.value

      createResult.getUserPublicKey.asBytes should have length 64
      createResult.getNeedsRotation shouldBe true
    }
  }

  "User Verify" should {
    "fails for user that does not exist" in {
      val jwt = JwtHelper.generateValidJwt("not a real user")
      val resp = Try(IronSdk.userVerify(jwt)).toEither

      val verifyResult = resp.value

      verifyResult.isPresent shouldBe false
    }

    "successfully verify existing user" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val resp = Try(IronSdk.userVerify(jwt)).toEither

      val verifyResult = resp.value

      verifyResult.isPresent shouldBe true

      verifyResult.get.getAccountId shouldBe primaryTestUserId
      verifyResult.get.getSegmentId shouldBe 2013
      verifyResult.get.getNeedsRotation shouldBe true
    }
  }

  "User Device Generate" should {
    "fail for bad user password" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val expectedException = Try(IronSdk.generateNewDevice(jwt, "BAD PASSWORD", new DeviceCreateOpts())).toEither
      expectedException.leftValue.getMessage should include("AesError")
    }

    "succeed for valid user" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val jwt2 = JwtHelper.generateValidJwt(secondaryTestUserId.getId)
      val deviceName = Try(DeviceName.validate("myDevice")).toEither.value
      val newDeviceResult = Try(
        IronSdk.generateNewDevice(jwt, primaryTestUserPassword, DeviceCreateOpts.create(deviceName.clone))
      ).toEither.value
      val secondDeviceResult = Try(
        IronSdk.generateNewDevice(jwt2, secondaryTestUserPassword, DeviceCreateOpts.create(deviceName.clone))
      ).toEither.value

      newDeviceResult.getCreated shouldBe newDeviceResult.getLastUpdated
      newDeviceResult.getName.isPresent shouldBe true
      newDeviceResult.getName.get shouldBe deviceName

      //Store off the device component parts as raw values so we can use them to reconstruct
      //an DeviceContext instance to initialize the SDK.
      primaryTestUserSegmentId = newDeviceResult.getSegmentId
      primaryTestUserPrivateDeviceKeyBytes = newDeviceResult.getDevicePrivateKey.asBytes
      primaryTestUserSigningKeysBytes = newDeviceResult.getSigningPrivateKey.asBytes

      secondaryTestUserSegmentId = secondDeviceResult.getSegmentId
      secondaryTestUserPrivateDeviceKeyBytes = secondDeviceResult.getDevicePrivateKey.asBytes
      secondaryTestUserSigningKeysBytes = secondDeviceResult.getSigningPrivateKey.asBytes

      newDeviceResult.getSigningPrivateKey.asBytes should have size 64
      newDeviceResult.getDevicePrivateKey.asBytes should have size 32
      newDeviceResult.getAccountId shouldBe primaryTestUserId

      val sdk = IronSdk.initialize(createDeviceContext)
      val deviceList = sdk.userListDevices.getResult
      deviceList.length shouldBe 1
      deviceList.head.getId.getId shouldBe a[java.lang.Long]
      deviceList.head.getName.isPresent shouldBe true
      deviceList.head.getName.get shouldBe deviceName
    }
  }

  "DeviceContext" should {
    "Successfully serialize/deserialize as JSON" in {
      val jwt = JwtHelper.generateValidJwt(secondaryTestUserId.getId)
      val deviceName = DeviceName.validate("device")
      val deviceContext =
        new DeviceContext(
          IronSdk.generateNewDevice(jwt, secondaryTestUserPassword, DeviceCreateOpts.create(deviceName.clone))
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
      Try(IronSdk.generateNewDevice(jwt, primaryTestUserPassword, DeviceCreateOpts.create(null))).toEither

      // a third device
      val deviceResp2 = Try(IronSdk.generateNewDevice(jwt, primaryTestUserPassword, new DeviceCreateOpts())).toEither
      val dev3 = new DeviceContext(deviceResp2.value)

      val sdk = IronSdk.initialize(createDeviceContext)
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
      val sdk = IronSdk.initialize(createDeviceContext)
      val result = Try(sdk.userDeleteDevice(secondaryDeviceId.clone)).toEither

      result.value shouldBe secondaryDeviceId

      // confirm that the second device was actually deleted. First and third devices should remain.
      val deviceList = Try(sdk.userListDevices).toEither.value.getResult
      deviceList.length shouldBe 2
      deviceList should not contain secondaryDeviceId
    }

    "Error for other user's device" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val deviceId = Try(DeviceId.validate(42)).toEither.value
      val result = Try(sdk.userDeleteDevice(deviceId)).toEither

      result.leftValue.getMessage should include("403")
    }

    "Delete current device" in {
      val sdk = IronSdk.initialize(tertiaryDevice)
      sdk.userDeleteDevice(null)

      // Need to use a new SDK object since I just deleted the device of the old one
      val sdk2 = IronSdk.initialize(createDeviceContext)
      // confirm that the third device was deleted. Only primary should remain.
      val deviceList = sdk2.userListDevices().getResult()
      deviceList.length shouldBe 1
    }
  }

  "User Get PublicKey" should {
    "Return empty for ids that don't exist" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val badUserId = Try(UserId.validate("not-a-user")).toEither.value
      val result = Try(sdk.userGetPublicKey(List(badUserId).toArray)).toEither
      result.value.toList shouldBe Nil
    }

    "Return both for ids that do exist" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val result = Try(sdk.userGetPublicKey(List(primaryTestUserId, secondaryTestUserId).toArray)).toEither
      //Sort the values just to make sure the assertion doesn't fail due to ordering being off.
      result.value.toList
        .map(_.getUser.getId)
        .sorted shouldBe List(primaryTestUserId.getId, secondaryTestUserId.getId).sorted
    }
  }

  "Group Create" should {
    "Create valid group" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val groupName = Try(GroupName.validate("a name")).toEither.value
      val groupCreateResult =
        sdk.groupCreate(GroupCreateOpts.create(null, groupName.clone, true, true, null, Array(), Array(), true))

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
      val sdk = IronSdk.initialize(createDeviceContext)
      val groupCreateResult = sdk.groupCreate(new GroupCreateOpts())

      groupCreateResult.getId.getId.length shouldBe 32 //gooid
      groupCreateResult.isAdmin shouldBe true
      groupCreateResult.isMember shouldBe true
      groupCreateResult.getCreated should not be null
      groupCreateResult.getLastUpdated shouldBe groupCreateResult.getCreated
      groupCreateResult.getNeedsRotation.get.getBoolean shouldBe false
    }
    "Create group without members" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val groupName = Try(GroupName.validate("no member")).toEither.value
      val groupCreateResult =
        sdk.groupCreate(
          GroupCreateOpts.create(null, groupName.clone, true, false, null, Array(), Array(), false)
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
      val sdk = IronSdk.initialize(createDeviceContext)
      val rotateResult = sdk.groupRotatePrivateKey(validGroupId)
      rotateResult.getNeedsRotation shouldBe false
      rotateResult.getId shouldBe validGroupId
    }
    "Fail for non-admin" in {
      val sdk = IronSdk.initialize(createSecondaryDeviceContext)
      val rotateResult = Try(sdk.groupRotatePrivateKey(validGroupId)).toEither
      rotateResult.isLeft shouldBe true
    }
    "Fail for invalid group" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val rotateResult = Try(sdk.groupRotatePrivateKey(GroupId.validate("7584"))).toEither
      rotateResult.isLeft shouldBe true
    }
  }

  "Group List" should {
    "Return previously created group" in {
      val sdk = IronSdk.initialize(createDeviceContext)

      val groupResult = sdk.groupList().getResult()

      groupResult.length shouldBe 3

      groupResult.head.getId.getId.length shouldBe 32 //gooid
      groupResult.head.getName.get.getName shouldBe "a name"
      groupResult.head.isAdmin shouldBe true
      groupResult.head.isMember shouldBe true
      groupResult.head.getCreated should not be null
      groupResult.head.getLastUpdated shouldBe groupResult.head.getCreated
    }
  }

  "Group Get Metadata" should {
    "Return an error when retrieving a group that doesnt exist" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val groupId = Try(GroupId.validate("not-a-group=ID-that-exists=")).toEither.value
      val resp = Try(sdk.groupGetMetadata(groupId)).toEither
      resp.leftValue.getMessage should include("404")
      resp.leftValue.getMessage should include("Requested resource was not found")
    }

    "Succeed for valid group ID" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val resp = Try(sdk.groupGetMetadata(validGroupId)).toEither
      val group = resp.value

      group.getId.getId.length shouldBe 32
      group.getId shouldBe validGroupId
      group.getName.get.getName shouldBe "a name"
      group.getGroupMasterPublicKey.asBytes should have length 64
      group.isAdmin shouldBe true
      group.isMember shouldBe true
      group.getCreated should not be null
      group.getLastUpdated shouldBe group.getCreated
      group.getAdminList.isPresent shouldBe true
      group.getMemberList.isPresent shouldBe true
      group.getAdminList.get.getList should have length 1
      group.getAdminList.get.getList.head shouldBe primaryTestUserId
      group.getMemberList.get.getList should have length 1
      group.getMemberList.get.getList.head shouldBe primaryTestUserId
      group.getNeedsRotation.get.getBoolean shouldBe false
    }

    "succeed for a non-member" in {
      val nonMemberSdk = IronSdk.initialize(createSecondaryDeviceContext)
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
      group.getLastUpdated shouldBe group.getCreated
      group.getAdminList.isPresent shouldBe false
      group.getMemberList.isPresent shouldBe false
    }

    "provide public key to users out of the group" in {
      val jwt = JwtHelper.generateValidJwt(secondaryTestUserId.getId)
      val secondaryUserDevice =
        Try(IronSdk.generateNewDevice(jwt, secondaryTestUserPassword, new DeviceCreateOpts())).toEither.value

      val sdk = IronSdk.initialize(new DeviceContext(secondaryUserDevice))
      val resp = Try(sdk.groupGetMetadata(validGroupId)).toEither
      val group = resp.value

      group.getId.getId.length shouldBe 32
      group.getId shouldBe validGroupId
      group.getName.get.getName shouldBe "a name"
      group.getGroupMasterPublicKey.asBytes should have length 64
      group.isAdmin shouldBe false
      group.isMember shouldBe false
      group.getCreated should not be null
      group.getLastUpdated shouldBe group.getCreated
      group.getAdminList.isPresent shouldBe false
      group.getMemberList.isPresent shouldBe false
    }
  }

  "Group update name" should {
    "change name of group" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val newGroupName = Try(GroupName.validate("new name")).toEither.value

      val updateResp = Try(sdk.groupUpdateName(validGroupId, newGroupName.clone)).toEither

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
      val sdk = IronSdk.initialize(createDeviceContext)

      val clearResp = Try(sdk.groupUpdateName(validGroupId, null)).toEither
      val clearedGroup = clearResp.value

      clearedGroup.getName.isPresent shouldBe false
    }
  }

  "Group remove member" should {
    "remove current user from group and fail for unknown user" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val randoUser = Try(UserId.validate("not-a-real-user")).toEither.value
      val removeMemberResp =
        Try(sdk.groupRemoveMembers(validGroupId, List(primaryTestUserId, randoUser).toArray)).toEither

      val removeMember = removeMemberResp.value

      removeMember.getSucceeded.toList should have length 1
      removeMember.getSucceeded.toList.head shouldBe primaryTestUserId
      removeMember.getFailed.toList should have length 1
      removeMember.getFailed.toList.head.getUser shouldBe randoUser
      removeMember.getFailed.toList.head.getError should include(randoUser.getId)
    }
  }

  "Group add member" should {
    "succeed and add user back to group" in {
      val sdk = IronSdk.initialize(createDeviceContext)

      val addMemberResp =
        Try(sdk.groupAddMembers(validGroupId, List(primaryTestUserId, secondaryTestUserId).toArray)).toEither

      val addMember = addMemberResp.value
      addMember.getFailed.toList should have length 0
      addMember.getSucceeded.toList should have length 2
      addMember.getSucceeded.toList.head shouldBe primaryTestUserId
    }

    "fail to add a user who is already in the group" in {
      val sdk = IronSdk.initialize(createDeviceContext)

      val addMemberResp = Try(sdk.groupAddMembers(validGroupId, List(primaryTestUserId).toArray)).toEither

      val addMember = addMemberResp.value
      addMember.getFailed.toList should have length 1
      addMember.getSucceeded.toList should have length 0
    }
  }

  // Now that the secondary user has been added as a member, re-verify the metadata it gets back
  "Group Get Metadata" should {
    "succeed for a member" in {
      val memberSdk = IronSdk.initialize(createSecondaryDeviceContext)
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
      val sdk = IronSdk.initialize(createDeviceContext)

      val addAdminResp = Try(sdk.groupAddAdmins(validGroupId, List(secondaryTestUserId).toArray)).toEither

      val addMember = addAdminResp.value
      addMember.getFailed.toList should have length 0
      addMember.getSucceeded.toList should have length 1
      addMember.getSucceeded.toList.head shouldBe secondaryTestUserId
    }

    "fail to add a user who is already in an admin of the group" in {
      val sdk = IronSdk.initialize(createDeviceContext)

      val addAdminsResp = Try(sdk.groupAddMembers(validGroupId, List(primaryTestUserId).toArray)).toEither

      val addMember = addAdminsResp.value
      addMember.getFailed.toList should have length 1
      addMember.getSucceeded.toList should have length 0
    }
  }

  "Group remove admin" should {
    "Succeed at removing a secondary user" in {
      val sdk = IronSdk.initialize(createDeviceContext)

      val removeMemberResp = Try(sdk.groupRemoveAdmins(validGroupId, List(secondaryTestUserId).toArray)).toEither

      val addMember = removeMemberResp.value
      addMember.getFailed.toList should have length 0
      addMember.getSucceeded.toList should have length 1
      addMember.getSucceeded.toList.head shouldBe secondaryTestUserId
    }
  }

  "Document encrypt/decrypt" should {
    "succeed for good name and data" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val docName = Try(DocumentName.validate("name")).toEither.value
      val maybeResult =
        Try(sdk.documentEncrypt(data, DocumentEncryptOpts.create(null, docName.clone, true, Array(), Array(), null))).toEither
      val result = maybeResult.value
      result.getName.get shouldBe docName
      result.getId.getId.length shouldBe 32
    }

    "roundtrip for single level transform for no name and good data" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val data: Array[Byte] = List(10, 2, 3).map(_.toByte).toArray
      val maybeResult = Try(sdk.documentEncrypt(data, new DocumentEncryptOpts())).toEither
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
      val sdk = IronSdk.initialize(createDeviceContext)
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
      val sdk = IronSdk.initialize(createDeviceContext)
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
      val sdk = IronSdk.initialize(createDeviceContext)
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val notAUser = Try(UserId.validate(java.util.UUID.randomUUID().toString())).toEither.value
      val notAGroup = Try(GroupId.validate(java.util.UUID.randomUUID().toString())).toEither.value
      val maybeResult = Try(
        sdk.documentEncrypt(data, DocumentEncryptOpts.create(null, null, true, Array(notAUser), Array(notAGroup), null))
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
      val sdk = IronSdk.initialize(createDeviceContext)
      val originalPublicKey = sdk.userGetPublicKey(Array(primaryTestUserId))(0).getPublicKey.asBytes
      val data: Array[Byte] = List(10, 2, 3).map(_.toByte).toArray
      val encryptResult = Try(sdk.documentEncrypt(data, new DocumentEncryptOpts())).toEither.value
      val decryptResult = Try(sdk.documentDecrypt(encryptResult.getEncryptedData)).toEither.value
      val rotateResult = Try(sdk.userRotatePrivateKey(primaryTestUserPassword)).toEither.value
      val rotatedPublicKey = sdk.userGetPublicKey(Array(primaryTestUserId))(0).getPublicKey.asBytes
      val rotatedDecryptResult = Try(sdk.documentDecrypt(encryptResult.getEncryptedData)).toEither.value

      rotatedPublicKey shouldBe originalPublicKey
      rotateResult.getNeedsRotation shouldBe false
      rotatedDecryptResult.getDecryptedData shouldBe decryptResult.getDecryptedData
      rotatedDecryptResult.getId shouldBe decryptResult.getId
      rotatedDecryptResult.getName shouldBe decryptResult.getName
      rotateResult.equals(rotateResult) shouldBe true
    }
    "create a new device after rotation" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val deviceName = Try(DeviceName.validate("newdevice")).toEither.value
      val newDeviceResult = Try(
        IronSdk.generateNewDevice(jwt, primaryTestUserPassword, DeviceCreateOpts.create(deviceName.clone))
      ).toEither.value
      newDeviceResult.getAccountId.getId shouldBe primaryTestUserId.getId
    }
    "fail for wrong password" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val maybeRotationResult = Try(sdk.userRotatePrivateKey("wrong password")).toEither

      maybeRotationResult.leftValue.getMessage shouldBe "AesError"
    }
    "rotate with initializeAndRotate for second user" in {
      val sdk = IronSdk.initialize(createSecondaryDeviceContext)
      val groupName = Try(GroupName.validate("a name")).toEither.value
      val groupCreateResult =
        sdk.groupCreate(GroupCreateOpts.create(null, groupName.clone, true, true, null, Array(), Array(), true))
      val originalPublicKey = sdk.userGetPublicKey(Array(secondaryTestUserId))(0).getPublicKey.asBytes
      val data: Array[Byte] = List(3, 1, 4).map(_.toByte).toArray
      val encryptResult = Try(sdk.documentEncrypt(data, new DocumentEncryptOpts())).toEither.value
      val decryptResult = Try(sdk.documentDecrypt(encryptResult.getEncryptedData)).toEither.value
      // rotate the private key using initializeAndRotate, but ignore the duplicate sdk returned
      IronSdk.initializeAndRotate(createSecondaryDeviceContext, secondaryTestUserPassword)
      val rotatedPublicKey = sdk.userGetPublicKey(Array(secondaryTestUserId))(0).getPublicKey.asBytes
      val rotatedDecryptResult = Try(sdk.documentDecrypt(encryptResult.getEncryptedData)).toEither.value
      val groupGetResult = sdk.groupGetMetadata(groupCreateResult.getId)

      // need to call user verify to check the needsRotation
      val jwt = JwtHelper.generateValidJwt(secondaryTestUserId.getId)
      val resp = Try(IronSdk.userVerify(jwt)).toEither.value.get

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
      val sdk = IronSdk.initialize(createDeviceContext)
      val data: Array[Byte] = List(10, 2, 3).map(_.toByte).toArray
      val maybeResult = Try(sdk.advanced.documentEncryptUnmanaged(data, new DocumentEncryptOpts())).toEither
      val result = maybeResult.value
      result.getId.getId.length shouldBe 32

      val maybeDecrypt =
        Try(sdk.advanced.documentDecryptUnmanaged(result.getEncryptedData, result.getEncryptedDeks)).toEither
      val decryptedResult = maybeDecrypt.value

      decryptedResult.getId.getId shouldBe result.getId.getId
      decryptedResult.getDecryptedData shouldBe data
      decryptedResult.getAccessViaUserOrGroup.getId() shouldBe primaryTestUserId.getId()
      decryptedResult.getAccessViaUserOrGroup.isUser() shouldBe true
      decryptedResult.getAccessViaUserOrGroup.isGroup() shouldBe false
    }

    "roundtrip through a group" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val maybeResult = Try(
        sdk.advanced
          .documentEncryptUnmanaged(
            data,
            DocumentEncryptOpts.create(null, null, false, Array(), Array(validGroupId), null)
          )
      ).toEither
      val result = maybeResult.value
      result.getChanged.getGroups should have length 1
      result.getChanged.getGroups.head.getId shouldEqual validGroupId.getId
      result.getEncryptedDeks.isEmpty shouldBe false

      val maybeDecrypt =
        Try(sdk.advanced.documentDecryptUnmanaged(result.getEncryptedData, result.getEncryptedDeks)).toEither
      val decryptedResult = maybeDecrypt.value

      decryptedResult.getId.getId shouldBe result.getId.getId
      decryptedResult.getDecryptedData shouldBe data
      decryptedResult.getAccessViaUserOrGroup.getId() shouldBe validGroupId.getId()
      decryptedResult.getAccessViaUserOrGroup.isUser() shouldBe false
      decryptedResult.getAccessViaUserOrGroup.isGroup() shouldBe true
    }

    "grant to specified users" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val maybeResult = Try(
        sdk.advanced
          .documentEncryptUnmanaged(
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
      val sdk = IronSdk.initialize(createDeviceContext)
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val maybeResult = Try(
        sdk.advanced
          .documentEncryptUnmanaged(
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
      val sdk = IronSdk.initialize(createDeviceContext)
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val notAUser = Try(UserId.validate(java.util.UUID.randomUUID().toString())).toEither.value
      val notAGroup = Try(GroupId.validate(java.util.UUID.randomUUID().toString())).toEither.value
      val maybeResult = Try(
        sdk.advanced
          .documentEncryptUnmanaged(
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
      val sdk = IronSdk.initialize(createDeviceContext)
      val newDocName = Try(DocumentName.validate("new name")).toEither.value

      val maybeUpdate = Try(sdk.documentUpdateName(validDocumentId, newDocName.clone)).toEither

      val result = maybeUpdate.value

      result.getName.isPresent shouldBe true
      result.getName.get shouldBe newDocName
    }

    "successfully clear name" in {
      val sdk = IronSdk.initialize(createDeviceContext)

      val maybeUpdate = Try(sdk.documentUpdateName(validDocumentId, null)).toEither

      val result = maybeUpdate.value

      result.getName.isPresent shouldBe false
    }
  }

  "Document List" should {
    "Return previously created documents" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      sdk.documentList.getResult should have length 6
    }
  }

  "Document Get Metadata" should {
    "Return an error when retrieving a document that doesnt exist" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val docID = Try(DocumentId.validate("not-a-document-ID-that-exists=/")).toEither.value
      val resp = Try(sdk.documentGetMetadata(docID)).toEither
      resp.leftValue.getMessage should include("404")
      resp.leftValue.getMessage should include("Requested resource was not found")
    }

    "Return expected details about document" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val maybeDoc = Try(sdk.documentGetMetadata(validDocumentId)).toEither

      val doc = maybeDoc.value

      doc.getId shouldBe validDocumentId
      doc.getName.isPresent shouldBe false
      doc.getAssociationType shouldBe AssociationType.Owner
      doc.getVisibleToUsers should have length 1
      doc.getVisibleToUsers.head.getId shouldBe primaryTestUserId
      doc.getVisibleToGroups should have length 0
    }
  }

  "Document update bytes" should {
    "Update encrypted bytes with existing AES key but still be decryptable" in {
      val sdk = IronSdk.initialize(createDeviceContext)

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
      val sdk = IronSdk.initialize(createDeviceContext)

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
      val sdk = IronSdk.initialize(createDeviceContext)
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
      val sdk = IronSdk.initialize(createDeviceContext)
      sdk.groupDelete(validGroupId) shouldBe validGroupId

      sdk.groupList.getResult.length shouldBe 2
    }

    "fail to delete non-existent group" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val badGroupId = Try(GroupId.validate("bad-group-id")).toEither.value
      val resp = Try(sdk.groupDelete(badGroupId)).toEither
      resp.leftValue.getMessage should include("404")
    }
  }
}
