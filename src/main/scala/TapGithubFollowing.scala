package com.fow5040.tapgithubfollowing

import io.circe.generic.auto._, io.circe.syntax._
import SingerDatatypes._

import github4s._
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
                                     else SingerState(Vector(Vector(0)))
    
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

  }

}
