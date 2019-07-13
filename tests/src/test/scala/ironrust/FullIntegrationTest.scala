package ironrust

import java.util.Calendar
import org.scalatest.CancelAfterFailure
import com.ironcorelabs.sdk._
import scala.util.Try

class FullIntegrationTest extends DudeSuite with CancelAfterFailure {
  //Generates a random user ID and password to use for this full integration test
  val primaryTestUserId = Try(UserId.validate(java.util.UUID.randomUUID().toString())).toEither.value
  val primaryTestUserPassword = java.util.UUID.randomUUID().toString()
  //Stores record of integration test users device context parts that are then used to initialize the
  //SDK for each test
  var primaryTestUserSegmentId = 0L
  var primaryTestUserPrivateDeviceKeyBytes: Array[Byte] = null
  var primaryTestUserSigningKeysBytes: Array[Byte] = null

  val secondaryTestUserID = Try(UserId.validate(java.util.UUID.randomUUID().toString())).toEither.value
  val secondaryTestUserPassword = java.util.UUID.randomUUID().toString()

  var secondaryUserRecord: UserCreateKeyPair = null

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

  "User Create" should {
    "successfully create a new user" in {
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)
      val resp = Try(IronSdk.userCreate(jwt, primaryTestUserPassword)).toEither
      val createResult = resp.value

      createResult.getUserEncryptedMasterKey should have length 92
      createResult.getUserPublicKey.asBytes should have length 64
    }

    "successfully create a 2nd new user" in {
      val jwt = JwtHelper.generateValidJwt(secondaryTestUserID.getId)
      val resp = Try(IronSdk.userCreate(jwt, secondaryTestUserPassword)).toEither
      val createResult = resp.value

      //Store off the new user we created so it can used for future tests below
      secondaryUserRecord = createResult

      createResult.getUserEncryptedMasterKey should have length 92
      createResult.getUserPublicKey.asBytes should have length 64
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
      val deviceName = Try(DeviceName.validate("myDevice")).toEither.value
      val newDeviceResult = Try(
        IronSdk.generateNewDevice(jwt, primaryTestUserPassword, DeviceCreateOpts.create(deviceName.clone))
      ).toEither.value

      //Store off the device component parts as raw values so we can use them to reconstruct
      //an DeviceContext instance to initialize the SDK.
      primaryTestUserSegmentId = newDeviceResult.getSegmentId
      primaryTestUserPrivateDeviceKeyBytes = newDeviceResult.getPrivateDeviceKey.asBytes
      primaryTestUserSigningKeysBytes = newDeviceResult.getSigningKeys.asBytes

      newDeviceResult.getSigningKeys.asBytes should have size 64
      newDeviceResult.getPrivateDeviceKey.asBytes should have size 32
      newDeviceResult.getAccountId shouldBe primaryTestUserId

      val sdk = IronSdk.initialize(createDeviceContext)
      val deviceList = sdk.userListDevices.getResult
      deviceList.length shouldBe 1
      deviceList.head.getId.getId shouldBe a[java.lang.Long]
      deviceList.head.getName.isPresent shouldBe true
      deviceList.head.getName.get shouldBe deviceName
    }
  }

  "User Device" should {
    var secondaryDeviceId: DeviceId = null
    var tertiaryDevice: DeviceContext = null

    "List return 3 good devices" in {
      val now = Calendar.getInstance.getTimeInMillis
      val jwt = JwtHelper.generateValidJwt(primaryTestUserId.getId)

      // a second device
      val deviceResp =
        Try(IronSdk.generateNewDevice(jwt, primaryTestUserPassword, DeviceCreateOpts.create(null))).toEither

      // a third device
      val deviceResp2 = Try(IronSdk.generateNewDevice(jwt, primaryTestUserPassword, new DeviceCreateOpts())).toEither
      val dev3 = deviceResp2.value

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
      val result = Try(sdk.userGetPublicKey(List(primaryTestUserId, secondaryTestUserID).toArray)).toEither
      //Sort the values just to make sure the assertion doesn't fail due to ordering being off.
      result.value.toList
        .map(_.getUser.getId)
        .sorted shouldBe List(primaryTestUserId.getId, secondaryTestUserID.getId).sorted
    }
  }

  "Group Create" should {
    "Create valid group" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val groupName = Try(GroupName.validate("a name")).toEither.value
      val groupCreateResult = sdk.groupCreate(GroupCreateOpts.create(null, groupName.clone, true))
      groupCreateResult.getId.getId.length shouldBe 32 //gooid
      groupCreateResult.getName.get shouldBe groupName
      groupCreateResult.isAdmin shouldBe true
      groupCreateResult.isMember shouldBe true
      groupCreateResult.getCreated should not be null
      groupCreateResult.getLastUpdated shouldBe groupCreateResult.getCreated

      validGroupId = groupCreateResult.getId
    }
  }

  "Group List" should {
    "Return previously created group" in {
      val sdk = IronSdk.initialize(createDeviceContext)

      val groupResult = sdk.groupList().getResult()

      groupResult.length shouldBe 1

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
      resp.leftValue.getMessage should include(
        """404"""
      )
      resp.leftValue.getMessage should include(
        """Requested resource was not found"""
      )
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
    }

    "provide public key to users out of the group" in {
      val jwt = JwtHelper.generateValidJwt(secondaryTestUserID.getId)
      val secondaryUserDevice =
        Try(IronSdk.generateNewDevice(jwt, secondaryTestUserPassword, new DeviceCreateOpts())).toEither.value

      val sdk = IronSdk.initialize(secondaryUserDevice)
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

      val addMemberResp = Try(sdk.groupAddMembers(validGroupId, List(primaryTestUserId).toArray)).toEither

      val addMember = addMemberResp.value
      addMember.getFailed.toList should have length 0
      addMember.getSucceeded.toList should have length 1
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

  "Group add admin" should {
    "succeed and add secordary user as an admin" in {
      val sdk = IronSdk.initialize(createDeviceContext)

      val addAdminResp = Try(sdk.groupAddAdmins(validGroupId, List(secondaryTestUserID).toArray)).toEither

      val addMember = addAdminResp.value
      addMember.getFailed.toList should have length 0
      addMember.getSucceeded.toList should have length 1
      addMember.getSucceeded.toList.head shouldBe secondaryTestUserID
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

      val removeMemberResp = Try(sdk.groupRemoveAdmins(validGroupId, List(secondaryTestUserID).toArray)).toEither

      val addMember = removeMemberResp.value
      addMember.getFailed.toList should have length 0
      addMember.getSucceeded.toList should have length 1
      addMember.getSucceeded.toList.head shouldBe secondaryTestUserID
    }
  }

  "Document encrypt" should {
    "succeed for good name and data" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val docName = Try(DocumentName.validate("name")).toEither.value
      val maybeResult =
        Try(sdk.documentEncrypt(data, DocumentEncryptOpts.create(null, docName.clone, true, Array(), Array(),null))).toEither
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
        sdk.documentEncrypt(data, DocumentEncryptOpts.create(null, null, true, Array(secondaryTestUserID), Array(),null))
      ).toEither
      val result = maybeResult.value
      result.getChanged.getUsers should have length 2
      result.getChanged.getUsers.head.getId shouldEqual primaryTestUserId.getId
      result.getChanged.getUsers()(1).getId shouldEqual secondaryTestUserID.getId
      result.getChanged.getGroups should have length 0
    }

    "grant to specified groups with empty policy" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val maybeResult = Try(
        sdk.documentEncrypt(data, DocumentEncryptOpts.create(null, null, true, Array(), Array(validGroupId), PolicyGrant.create(Category.validate("PII"), Sensitivity.validate("INTERNAL"), null, null)))
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
        sdk.documentEncrypt(data, DocumentEncryptOpts.create(null, null, true, Array(notAUser), Array(notAGroup),null))
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
      sdk.documentList.getResult should have length 5
    }
  }

  "Document Get Metadata" should {
    "Return an error when retrieving a document that doesnt exist" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val docID = Try(DocumentId.validate("not-a-document-ID-that-exists=/")).toEither.value
      val resp = Try(sdk.documentGetMetadata(docID)).toEither
      resp.leftValue.getMessage should include(
        """404"""
      )
      resp.leftValue.getMessage should include(
        """Requested resource was not found"""
      )
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
          List(secondaryTestUserID, badUserId).toArray,
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
          List(secondaryTestUserID, badUserId).toArray,
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

      sdk.groupList.getResult.length shouldBe 0
    }

    "fail to delete non-existent group" in {
      val sdk = IronSdk.initialize(createDeviceContext)
      val badGroupId = Try(GroupId.validate("bad-group-id")).toEither.value
      val resp = Try(sdk.groupDelete(badGroupId)).toEither
      resp.leftValue.getMessage should include(
        """404"""
      )
    }
  }
}
