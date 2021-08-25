package com.fow5040.tapgithubfollowing
import cats.syntax.show._
import io.circe.generic.auto._, io.circe.syntax._, io.circe.parser.decode
import io.circe.{ Decoder, DecodingFailure, Encoder, Json }
import scopt.OParser
import SingerDatatypes.SingerConfig
import SingerDatatypes.SingerState
import SingerDatatypes.SingerStateMessage

object TapInitializer {

  case class TapOptions (
    configFile : String = ".",
    stateFile : String = ".",
    discover : Boolean = false
  )

  def generateSingerConfig (args : Array[String]) : TapOptions = {

    val builder = OParser.builder[TapOptions]

    val parser1 = {
      import builder._
      OParser.sequence(
        programName("tap-github-following"),
        head("tap-github-following", "0.x"),
        opt[Unit]("discover")
          .action( (_,c) => c.copy(discover = true))
          .text("test access token in config and discover available schemas"),
        opt[String]('c', "config")
          .required()
          .valueName("<file>")
          .action((x, c) => c.copy(configFile = x))
          .text("path to configuration file"),
        opt[String]('s', "state")
          .valueName("<file>")
          .action((x, c) => c.copy(stateFile = x))
          .text("path to state file"),
        help("help").text("""Example usages are: 
          INTIAL: bin/tap-github-following --config config.json --discover
          SUBSEQUENT: bin/tap-github-following --config config.json [--state state.json]""")
      )
    }


    // OParser.parse returns Option[TapOptions]
    OParser.parse(parser1, args, TapOptions()) match {
      case Some(config) =>
        return config
      case _ =>
        //Error messages in parsing already printed by Parser
        sys.exit(1)
    }

    // Should never get here
    return TapOptions()
  }

  def openConfigFile(filename: String) : SingerConfig = {
    val sourceFile = scala.io.Source.fromFile(filename)
    val lines = try sourceFile.mkString finally sourceFile.close()

    decode[SingerConfig](lines) match {
      case Left(e) => {System.err.println(e.show); sys.exit(1)}
      case Right(s) => s
    }

  }

  def openStateFile(filename: String) : SingerState = {
    val sourceFile = scala.io.Source.fromFile(filename)
    val lines = try sourceFile.mkString finally sourceFile.close()

    decode[SingerState](lines) match {
      case Left(e) => {System.err.println(e.show); sys.exit(1)}
      case Right(s) => s
    }

  }
}
