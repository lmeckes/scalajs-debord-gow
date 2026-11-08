package com.github.meckesl.ws

import com.github.meckesl.protocol.{ClientMsg, ServerMsg}
import org.scalajs.dom
import org.scalajs.dom.{CloseEvent, Event, MessageEvent, WebSocket}
import upickle.default.{read, write}

import scala.scalajs.js.timers.setTimeout
import scala.util.Try

/** JSON lobby connection to the server that served the page; reconnects automatically. */
class LobbyClient(onOpen: () => Unit, onMessage: ServerMsg => Unit, onClosed: () => Unit) {

  private var connection: WebSocket = _
  private var retryDelay = 1000
  private var stopped = false

  /** False when the page was opened from disk, where there is no server to connect to. */
  val available: Boolean = dom.window.location.protocol != "file:"

  private def url: String = {
    val location = dom.window.location
    val scheme = if (location.protocol == "https:") "wss" else "ws"
    s"$scheme://${location.host}/lobby"
  }

  private def connect(): Unit = {
    connection = new WebSocket(url)
    connection.onopen = (_: Event) => {
      retryDelay = 1000
      onOpen()
    }
    connection.onmessage = (e: MessageEvent) => {
      Try(read[ServerMsg](e.data.toString)).foreach(onMessage)
    }
    connection.onclose = (_: CloseEvent) => {
      if (!stopped) {
        onClosed()
        setTimeout(retryDelay)(connect())
        retryDelay = (retryDelay * 2).min(10000)
      }
    }
  }

  def send(msg: ClientMsg): Unit =
    if (connection != null && connection.readyState == 1) connection.send(write(msg))

  def stop(): Unit = {
    stopped = true
    if (connection != null) connection.close()
  }

  if (available) connect()
}
