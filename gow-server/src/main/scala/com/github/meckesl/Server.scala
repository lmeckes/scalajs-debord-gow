package com.github.meckesl

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.github.meckesl.lobby.{Boards, Connection, Lobby}
import com.github.meckesl.protocol.{ClientMsg, ServerMsg}
import upickle.default.{read, write}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object Server extends App {

  implicit val system: ActorSystem = ActorSystem("gow-server")
  import system.dispatcher

  Boards.loadDefault()
  val lobby = new Lobby()
  system.scheduler.scheduleWithFixedDelay(5.seconds, 5.seconds)(() => lobby.tick())

  def lobbyHandler(): Flow[Message, Message, Any] = {
    val (queue, outgoing) = Source.queue[Message](256, OverflowStrategy.dropHead).preMaterialize()
    val conn = new Connection {
      def send(msg: ServerMsg): Unit = queue.offer(TextMessage(write(msg)))
      def close(): Unit = queue.complete()
    }
    val incoming = Flow[Message]
      .mapAsync(1) {
        case tm: TextMessage => tm.textStream.runFold("")(_ + _).map(Option(_))
        case bm: BinaryMessage => bm.dataStream.runWith(Sink.ignore).map(_ => None)
      }
      .collect { case Some(text) => text }
      .to(Sink.foreach[String] { text =>
        Try(read[ClientMsg](text)) match {
          case Success(msg) => lobby.handle(conn, msg)
          case Failure(_) => conn.send(ServerMsg.ServerError("Malformed message"))
        }
      }.mapMaterializedValue(_.onComplete(_ => lobby.disconnected(conn))))
    Flow.fromSinkAndSourceCoupled(incoming, outgoing)
  }

  val route = {
    respondWithHeaders(
      RawHeader("Cache-Control", "no-cache, no-store, must-revalidate"),
      RawHeader("Pragma", "no-cache"),
      RawHeader("Expires", "0")
    ) {
      path("lobby") {
        extractWebSocketUpgrade { upgrade =>
          complete(upgrade.handleMessages(lobbyHandler()))
        }
      } ~
        get {
          extractUnmatchedPath { path =>
            if (path.toString == "/" || path.toString.isEmpty)
              getFromResource("web/index.html")
            else
              getFromResourceDirectory("web")
          }
        }
    }
  }

  val host = sys.env.getOrElse("HOST", "0.0.0.0")
  val port = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(8080)

  Http().newServerAt(host, port).bind(route).onComplete {
    case Success(binding) =>
      println(s"Server online at http://localhost:${binding.localAddress.getPort}/ (bound to $host)")
    case Failure(e) =>
      println(s"Failed to bind $host:$port: ${e.getMessage}")
      system.terminate()
  }
}
