package com.fow5040.tapgithubfollowing

import io.circe.generic.auto._, io.circe.syntax._
import SingerDatatypes._

import github4s.Github
import github4s.domain._
//TODO: This isn't resolving for some reason - use Java client for now
//import org.http4s.blaze.client._
import org.http4s.client.{Client, JavaNetClientBuilder}
import cats.syntax.show._
import cats.effect.IO
import cats.effect.unsafe.implicits.global

import scala.concurrent.Await
import scala.concurrent.duration._

object TapGithubFollowing {

  def main(args : Array[String]){

    // TODO: Create static log file output or allow user to provide one in run options
    val opts = TapInitializer.generateSingerConfig(args)
    val config : SingerConfig = TapInitializer.openConfigFile(opts.configFile)
    // If no state file is provided, assume intial state of [[0]]
    val initialState : SingerState = if (opts.stateFile != ".") TapInitializer.openStateFile(opts.stateFile)
                                     else SingerState(Vector(Vector(0,1)))
    
    if (opts.discover) doDiscover(config)
    else doReplication(config, initialState)
  }

  def doDiscover(config: SingerConfig){
    // Test if provided client token is valid
    val httpClient: Client[IO] = JavaNetClientBuilder[IO].create
    val userRequest = Github[IO](httpClient, Option(config.access_token)).users.get(config.starting_user).unsafeToFuture()

    // If this times out, allow timeout exception to propagate
    val maybeUserResult = Await.result(userRequest, 10.seconds);

    maybeUserResult.result match {
      case Left(e) => {System.err.println(e); sys.exit(1)}
      case Right(u) => () // TODO: Log API call success here, maybe
    }

    val outputCatalog = SingerCatalog( 
                          streams = Vector(
                            SingerStream(
                              tap_stream_id = "github_following",
                              stream = "github_following",
                              schema = SingerSchema(Map(
                                "username" -> Map(
                                  "type" -> "string",
                                ),
                                "user_id" -> Map(
                                  "type" -> "integer",
                                ),
                                "degree_of_removal" -> Map(
                                  "type" -> "integer"
                                )
                              ))
                            )
                        ))

    println(outputCatalog.asJson.spaces2)
    sys.exit(0)

  }

  def doReplication(config: SingerConfig, initialState: SingerState){
    // if state is 0
    // Get user >> emit messages


    //emitNextUserMessages([[1,1][0,length of following]])
    //  add each user if not in higher list
    //  increment state
    //  send state message
    //  emitNextUserMessages(newState)


  }




  //** Side Effect Functions **//

  /** Requests a list of followed users the given index vector
    * 
    * For Example: Starting user's followed users = f([0])
    * Starting user's first followed user's, followed users = f([0,0])
    *
    * @param graph_traversal
    * @return
    */
  def requestUserList( graph_traversal : Vector[Int] ) : List[User] = {
    return List[User]()
  }

  /** Returns a vector of sizes needed to return a new upticked state
    * 
    * The vector size equals the number of overflowed vectors in the current state
    * Each size value corresponds to a user's total number of followed users
    *
    * @param state
    * @return
    */
  def requestNextStateVectorSizes( state : SingerState ) : Vector[Int] = {
    return Vector(0)
  }




  //** No Side Effect Functions **//

  /** Returns true if the last vector in a state's graph_traveral is full
    *
    * @param state
    * @return
    */
  def isOverflowed ( graph_traversal : Vector[Vector[Int]] ) : Boolean = {
    val lastVec = graph_traversal.last
    return lastVec.head == lastVec.last
  }

  /** Returns what the next state value would be, before handling overflow
    *
    * @param state
    * @return
    */
  def incrementState ( state : SingerState ) : SingerState = {
    def incrementTraversal ( gt : Vector[Vector[Int]]) : Vector[Vector[Int]] = {
      // case where we get [[0,1]]
      if (gt.length == 1) return Vector(Vector(1,1))

      // increment the last vector
      val lastVec = gt.last
      val lastInd = gt.length-1
      val newLastVec = lastVec.updated(0, lastVec.head + 1)
      val newgt = gt.updated(lastInd, newLastVec)
      // if it's not overflowed, great
      if (!isOverflowed(newgt)) return newgt

      // otherwise recursively overflow left
      return incrementTraversal(gt.dropRight(1)) ++ Vector(newLastVec)
    }
      
    val graph_traversal = state.graph_traversal
    val new_graph_traversal = incrementTraversal(graph_traversal)
    return SingerState( new_graph_traversal )
  }

  /** Given a state with overflowed vectors, and a vector of sizes for the next
    * set of state vectors, return the next state to track replication
    *
    * @param state
    * @param nextVecSizes
    * @return
    */
  def uptickState ( state : SingerState, nextVecSizes : Vector[Int]) : SingerState = {
    /** EXAMPLES
      *   nextVecSizes = [66,33]
      *   state = [[0,1][6,11][13,13][7,7]]
      *   return  [[0,1][6,11][0,66],[0,33]]
      * 
      *   nextVecSizes = [11,66,33,27]
      *   state = [[1,1][11,11][13,13][7,7]]
      *   return  [[0,1][0,11][0,66][0,33][0,27]]
      */
       
    val gt = state.graph_traversal
    val overflowCount = nextVecSizes.length;
    val newGt = nextVecSizes.map(Vector(0,_))
    if (gt.length == overflowCount) 
      return SingerState(Vector(Vector(0,1)) ++ newGt)
    val unchangedGt = gt.take(gt.length - overflowCount)
    return SingerState(unchangedGt ++ newGt)
  }

}
