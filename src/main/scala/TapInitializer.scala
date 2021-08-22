package com.fow5040.tapgithubfollowing
import scopt.OParser
import java.io.File
import SingerDatatypes.SingerConfig

object TapInitializer {

  case class TapConfig (
    configFile : File = new File ("."),
    stateFile : File = new File ("."),
    discover : Boolean = false
  )

  def genSingerConfig (args : Array[String]) : SingerConfig {
    val builder = OParser.builder[TapConfig]
    val parser1 = {
      import builder._
      OParser.sequence(
        programName("tap-github-following"),
        head("tap-github-following", "0.x"),
        opt[Unit]("discover")
          .action( (_,c) => c.copy(discover = true))
          .text("test config and discover available schemas")
        opt[File]('c', "config")
          .required()
          .valueName("<file>")
          .action((x, c) => c.copy(configFile = x))
          .text("path to configuration file")
        opt[File]('s', "state")
          .valueName("<file>")
          .action((x, c) => c.copy(stateFile = x))
          .text("path to state file"),
        help("help").text("""Example usages are: 
          INTIAL: bin/tap-github-following --config config.json --discover
          SUBSEQUENT: bin/tap-github-following --config config.json [--state state.json]""")
      )
    }


    // OParser.parse returns Option[TapConfig]
    OParser.parse(parser1, args, TapConfig()) match {
      case Some(config) =>
        // do something
      case _ =>
        sys.exit(1)
    }

    SingerConfig(access_token="",starting_user="")
  }
}
