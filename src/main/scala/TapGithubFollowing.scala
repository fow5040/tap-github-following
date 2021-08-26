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
    // If no state file is provided, assume intial state of [[0,1]]
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

    val httpClient: Client[IO] = JavaNetClientBuilder[IO].create
    val githubClient = Github[IO](httpClient, Option(config.access_token))

    var newState = initialState

    // Always Output Schema message before record messages
    val schemaMessage = SingerSchemaMessage(
                              `type` = "SCHEMA",
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
                              )),
                              key_properties = Vector("user_id", "degree_of_removal")
                            )

    println(schemaMessage.asJson.noSpaces)

    // Output startingUser if initial state is a fresh state, i.e. [[0,1]]
    if(initialState.graph_traversal.length == 1){
      val u = doSingleUserRequest(githubClient, config.starting_user)
      val singerRecord = SingerRecord(u.login, u.id, 0)
      val output = SingerRecordMessage("RECORD","github_following",singerRecord)
      println(output.asJson.noSpaces)
    }

    var listsGotten = 0

    while( true ){

      //If max_lists_to_get was provided, quit if we've gotten more than that many lists
      if (config.max_lists_to_get >= 0 && listsGotten >= config.max_lists_to_get) {
        sys.exit(0)
      }

      val removal_degs = newState.graph_traversal.length
      val graph_nodes = newState.graph_traversal.map( _.head )

      // Only replicate if not overflowed - i.e. user must follow other users
      // i.e. State = [[0,1][2,5][0,0]]
      if(!isOverflowed(newState.graph_traversal)){

        //TODO: Implement the 'not in higher' list logic and 'get current user id' logic
        //      Only request the user list if the current user is not in a higher tier
        val userList = memoized_requestUserList(graph_nodes, githubClient, config)
        listsGotten += 1
        userList.foreach( u => {
          //TODO: Implement the 'not in higher' list logic
          //      Only write message if the user is not in a higher tier
          val singerRecord = SingerRecord(u.login, u.id, removal_degs)
          val output = SingerRecordMessage("RECORD","github_following",singerRecord)
          println(output.asJson.noSpaces)
        })
      }

      newState = incrementState(newState)
      if(isOverflowed(newState.graph_traversal)){
        val nextSizes = memoized_requestNextStateVectorSizes(newState, githubClient, config)
        newState = uptickState(newState, nextSizes)
      }

      val output = SingerStateMessage("STATE",newState)
      println(output.asJson.noSpaces)
    }

  }




  //** Side Effect Functions **//

  /** Gets information for a single user
    *   should only be used on initial replication
    * @param gc
    * @param username
    * @return
    */
  def doSingleUserRequest( gc: Github[IO], username: String) : User = {
    val userRequest = gc.users.get(username).unsafeToFuture()
    val maybeUsersResult = Await.result(userRequest, 10.seconds);
    maybeUsersResult.result match {
      case Left(error) => {System.err.println(error); sys.exit(1)}
      case Right(user) => {return user}
    }
  }


  /** Requests a list of followed users the given index vector
    * 
    * For Example: Starting user's followed users = f([0])
    * Starting user's first followed user's, followed users = f([0,0])
    * 
    * @param graph_nodes
    * @param gc
    * @param config
    * @return
    */
  def requestUserList( graph_nodes : Vector[Int], gc: Github[IO], config: SingerConfig) : List[User] = {
   if (graph_nodes.length == 1){
      return doUserRequest(gc, config.starting_user)
    } else{
      val indToGet = graph_nodes.last
      val higherList = requestUserList(graph_nodes.dropRight(1), gc, config)
      return if (higherList.length == 0 ) List[User]() else doUserRequest(gc, higherList(indToGet).login)
    }
  }

  /** Gets a list of followed users, given a username
    *
    * @param gc
    * @param username
    * @return
    */
  def doUserRequest( gc: Github[IO], username: String) : List[User] = {
    val usersRequest = gc.users.getFollowing(username).unsafeToFuture()
    val maybeUsersResult = Await.result(usersRequest, 10.seconds);
    maybeUsersResult.result match {
      case Left(error) => {System.err.println(error); sys.exit(1)}
      case Right(users) => {return users}
    }
  }
 
  /** Same as requestUserList, but memoized
    * 
    * will use alot of memory
    */
  def memoized_requestUserList( graph_nodes : Vector[Int], gc: Github[IO], config : SingerConfig ) : List[User] = {
    if (graph_nodes.length == 1){
      return memoized_doUserRequest(gc, config.starting_user)
    } else{
      val indToGet = graph_nodes.last
      val higherList = memoized_requestUserList(graph_nodes.dropRight(1), gc, config)
      return if (higherList.length == 0 ) List[User]() else memoized_doUserRequest(gc, higherList(indToGet).login)
    }
  }


  /** Same as requestUserList, but memoized
    * 
    * will use alot of memory
    */
  def memoized_doUserRequest: ( Github[IO], String ) => List[User] = {

    val requestCache = collection.mutable.Map.empty[String, List[User]]

    ( gc: Github[IO], username: String) => 
      requestCache.getOrElse(username, {
        val userList = doUserRequest(gc, username)
        requestCache.update(username, userList)
        requestCache(username)
      })
  }

  /** Returns a vector of sizes needed to return a new upticked state
    * 
    * The vector size equals the number of overflowed vectors in the current state
    * Each size value corresponds to a user's total number of followed users
    *
    * @param state
    * @return
    */
  def memoized_requestNextStateVectorSizes( state : SingerState, gc : Github[IO], config : SingerConfig) : Vector[Int] = {
    // [[1,1][11,11]] => [get([0]).length, get([0,0]).length]
    // [[0,1][6,11][5,5][7,7]] => [get([0,6]).length, get([0,6,0]).length]

    return overflowedStateToRequestIndices(state).map( vec => memoized_requestUserList(vec,gc,config).length)
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

      val lastVec = gt.last
      val lastInd = gt.length-1
      // increment the last vector if not already overflowed
      val newLastVec = if (!isOverflowed(gt)) lastVec.updated(0, lastVec.head + 1) else lastVec

      val newgt = gt.updated(lastInd, newLastVec)
      // if it's still not overflowed, great
      if (!isOverflowed(newgt)) return newgt

      // otherwise recursively overflow left
      return incrementTraversal(gt.dropRight(1)) ++ Vector(newLastVec)
    }
      
    val graph_traversal = state.graph_traversal
    val new_graph_traversal = incrementTraversal(graph_traversal)
    return SingerState( new_graph_traversal )
  }

  /** Given a state with overflowed vectors, return a vector of next indices to request
    *
    * @param state
    * @return
    */
  def overflowedStateToRequestIndices ( state : SingerState) : Vector[Vector[Int]] = {
    /** EXAMPLES
      * 
      * [[1,1][11,11]] => [[0],[0,0]]
      * [[0,1][6,11][5,5][7,7]] => [[0,6],[0,6,0]]
      * 
      */

    //ex. [[0,1][6,11][5,5][7,7]]
    val gt = state.graph_traversal
    val indices = 1 to gt.length
    val subGts = indices.map(ind => gt.take(ind))
   
    /** ex. [
     *       [[0,1]],
     *       [[0,1][6,11]],
     *       [[0,1][6,11][5,5]],
     *       [[0,1][6,11][5,5][7,7]]
     *      ]
     * 
     *
     */
    val requestIndices = subGts.filter(isOverflowed)
                               .toVector
                               .map( this_gt => 
                                     this_gt.map( vec => 
                                                  if (vec.head == vec.last) 0
                                                  else vec.head
                                    )) 
                       
    // edge case for full overflow -> if full overflow, then return full zero vector.
    if (requestIndices.length == gt.length) return requestIndices
    else return requestIndices.map(v => v.init)
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
