package com.github.meckesl.ui

import org.scalajs.dom
import org.scalajs.dom.{Event, KeyboardEvent, html}

import scala.scalajs.js

/** YouTube-style corner button that lets the board fill the screen. The board area is pinned over the whole page
  * by CSS and, where the browser allows it, the page also goes native fullscreen (so toasts, the loupe and dialogs
  * still show). Toggled by the button or the F key; Escape leaves it. */
class FullscreenToggle(area: html.Element, board: html.Element, button: html.Button, onChange: () => Unit) {

  private val body = dom.document.body
  private val doc = dom.document.asInstanceOf[js.Dynamic]
  private val root = dom.document.documentElement.asInstanceOf[js.Dynamic]
  private val ignore: js.Function1[js.Any, Unit] = _ => ()

  def isOn: Boolean = body.classList.contains("board-fullscreen")

  private def nativeActive: Boolean =
    Seq(doc.fullscreenElement, doc.webkitFullscreenElement).exists(e => !js.isUndefined(e) && e != null)

  private def call(target: js.Dynamic, methods: String*): Unit =
    methods.find(m => js.typeOf(target.selectDynamic(m)) == "function").foreach { m =>
      val result = target.applyDynamic(m)()
      if (js.typeOf(result) == "object" && result != null && js.typeOf(result.`catch`) == "function")
        result.`catch`(ignore)
    }

  private def set(on: Boolean): Unit = if (on != isOn) {
    body.classList.toggle("board-fullscreen", on)
    updateLabel()
    onChange()
  }

  def enter(): Unit = {
    set(true)
    if (!nativeActive) call(root, "requestFullscreen", "webkitRequestFullscreen")
  }

  def exit(): Unit = {
    set(false)
    if (nativeActive) call(doc, "exitFullscreen", "webkitExitFullscreen")
  }

  def toggle(): Unit = if (isOn) exit() else enter()

  /** Keeps the button over the bottom-right corner of the visible board. */
  def place(): Unit = {
    val a = area.getBoundingClientRect()
    val b = board.getBoundingClientRect()
    button.style.right = s"${(a.right - b.right).max(0) + 8}px"
    button.style.bottom = s"${(a.bottom - b.bottom).max(0) + 8}px"
  }

  private def updateLabel(): Unit = {
    val label =
      if (isOn) I18n.t("Quitter le plein écran (F)", "Exit fullscreen (F)")
      else I18n.t("Plein écran (F)", "Fullscreen (F)")
    button.title = label
    button.setAttribute("aria-label", label)
    button.setAttribute("aria-pressed", isOn.toString)
  }

  button.onclick = (_: Event) => {
    toggle()
    button.blur()
  }

  private val onNativeChange: js.Function1[Event, Unit] = _ => {
    if (!nativeActive) set(false)
    onChange()
  }
  dom.document.addEventListener("fullscreenchange", onNativeChange)
  dom.document.addEventListener("webkitfullscreenchange", onNativeChange)

  dom.window.addEventListener("keydown", (e: KeyboardEvent) => {
    val tag = e.target.asInstanceOf[dom.Element].tagName
    val plain = !(e.ctrlKey || e.metaKey || e.altKey)
    if (tag != "INPUT" && plain && !e.repeat) e.key match {
      case "f" | "F" =>
        e.preventDefault()
        toggle()
      case "Escape" if isOn => exit()
      case _ =>
    }
  })

  I18n.onChange(updateLabel())
  updateLabel()
}
