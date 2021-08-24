import org.scalatest.FlatSpec
import io.circe.generic.auto._, io.circe.syntax._, io.circe.parser.decode
import io.circe.{ Decoder, DecodingFailure, Encoder, Json }
import cats.syntax.show._
import com.fow5040.tapgithubfollowing.SingerDatatypes._
import com.fow5040.tapgithubfollowing.TapGithubFollowing._
import shapeless.record

class StateLogicSpec extends FlatSpec {

    "The State Incrementer" should "handle the initial state" in {
        // [0,1] => [1,1]
        val initialState = SingerState(Vector(Vector(0,1)))
        val expectedState = SingerState(Vector(Vector(1,1)))
        val nextState = incrementState(initialState)
            
        assert(nextState == expectedState)
    }

    it should "handle a basic state change" in {
        // [0,1][4,11] => [0,1][5,11]
        val initialState = SingerState(Vector(Vector(0,1), Vector(4,11)))
        val expectedState = SingerState(Vector(Vector(0,1), Vector(5,11)))
        val nextState = incrementState(initialState)
            
        assert(nextState == expectedState)
    }

    it should "handle state change with overflow" in {
        // [0,1][8,9][11,12] => [1,1][9,9][12,12]
        val initialState = SingerState(Vector(Vector(0,1), Vector(8,9), Vector(11,12)))
        val expectedState = SingerState(Vector(Vector(1,1), Vector(9,9), Vector(12,12)))
        val nextState = incrementState(initialState)
            
        assert(nextState == expectedState)
    }
    "The overflow checker" should "confirm a basic overflow" in {
        val stateOverflowed = SingerState(Vector(Vector(0,1),Vector(11,11)))
        assert (isOverflowed(stateOverflowed.graph_traversal))
    }

    it should "not give a false positive" in {
        val stateOverflowed = SingerState(Vector(Vector(0,1),Vector(8,11),Vector(4,15)))
        assert (!isOverflowed(stateOverflowed.graph_traversal))
    }

    "The state upticker" should "handle a basic uptick" in {
        // [1,1][11,11] => [0,1][0,11][0,100]
        val initialState = SingerState(Vector(Vector(1,1), Vector(11,11)))
        val nextSizes = Vector(11,100)
        val expectedState = SingerState(Vector(Vector(0,1), Vector(0,11), Vector(0,100)))
        val nextState = uptickState(initialState, nextSizes)
        assert(nextState == expectedState)
    }

    it should "handle a nested uptick" in {
        // [0,1][6,11][100,100] => [0,1][6,11][0,66]
        val initialState = SingerState(Vector(Vector(0,1), Vector(6,11), Vector(100,100)))
        val nextSizes = Vector(66)
        val expectedState = SingerState(Vector(Vector(0,1), Vector(6,11), Vector(0,66)))
        val nextState = uptickState(initialState, nextSizes)
        assert(nextState == expectedState)
    }

    it should "handle multiple upticks" in {
        // [1,1][11,11][100,100] => [0,1][0,11][0,66][0,33]
        val initialState = SingerState(Vector(Vector(1,1), Vector(11,11), Vector(100,100)))
        val nextSizes = Vector(11,66,33)
        val expectedState = SingerState(Vector(Vector(0,1), Vector(0,11), Vector(0,66), Vector(0,33)))
        val nextState = uptickState(initialState, nextSizes)
        assert(nextState == expectedState)
    }


}
