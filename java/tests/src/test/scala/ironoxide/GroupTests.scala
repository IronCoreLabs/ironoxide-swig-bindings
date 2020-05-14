package ironoxide

import scala.util.Try
import com.ironcorelabs.sdk._

class GroupTests extends TestSuite {
  "Group Create" should {
    "succeed with valid name" in {
      val groupName = Try(GroupName.validate("a name")).toEither.value
      val groupCreateResult =
        primarySdk.groupCreate(new GroupCreateOpts(null, groupName, true, true, null, Array(), Array(), true))

      groupCreateResult.getId.getId.length shouldBe 32
      groupCreateResult.getName.get shouldBe groupName
      groupCreateResult.isAdmin shouldBe true
      groupCreateResult.isMember shouldBe true
      groupCreateResult.getCreated should not be null
      groupCreateResult.getLastUpdated shouldBe groupCreateResult.getCreated
      groupCreateResult.getAdminList.getList should have length 1
      groupCreateResult.getAdminList.getList.head shouldBe primaryUser
      groupCreateResult.getMemberList.getList should have length 1
      groupCreateResult.getMemberList.getList.head shouldBe primaryUser
      groupCreateResult.getNeedsRotation.get.getBoolean shouldBe true
    }
    "succeed creating default group" in {
      val groupCreateResult = primarySdk.groupCreate(new GroupCreateOpts)

      groupCreateResult.getId.getId.length shouldBe 32
      groupCreateResult.isAdmin shouldBe true
      groupCreateResult.isMember shouldBe true
      groupCreateResult.getCreated should not be null
      groupCreateResult.getLastUpdated shouldBe groupCreateResult.getCreated
      groupCreateResult.getNeedsRotation.get.getBoolean shouldBe false
    }
    "succeed with specific owner" in {
      val opts =
        new GroupCreateOpts(null, null, true, true, secondaryUser, Array(secondaryUser), Array(secondaryUser), true)
      val createResult = Try(primarySdk.groupCreate(opts)).toEither.value
      createResult.getOwner shouldBe secondaryUser
    }
    "fail with invalid user" in {
      val opts =
        new GroupCreateOpts(null, null, true, true, null, Array(UserId.validate("fakeuser4567")), Array(), true)
      val createResult = Try(primarySdk.groupCreate(opts))
      createResult.isFailure shouldBe true
    }
  }

  "Group Get Metadata" should {
    "Return an error when retrieving a group that doesn't exist" in {
      val groupId = Try(GroupId.validate("not-a-group=ID-that-exists=")).toEither.value
      val resp = Try(primarySdk.groupGetMetadata(groupId)).toEither
      resp.leftValue.getMessage should include("404")
      resp.leftValue.getMessage should include("Requested resource was not found")
    }

    "Succeed for valid group ID" in {
      val groupCreate = Try(primarySdk.groupCreate(new GroupCreateOpts)).toEither.value
      val group = Try(primarySdk.groupGetMetadata(groupCreate.getId)).toEither.value

      group.getId.getId.length shouldBe 32
      group.getId shouldBe groupCreate.getId
      group.getName.isEmpty shouldBe true
      group.getGroupMasterPublicKey.asBytes should have length 64
      group.isAdmin shouldBe true
      group.isMember shouldBe true
      group.getCreated should not be null
      group.getLastUpdated shouldBe group.getCreated
      group.getAdminList.isPresent shouldBe true
      group.getMemberList.isPresent shouldBe true
      group.getAdminList.get.getList should have length 1
      group.getAdminList.get.getList.head shouldBe primaryUser
      group.getMemberList.get.getList should have length 1
      group.getMemberList.get.getList.head shouldBe primaryUser
      group.getNeedsRotation.get.getBoolean shouldBe false
    }

    "succeed for a non-member" in {
      val groupCreate = Try(primarySdk.groupCreate(new GroupCreateOpts)).toEither.value
      val group = Try(secondarySdk.groupGetMetadata(groupCreate.getId)).toEither.value

      group.getId.getId.length shouldBe 32
      group.getId shouldBe groupCreate.getId
      group.getName.isEmpty shouldBe true
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
      val groupCreate = Try(primarySdk.groupCreate(new GroupCreateOpts)).toEither.value
      val group = Try(secondarySdk.groupGetMetadata(groupCreate.getId)).toEither.value

      group.getId.getId.length shouldBe 32
      group.getId shouldBe groupCreate.getId
      group.getName.isEmpty shouldBe true
      group.getGroupMasterPublicKey.asBytes should have length 64
      group.isAdmin shouldBe false
      group.isMember shouldBe false
      group.getCreated should not be null
      group.getLastUpdated shouldBe group.getCreated
      group.getAdminList.isPresent shouldBe false
      group.getMemberList.isPresent shouldBe false
    }
  }

  "Group Delete" should {
    "delete a group" in {
      val createResult = Try(primarySdk.groupCreate(new GroupCreateOpts)).toEither.value
      val deleteResult = Try(primarySdk.groupDelete(createResult.getId)).toEither.value
      deleteResult shouldBe createResult.getId
    }
  }

  "Initialize and Rotate" should {
    "rotate a group on init" in {
      val createResult = Try(
        primarySdk.groupCreate(new GroupCreateOpts(null, null, true, true, null, Array(), Array(), true))
      ).toEither.value
      val groupGet1 = Try(primarySdk.groupGetMetadata(createResult.getId)).toEither.value
      groupGet1.getNeedsRotation.get.getBoolean shouldBe true
      val _ = IronOxide.initializeAndRotate(primaryUserDevice, testUsersPassword, new IronOxideConfig, null)
      val groupGet2 = Try(primarySdk.groupGetMetadata(createResult.getId)).toEither.value
      groupGet2.getNeedsRotation.get.getBoolean shouldBe false
    }
  }

  "Group List" should {
    "list the primary user's groups" in {
      primarySdk.groupCreate(new GroupCreateOpts)
      val listResult = Try(primarySdk.groupList).toEither.value.getResult
      listResult.length should be > 0
    }
  }

  "Add and Remove Member" should {
    "add and remove a member from a group" in {
      val groupCreate = Try(primarySdk.groupCreate(new GroupCreateOpts)).toEither.value
      val memberAdd = Try(primarySdk.groupAddMembers(groupCreate.getId, Array(secondaryUser))).toEither.value
      memberAdd.getSucceeded.length shouldBe 1
      memberAdd.getFailed.isEmpty shouldBe true

      val secondaryGet = Try(secondarySdk.groupGetMetadata(groupCreate.getId)).toEither.value
      secondaryGet.isMember shouldBe true
      secondaryGet.isAdmin shouldBe false

      val memberRemove = Try(primarySdk.groupRemoveMembers(groupCreate.getId, Array(secondaryUser))).toEither.value
      memberRemove.getSucceeded.length shouldBe 1
      memberRemove.getFailed.isEmpty shouldBe true
    }
  }

  "Add and Remove Admin" should {
    "add and remove a admin from a group" in {
      val groupCreate = Try(primarySdk.groupCreate(new GroupCreateOpts)).toEither.value
      val adminAdd = Try(primarySdk.groupAddAdmins(groupCreate.getId, Array(secondaryUser))).toEither.value
      adminAdd.getSucceeded.length shouldBe 1
      adminAdd.getFailed.isEmpty shouldBe true

      val secondaryGet = Try(secondarySdk.groupGetMetadata(groupCreate.getId)).toEither.value
      secondaryGet.isMember shouldBe false
      secondaryGet.isAdmin shouldBe true

      val adminRemove = Try(primarySdk.groupRemoveAdmins(groupCreate.getId, Array(secondaryUser))).toEither.value
      adminRemove.getSucceeded.length shouldBe 1
      adminRemove.getFailed.isEmpty shouldBe true
    }
  }

  "Group Private Key Rotation" should {
    "rotate a group's private key" in {
      val groupCreate = Try(
        primarySdk.groupCreate(new GroupCreateOpts(null, null, true, true, null, Array(), Array(), true))
      ).toEither.value
      val rotateResult = Try(primarySdk.groupRotatePrivateKey(groupCreate.getId)).toEither.value
      rotateResult.getNeedsRotation shouldBe false
    }
    "fail for non-admin" in {
      val groupCreate = Try(
        primarySdk.groupCreate(new GroupCreateOpts(null, null, true, true, null, Array(), Array(secondaryUser), true))
      ).toEither.value
      val rotateResult = Try(secondarySdk.groupRotatePrivateKey(groupCreate.getId))
      rotateResult.isFailure shouldBe true
    }
    "Fail for invalid group" in {
      val rotateResult = Try(primarySdk.groupRotatePrivateKey(GroupId.validate("7584")))
      rotateResult.isFailure shouldBe true
    }
  }

  "Group Update Name" should {
    "change a group's name" in {
      val createResult = Try(
        primarySdk.groupCreate(
          new GroupCreateOpts(null, GroupName.validate("original"), true, true, null, Array(), Array(), false)
        )
      ).toEither.value
      createResult.getName.get.getName shouldBe "original"
      val updateResult = Try(primarySdk.groupUpdateName(createResult.getId, GroupName.validate("new"))).toEither.value
      updateResult.getName.get.getName shouldBe "new"
    }
    "clear a group's name" in {
      val createResult = Try(
        primarySdk.groupCreate(
          new GroupCreateOpts(null, GroupName.validate("original"), true, true, null, Array(), Array(), false)
        )
      ).toEither.value
      createResult.getName.get.getName shouldBe "original"
      val updateResult = Try(primarySdk.groupUpdateName(createResult.getId, null)).toEither.value
      updateResult.getName.isEmpty shouldBe true
    }

  }
}
