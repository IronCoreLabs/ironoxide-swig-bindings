package ironrust

import com.ironcorelabs.sdk._
import scala.util.Try

class UserCreateFailureTest extends DudeSuite {
  "userCreate" should {
    "return error for fixed jwt" in {
      val badResponseTry = Try(IronOxide.userCreate("foo", "bar", new UserCreateOpts(true), null)).toEither
      badResponseTry.leftValue.getMessage should include(
        """must be valid ascii and be formatted correctly"""
      )
    }
    "return error for outdated jwt" in {
      val jwt =
        "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE1NTA3NzE4MjMsImlhdCI6MTU1MDc3MTcwMywia2lkIjo1NTEsInBpZCI6MTAxMiwic2lkIjoidGVzdC1zZWdtZW50Iiwic3ViIjoiYTAzYjhlNTYtMTVkMi00Y2Y3LTk0MWYtYzYwMWU1NzUxNjNiIn0.vlqt0da5ltA2dYEK9i_pfRxPd3K2uexnkbAbzmbjW65XNcWlBOIbcdmmQLnSIZkRyTORD3DLXOIPYbGlApaTCR5WbaR3oPiSsR9IqdhgMEZxCcarqGg7b_zzwTP98fDcALGZNGsJL1hIrl3EEXdPoYjsOJ5LMF1H57NZiteBDAsm1zfXgOgCtvCdt7PQFSCpM5GyE3und9VnEgjtcQ6HAZYdutqjI79vaTnjt2A1X38pbHcnfvSanzJoeU3szwtBiVlB3cfXbROvBC7Kz8KvbWJzImJcJiRT-KyI4kk3l8wAs2FUjSRco8AQ1nIX21QHlRI0vVr_vdOd_pTXOUU51g"
      val resp = Try(IronOxide.userCreate(jwt, "foo", new UserCreateOpts(true), null)).toEither
      resp.leftValue.getMessage should include(
        """ServerError { message: "\'jwt ey"""
      )
    }
  }
}
