package ironoxide

import scala.util.Try
import com.ironcorelabs.sdk._
import java.util.Date

class DocumentTests extends TestSuite {
  "Document List" should {
    "return the primary user's documents" in {
      val bytes = Array(1, 2).map(_.toByte)
      val _ = Try(primarySdk.documentEncrypt(bytes, new DocumentEncryptOpts)).toEither.value
      val listResult = Try(primarySdk.documentList()).toEither.value.getResult
      listResult.length should be > 0
    }
  }

  "Document Encrypt/Decrypt" should {
    "roundtrip bytes" in {
      val bytes = Array(2, 3).map(_.toByte)
      val encryptResult = Try(primarySdk.documentEncrypt(bytes, new DocumentEncryptOpts)).toEither.value
      encryptResult.getName.isEmpty shouldBe true
      encryptResult.getErrors.getUsers.isEmpty shouldBe true
      encryptResult.getErrors.getGroups.isEmpty shouldBe true
      encryptResult.getChanged.getUsers.length shouldBe 1
      val createdTime = encryptResult.getCreated().toInstant().toEpochMilli
      val updatedTime = encryptResult.getLastUpdated().toInstant().toEpochMilli
      createdTime shouldBe updatedTime
      createdTime should be > 1698948098310L // Later than arbitrary time on Nov 2, 2023

      val decryptResult = Try(primarySdk.documentDecrypt(encryptResult.getEncryptedData)).toEither.value
      decryptResult.getDecryptedData shouldBe bytes
    }
    "encrypt with policy" in {
      val dc = createUserAndDevice()
      val sdk = Try(IronOxide.initialize(dc, new IronOxideConfig)).toEither.value
      val groupId = GroupId.validate(s"data_recovery_${dc.getAccountId.getId}")
      val groupOpts = new GroupCreateOpts(groupId, null, true, true, null, Array(), Array(), false)
      primarySdk.groupCreate(groupOpts)
      val bytes = Array(1, 2, 3, 4).map(_.toByte)
      val policy = new PolicyGrant(Category.validate("PII"), Sensitivity.validate("INTERNAL"), null, null)
      val documentOpts = new DocumentEncryptOpts(null, null, false, Array(), Array(), policy)
      val encryptResult = Try(sdk.documentEncrypt(bytes, documentOpts)).toEither.value
      encryptResult.getChanged.getGroups shouldBe Array(groupId)
      encryptResult.getChanged.getUsers shouldBe Array(dc.getAccountId)
      encryptResult.getErrors.getGroups.map(_.getId) shouldBe Array(GroupId.validate("badgroupid_frompolicy"))
      encryptResult.getErrors.getUsers.map(_.getId) shouldBe Array(UserId.validate("baduserid_frompolicy"))

      // try with empty policy
      val opts = new DocumentEncryptOpts(null, null, false, Array(), Array(), new PolicyGrant)
      sdk.documentEncrypt(bytes, opts)
      sdk.clearPolicyCache shouldBe 1
    }
    "encrypt to other user" in {
      val opts = new DocumentEncryptOpts(null, null, false, Array(secondaryUser), Array(), null)
      val encryptResult = Try(primarySdk.documentEncrypt(Array(), opts)).toEither.value
      encryptResult.getChanged.getUsers shouldBe Array(secondaryUser)
      encryptResult.getChanged.getGroups.isEmpty shouldBe true
      encryptResult.getErrors.getUsers.isEmpty shouldBe true
      encryptResult.getErrors.getGroups.isEmpty shouldBe true
    }
    "encrypt to nothing" in {
      val opts = new DocumentEncryptOpts(null, null, false, Array(), Array(), null)
      val encryptResult = Try(primarySdk.documentEncrypt(Array(), opts))
      encryptResult.isFailure shouldBe true
    }
    "return failures for bad users and groups" in {
      val data: Array[Byte] = Array(1, 2, 3).map(_.toByte)
      val notAUser = UserId.validate(java.util.UUID.randomUUID.toString)
      val notAGroup = GroupId.validate(java.util.UUID.randomUUID.toString)
      val encryptResult = Try(
        primarySdk.documentEncrypt(
          data,
          new DocumentEncryptOpts(null, null, true, Array(notAUser), Array(notAGroup), null)
        )
      ).toEither.value
      // what was valid should go through
      encryptResult.getChanged.getUsers should have length 1
      encryptResult.getChanged.getUsers.head.getId shouldBe primaryUser.getId
      encryptResult.getChanged.getGroups should have length 0
      // the invalid stuff should have errored
      encryptResult.getErrors.getUsers should have length 1
      encryptResult.getErrors.getUsers.head.getId.getId shouldBe notAUser.getId
      encryptResult.getErrors.getUsers.head.getErr shouldBe "User could not be found"
      encryptResult.getErrors.getGroups should have length 1
      encryptResult.getErrors.getGroups.head.getId.getId shouldBe notAGroup.getId
      encryptResult.getErrors.getGroups.head.getErr shouldBe "Group could not be found"
    }
  }

  "Document Update Name" should {
    "update a document's name" in {
      val originalName = DocumentName.validate("top secret")
      val opts = new DocumentEncryptOpts(null, originalName, true, Array(), Array(), null)
      val encryptResult = Try(primarySdk.documentEncrypt(Array(), opts)).toEither.value
      encryptResult.getName.get shouldBe originalName

      val newName = DocumentName.validate("declassified")
      val updateResult = Try(primarySdk.documentUpdateName(encryptResult.getId, newName)).toEither.value
      updateResult.getName.get shouldBe newName
    }
    "clear a document's name" in {
      val originalName = DocumentName.validate("top secret")
      val opts = new DocumentEncryptOpts(null, originalName, true, Array(), Array(), null)
      val encryptResult = Try(primarySdk.documentEncrypt(Array(), opts)).toEither.value
      encryptResult.getName.get shouldBe originalName

      val updateResult = Try(primarySdk.documentUpdateName(encryptResult.getId, null)).toEither.value
      updateResult.getName.isEmpty shouldBe true
    }
  }

  "Document Update Bytes" should {
    "update a document's bytes" in {
      val bytes = Array(1, 2).map(_.toByte)
      val encryptResult = Try(primarySdk.documentEncrypt(bytes, new DocumentEncryptOpts)).toEither.value
      val newBytes = Array(3, 4).map(_.toByte)
      val updateResult = Try(primarySdk.documentUpdateBytes(encryptResult.getId, newBytes)).toEither.value
      val decryptResult = Try(primarySdk.documentDecrypt(updateResult.getEncryptedData)).toEither.value
      decryptResult.getDecryptedData shouldBe newBytes
    }
  }

  "Document Add and Remove Members" should {
    "add and remove members" in {
      val encryptResult = Try(primarySdk.documentEncrypt(Array(), new DocumentEncryptOpts)).toEither.value
      encryptResult.getChanged.getUsers.length shouldBe 1
      encryptResult.getChanged.getGroups.isEmpty shouldBe true
      val addResult =
        Try(primarySdk.documentGrantAccess(encryptResult.getId, Array(secondaryUser), Array())).toEither.value
      addResult.getChanged.getUsers.length shouldBe 1
      addResult.getChanged.getGroups.isEmpty shouldBe true
      val metadata = Try(primarySdk.documentGetMetadata(encryptResult.getId)).toEither.value
      metadata.getVisibleToUsers.length shouldBe 2
      val removeResult =
        Try(primarySdk.documentRevokeAccess(encryptResult.getId, Array(secondaryUser), Array())).toEither.value
      removeResult.getChanged.getUsers.length shouldBe 1
      removeResult.getChanged.getGroups.isEmpty shouldBe true
      val metadata2 = Try(primarySdk.documentGetMetadata(encryptResult.getId)).toEither.value
      metadata2.getVisibleToUsers.length shouldBe 1
    }
  }

  "Document Encrypt/Decrypt Unmanaged" should {
    "roundtrip to self" in {
      val bytes = Array(1, 2, 3).map(_.toByte)
      val encryptResult =
        Try(primarySdk.documentEncryptUnmanaged(bytes, new DocumentEncryptOpts)).toEither.value
      val decryptResult = Try(
        primarySdk.documentDecryptUnmanaged(encryptResult.getEncryptedData, encryptResult.getEncryptedDeks)
      ).toEither.value
      decryptResult.getDecryptedData shouldBe bytes
    }
    "roundtrip to other" in {
      val bytes = Array(1, 2, 3).map(_.toByte)
      val encryptResult =
        Try(
          primarySdk.documentEncryptUnmanaged(
            bytes,
            new DocumentEncryptOpts(null, null, false, Array(secondaryUser), Array(), null)
          )
        ).toEither.value
      val decryptResult = Try(
        secondarySdk.documentDecryptUnmanaged(encryptResult.getEncryptedData, encryptResult.getEncryptedDeks)
      ).toEither.value
      decryptResult.getDecryptedData shouldBe bytes
    }
    "fail to roundtrip to nothing" in {
      val bytes = Array(1, 2, 3).map(_.toByte)
      val encryptResult = Try(
        primarySdk
          .documentEncryptUnmanaged(bytes, new DocumentEncryptOpts(null, null, false, Array(), Array(), null))
      )
      encryptResult.isFailure shouldBe true
    }
  }

  "Document Get Id From Bytes" should {
    "return the document's ID" in {
      val encryptResult = Try(primarySdk.documentEncrypt(Array(), new DocumentEncryptOpts)).toEither.value
      val id = Try(primarySdk.documentGetIdFromBytes(encryptResult.getEncryptedData)).toEither.value
      id shouldBe encryptResult.getId
    }
  }

  "Document Get Metadata" should {
    "Return an error when retrieving a document that doesn't exist" in {
      val docID = DocumentId.validate("not-a-document-ID-that-exists=/")
      val getResult = Try(primarySdk.documentGetMetadata(docID))
      getResult.isFailure shouldBe true
    }

    "Return expected details about document" in {
      val bytes = Array(2, 3, 4).map(_.toByte)
      val encryptResult = Try(primarySdk.documentEncrypt(bytes, new DocumentEncryptOpts)).toEither.value
      val doc = Try(primarySdk.documentGetMetadata(encryptResult.getId)).toEither.value
      // we don't have equals and hashCode on our foreign_enums, so we'll make the call twice
      // and make sure that the inherited implementation compares value correctly
      val doc2 = Try(primarySdk.documentGetMetadata(encryptResult.getId)).toEither.value

      doc.getId shouldBe encryptResult.getId
      doc.getName.isPresent shouldBe false
      doc.getAssociationType shouldBe AssociationType.FromUser
      doc.getAssociationType shouldBe doc2.getAssociationType
      doc.getAssociationType.hashCode shouldBe doc2.getAssociationType.hashCode
      doc.getVisibleToUsers should have length 1
      doc.getVisibleToUsers.head.getId shouldBe primaryUser
      doc.getVisibleToGroups should have length 0
    }
    "Return details when encrypted to group" in {
      val data: Array[Byte] = List(1, 2, 3).map(_.toByte).toArray
      val groupCreate = primarySdk.groupCreate(new GroupCreateOpts)
      val encryptResult =
        primarySdk.documentEncrypt(
          data,
          new DocumentEncryptOpts(null, null, false, Array(), Array(groupCreate.getId), null)
        )
      val getResult = primarySdk.documentGetMetadata(encryptResult.getId)

      getResult.getVisibleToGroups.map(_.getId) shouldBe Array(groupCreate.getId)
      getResult.getId shouldBe encryptResult.getId
    }
  }

  "Document Get Metadata Unmanaged" should {
    "return metadata from EDEKs" in {
      val bytes = Array(1, 2, 3).map(_.toByte)
      val encryptResult =
        Try(primarySdk.documentEncryptUnmanaged(bytes, new DocumentEncryptOpts)).toEither.value
      val metadata =
        Try(primarySdk.documentGetMetadataUnmanaged(encryptResult.getEncryptedDeks)).toEither.value
      metadata.getId shouldBe encryptResult.getId
      metadata.getVisibleToUsers.length should be >= 1
      metadata.getVisibleToGroups should have length 0
    }
    "return group visibility when encrypted to group" in {
      val groupCreate = primarySdk.groupCreate(new GroupCreateOpts)
      val bytes = Array(4, 5, 6).map(_.toByte)
      val encryptResult = Try(
        primarySdk.documentEncryptUnmanaged(
          bytes,
          new DocumentEncryptOpts(null, null, false, Array(), Array(groupCreate.getId), null)
        )
      ).toEither.value
      val metadata =
        Try(primarySdk.documentGetMetadataUnmanaged(encryptResult.getEncryptedDeks)).toEither.value
      metadata.getVisibleToGroups.map(_.getId) shouldBe Array(groupCreate.getId)
    }
    "fail for invalid EDEKs" in {
      val garbage = Array(1, 2, 3).map(_.toByte)
      val result = Try(primarySdk.documentGetMetadataUnmanaged(garbage))
      result.isFailure shouldBe true
    }
  }

  "Document Get Id From Bytes Unmanaged" should {
    "return document ID from encrypted bytes" in {
      val bytes = Array(7, 8, 9).map(_.toByte)
      val encryptResult =
        Try(primarySdk.documentEncryptUnmanaged(bytes, new DocumentEncryptOpts)).toEither.value
      val id =
        Try(primarySdk.documentGetIdFromBytesUnmanaged(encryptResult.getEncryptedData)).toEither.value
      id shouldBe encryptResult.getId
    }
  }

  "Document Get Id From EDEKs Unmanaged" should {
    "return document ID from EDEKs" in {
      val bytes = Array(10, 11, 12).map(_.toByte)
      val encryptResult =
        Try(primarySdk.documentEncryptUnmanaged(bytes, new DocumentEncryptOpts)).toEither.value
      val idFromEdeks =
        Try(primarySdk.documentGetIdFromEdeksUnmanaged(encryptResult.getEncryptedDeks)).toEither.value
      val idFromBytes =
        Try(primarySdk.documentGetIdFromBytesUnmanaged(encryptResult.getEncryptedData)).toEither.value
      idFromEdeks shouldBe encryptResult.getId
      idFromEdeks shouldBe idFromBytes
    }
  }

  "Document Grant Access Unmanaged" should {
    "grant access and return updated EDEKs" in {
      val bytes = Array(13, 14, 15).map(_.toByte)
      val encryptResult =
        Try(primarySdk.documentEncryptUnmanaged(bytes, new DocumentEncryptOpts)).toEither.value
      val grantResult = Try(
        primarySdk.documentGrantAccessUnmanaged(
          encryptResult.getEncryptedDeks,
          Array(secondaryUser),
          Array()
        )
      ).toEither.value
      grantResult.getChanged.getUsers shouldBe Array(secondaryUser)
      grantResult.getErrors.isEmpty shouldBe true
      grantResult.getEncryptedDeks.length should be > 0
      grantResult.getAccessViaUserOrGroup.isPresent shouldBe true

      // secondary user can decrypt with updated EDEKs
      val decryptResult = Try(
        secondarySdk.documentDecryptUnmanaged(encryptResult.getEncryptedData, grantResult.getEncryptedDeks)
      ).toEither.value
      decryptResult.getDecryptedData shouldBe bytes
    }
    "grant access to group" in {
      val groupOpts =
        new GroupCreateOpts(null, null, true, true, null, Array(secondaryUser), Array(secondaryUser), false)
      val groupCreate = primarySdk.groupCreate(groupOpts)
      val bytes = Array(16, 17, 18).map(_.toByte)
      val encryptResult =
        Try(primarySdk.documentEncryptUnmanaged(bytes, new DocumentEncryptOpts)).toEither.value
      val grantResult = Try(
        primarySdk.documentGrantAccessUnmanaged(
          encryptResult.getEncryptedDeks,
          Array(),
          Array(groupCreate.getId)
        )
      ).toEither.value
      grantResult.getChanged.getGroups shouldBe Array(groupCreate.getId)
      grantResult.getErrors.isEmpty shouldBe true

      // secondary user (group member) can decrypt with updated EDEKs
      val decryptResult = Try(
        secondarySdk.documentDecryptUnmanaged(encryptResult.getEncryptedData, grantResult.getEncryptedDeks)
      ).toEither.value
      decryptResult.getDecryptedData shouldBe bytes
    }
  }

  "Document Revoke Access Unmanaged" should {
    "revoke user access and return updated EDEKs" in {
      val bytes = Array(19, 20, 21).map(_.toByte)
      val encryptResult = Try(
        primarySdk.documentEncryptUnmanaged(
          bytes,
          new DocumentEncryptOpts(null, null, true, Array(secondaryUser), Array(), null)
        )
      ).toEither.value
      // secondary user can decrypt with original EDEKs
      val decryptResult1 = Try(
        secondarySdk.documentDecryptUnmanaged(encryptResult.getEncryptedData, encryptResult.getEncryptedDeks)
      ).toEither.value
      decryptResult1.getDecryptedData shouldBe bytes

      val revokeResult = Try(
        primarySdk.documentRevokeAccessUnmanaged(
          encryptResult.getEncryptedDeks,
          Array(secondaryUser),
          Array()
        )
      ).toEither.value
      revokeResult.getChanged.getUsers shouldBe Array(secondaryUser)
      revokeResult.getErrors.isEmpty shouldBe true
      revokeResult.getEncryptedDeks.length should be > 0
      // revoke is offline, so accessVia should be absent
      revokeResult.getAccessViaUserOrGroup.isPresent shouldBe false

      // secondary user cannot decrypt with updated EDEKs
      val decryptResult2 = Try(
        secondarySdk.documentDecryptUnmanaged(encryptResult.getEncryptedData, revokeResult.getEncryptedDeks)
      )
      decryptResult2.isFailure shouldBe true
    }
    "fail for invalid EDEKs" in {
      val garbage = Array(1, 2, 3).map(_.toByte)
      val result = Try(primarySdk.documentRevokeAccessUnmanaged(garbage, Array(secondaryUser), Array()))
      result.isFailure shouldBe true
    }
  }

  "Document File Encrypt/Decrypt Unmanaged" should {
    "roundtrip file to self" in {
      val testData = "Hello, streaming encryption!".getBytes("UTF-8")
      val sourcePath = java.nio.file.Files.createTempFile("ironoxide-test-source", ".txt")
      val encryptedPath = java.nio.file.Files.createTempFile("ironoxide-test-encrypted", ".iron")
      val decryptedPath = java.nio.file.Files.createTempFile("ironoxide-test-decrypted", ".txt")

      try {
        // Write test data to source file
        java.nio.file.Files.write(sourcePath, testData)

        // Encrypt file
        val encryptResult = Try(
          primarySdk.documentFileEncryptUnmanaged(
            sourcePath.toString,
            encryptedPath.toString,
            new DocumentEncryptOpts
          )
        ).toEither.value
        encryptResult.getId.getId.length should be > 0
        encryptResult.getEncryptedDeks.length should be > 0
        encryptResult.getChanged.getUsers.length shouldBe 1

        // Decrypt file
        val decryptResult = Try(
          primarySdk.documentFileDecryptUnmanaged(
            encryptedPath.toString,
            decryptedPath.toString,
            encryptResult.getEncryptedDeks
          )
        ).toEither.value
        decryptResult.getId shouldBe encryptResult.getId
        decryptResult.getAccessViaUserOrGroup.isUser shouldBe true

        // Verify decrypted content matches original
        val decryptedData = java.nio.file.Files.readAllBytes(decryptedPath)
        decryptedData shouldBe testData
      } finally {
        java.nio.file.Files.deleteIfExists(sourcePath)
        java.nio.file.Files.deleteIfExists(encryptedPath)
        java.nio.file.Files.deleteIfExists(decryptedPath)
        ()
      }
    }
  }

}
