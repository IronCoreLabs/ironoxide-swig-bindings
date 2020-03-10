package ironrust

import org.scalatest.{BeforeAndAfterAll, OptionValues}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import cats.scalatest.EitherValues

//https://www.youtube.com/watch?v=LlLX6KSdWcQ
trait DudeSuite extends AnyWordSpec with BeforeAndAfterAll with Matchers with EitherValues with OptionValues {
  override def beforeAll(): Unit = {
    try {
      java.lang.System.loadLibrary("ironoxide_java")
    } catch {
      case _: UnsatisfiedLinkError =>
        println("Failed to load ironoxide_java")
        println(
          s"""The value was not found in java.library.path. Path was '${System.getProperty("java.library.path")}'.
                          |Note that the path should be to the directory where ironoxide_java is, not the actual path. If you build ironoxide_java with
                          |`cargo build` then there should be libironoxide_java.* in ../target/debug.""".stripMargin
        );
        //There is no way we can actually continue, so I'm going to do the dirty thing to prevent misleading errors from spewing.
        System.exit(1)
    }
  }
}
