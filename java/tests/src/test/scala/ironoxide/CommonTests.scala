package ironoxide

import scala.util.Try
import com.ironcorelabs.sdk._
import scodec.bits.ByteVector

class CommonTests extends TestSuite {
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
      assert(ironoxideJavaFolder.length > 0, "Unable to find Java source files in OUT_DIR")
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

  "DeviceContext" should {
    "Successfully serialize/deserialize as JSON" in {
      val jwt = generateValidJwt(secondaryUser.getId)
      val deviceName = DeviceName.validate("device")
      val deviceContext =
          IronOxide.generateNewDevice(jwt, testUsersPassword, new DeviceCreateOpts(deviceName), null).device
        
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

  "Initialize" should {
    "fail with short timeout" in {
      val shortConfig = new IronOxideConfig(new PolicyCachingConfig, Duration.fromMillis(5))
      val maybeSdk = Try(IronOxide.initialize(primaryUserDevice, shortConfig))
      maybeSdk.isFailure shouldBe true
    }
  }

}
