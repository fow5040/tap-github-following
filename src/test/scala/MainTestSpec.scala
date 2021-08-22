import org.scalatest.FlatSpec
import io.circe.generic.auto._, io.circe.syntax._, io.circe.parser.decode
import io.circe.{ Decoder, DecodingFailure, Encoder, Json }
import cats.syntax.show._
import com.fow5040.tapgithubfollowing.SingerDatatypes._
import shapeless.record

class MainTestSpec extends FlatSpec {

    val recordMessageString = """
    {
        "type": "RECORD",
        "stream": "github_following",
        "record": {
            "username": "fow5040",
            "user_id": 4,
            "degree_of_removal": 0
        }
    }
    """

    "A singer message" should "deserialize into a generic message" in {
        val eitherSingerMessage = decode[SingerMessage](recordMessageString);

        if(eitherSingerMessage.isLeft) print(eitherSingerMessage.left.get.show)
            
        assert(eitherSingerMessage.isRight)
    }

    it should "deserialize into a typed message" in {
        val eitherSingerRecordMessage = decode[SingerRecordMessage](recordMessageString);

        assert(eitherSingerRecordMessage.isRight)
    }

    it should "serialize into a typed message" in {
        val aSingerRecordMessage = SingerRecordMessage(
            "RECORD",
            "github_following",
            SingerRecord("fow5040",4,0)
        )

        val messageJson = aSingerRecordMessage.asJson;
        val testMessageJson = decode[SingerRecordMessage](recordMessageString)
                                .getOrElse(SingerRecordMessage("","",SingerRecord("",0,0)))
                                .asJson

        assert(messageJson == testMessageJson)
    }
  
}
