package com.github.meckesl.ws

import org.scalajs.dom
import org.scalajs.dom.{Event, HTMLButtonElement, HTMLDivElement, HTMLInputElement, HTMLSpanElement, MessageEvent, WebSocket}

class LobbyClient(div : HTMLDivElement, input: HTMLInputElement, send: HTMLButtonElement) {

  private val connection = {
    val location = dom.window.location
    val scheme = if (location.protocol == "https:") "wss" else "ws"
    new WebSocket(s"$scheme://${location.host}/lobby")
  }

  input.onkeydown = (e: dom.KeyboardEvent) => {
    println("lobby message")
    if (e.keyCode == dom.KeyCode.Enter) {
      e.preventDefault()
      sendMessage(input.value)
      input.value = ""
    }
  }

  send.onclick = (e: dom.MouseEvent) => {
    sendMessage(input.value)
    input.value = ""
  }

  connection.onopen = (event: Event) => {
    output("WebSocket connection established")
  }

  connection.onerror = (event: Event) => {
    output(s"WebSocket error observed: ${event.toString}")
  }

  connection.onmessage = (messageEvent: MessageEvent) => {
    output(s"Received from server: ${messageEvent.data.toString}")
  }

  connection.onclose = (event: Event) => {
    output("WebSocket connection closed")
  }

  private def sendMessage(message: String): Unit = {
    connection.send(message)
  }

  private def output(s: String): Unit = div.innerHTML += s"$s<br/>"

}

