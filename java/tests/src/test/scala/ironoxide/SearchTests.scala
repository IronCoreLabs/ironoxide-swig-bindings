package ironoxide

import scala.util.Try
import com.ironcorelabs.sdk._

class SearchTests extends TestSuite {
  "Tokenize" should {
    "tokenize data and a query" in {
      val searchQuery = "ironcore labs"
      val groupId = Try(primarySdk.groupCreate(new GroupCreateOpts)).toEither.value.getId
      val ebis = Try(primarySdk.createBlindIndex(groupId)).toEither.value
      val bis = Try(primarySdk.initializeBlindIndexSearch(ebis)).toEither.value
      val queryTokens = Try(bis.tokenizeQuery(searchQuery, "")).toEither.value
      val dataTokens = Try(bis.tokenizeData(searchQuery, "")).toEither.value
      queryTokens.length shouldBe 8
      dataTokens.length should be > 8
      queryTokens.toSet.subsetOf(dataTokens.toSet) shouldBe true
    }
    "tokenize with partition" in {
      val searchQuery = "ironcore labs"
      val groupId = Try(primarySdk.groupCreate(new GroupCreateOpts)).toEither.value.getId
      val ebis = Try(primarySdk.createBlindIndex(groupId)).toEither.value
      val bis = Try(primarySdk.initializeBlindIndexSearch(ebis)).toEither.value
      val queryTokens = Try(bis.tokenizeQuery(searchQuery, "")).toEither.value
      val queryTokens2 = Try(bis.tokenizeQuery(searchQuery, "foo")).toEither.value
      queryTokens.length shouldBe 8
      queryTokens2.length shouldBe 8
      queryTokens should not be queryTokens2

    }
  }
}
