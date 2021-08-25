package com.fow5040.tapgithubfollowing;

import scala.collection.immutable.Map
import io.circe.generic.auto._, io.circe.syntax._, io.circe.parser.decode
import io.circe.{ Decoder, DecodingFailure, Encoder, Json }

object SingerDatatypes {

    // INPUT datatypes
    case class SingerState (
        graph_traversal: Vector[Vector[Int]]
    )

    case class SingerConfig (
        access_token: String,
        starting_user: String
    )

    case class SingerCatalog (
        streams: Vector[SingerStream]
    )

    case class SingerStream (
        stream: String,
        tap_stream_id: String,
        schema: SingerSchema
    )

    case class SingerSchema (
        properties: Map[String, Map[String, String]]
    )

    case class SingerRecord (
        username : String,
        user_id: Long,
        degree_of_removal: Int
    )


    // OUTPUT datatypes
    sealed trait SingerMessageTrait {
        // this is awkward
        def `type`: String
    }

    case class SingerMessage (
        `type`: String
    ) extends SingerMessageTrait;

    case class SingerSchemaMessage (
        `type`: String,
        stream: String,
        schema: SingerSchema,
        key_properties: Array[String]
    ) extends SingerMessageTrait

    case class SingerRecordMessage(
        `type`: String,
        stream: String,
        record: SingerRecord
    ) extends SingerMessageTrait

    case class SingerStateMessage (
        `type`: String,
        value: SingerState
    ) extends SingerMessageTrait

}
