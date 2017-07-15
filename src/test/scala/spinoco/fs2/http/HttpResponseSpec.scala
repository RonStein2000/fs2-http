package spinoco.fs2.http

import cats.effect.IO
import fs2._
import org.scalacheck.Properties
import org.scalacheck.Prop._
import scodec.Attempt
import spinoco.protocol.http.header._
import spinoco.protocol.http.codec.HttpResponseHeaderCodec
import spinoco.protocol.http.{HttpResponseHeader, HttpStatusCode}
import spinoco.fs2.http.util.chunk2ByteVector
import spinoco.protocol.http.header.value.{ContentType, HttpCharset, MediaType}


object HttpResponseSpec extends Properties("HttpResponse") {

  property("encode") = secure {

    val response =
      HttpResponse[IO](HttpStatusCode.Ok)
      .withUtf8Body("Hello World")

    HttpResponse.toStream(response, HttpResponseHeaderCodec.defaultCodec)
      .chunks.runLog.map { _.map(chunk2ByteVector).reduce { _ ++ _ }.decodeUtf8 }
      .unsafeRunSync() ?=
      Right(Seq(
        "HTTP/1.1 200 OK"
        , "Content-Type: text/plain; charset=utf-8"
        , "Content-Length: 11"
        , ""
        , "Hello World"
      ).mkString("\r\n"))

  }


  property("decode") = secure {

    Stream.chunk(Chunk.bytes(
      Seq(
        "HTTP/1.1 200 OK"
        , "Content-Type: text/plain; charset=utf-8"
        , "Content-Length: 11"
        , ""
        , "Hello World"
      ).mkString("\r\n").getBytes
    ))
    .covary[IO]
    .through(HttpResponse.fromStream[IO](4096, HttpResponseHeaderCodec.defaultCodec))
    .flatMap { response => Stream.eval(response.bodyAsString).map(response.header -> _ ) }
    .runLog.unsafeRunSync() ?=
    Vector(
      HttpResponseHeader(
        status = HttpStatusCode.Ok
        , reason = "OK"
        , headers = List(
          `Content-Type`(ContentType(MediaType.`text/plain`, Some(HttpCharset.`UTF-8`), None))
          , `Content-Length`(11)
        )
      ) -> Attempt.Successful("Hello World")
    )
  }



}
