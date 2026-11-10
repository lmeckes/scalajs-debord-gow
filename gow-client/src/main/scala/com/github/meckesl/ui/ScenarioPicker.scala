package com.github.meckesl.ui

import com.github.meckesl.repo.ScenarioRepository.Scenario
import com.github.meckesl.repo.TileRepository._
import com.github.meckesl.repo.{PlayerRepository, RuleRepository, ScenarioRepository}
import org.scalajs.dom
import org.scalajs.dom.{html, Event, KeyboardEvent, MouseEvent}

/** Starting position dropdown with a minimap of each scenario; `onPreview` follows the highlighted option. */
class ScenarioPicker(root: html.Element, initial: Option[String], onPreview: Scenario => Unit, onChange: Scenario => Unit) {

  private def el[T](tag: String, cls: String): T = {
    val e = dom.document.createElement(tag)
    e.setAttribute("class", cls)
    e.asInstanceOf[T]
  }

  private val scenarios = ScenarioRepository.all
  private var selected = initial.flatMap(id => scenarios.indexWhere(_.id == id) match { case -1 => None; case i => Some(i) }).getOrElse(0)
  private var active = selected

  def value: Scenario = scenarios(selected)

  private def minimap(s: Scenario): html.Canvas = {
    val board = RuleRepository.scenarioBoards(s.id)
    val (w, h) = (board.width, board.height)
    val cell = 48.0 / h
    val ratio = dom.window.devicePixelRatio
    val canvas = el[html.Canvas]("canvas", "minimap")
    canvas.width = math.ceil(w * cell * ratio).toInt
    canvas.height = math.ceil(h * cell * ratio).toInt
    canvas.style.width = s"${w * cell}px"
    canvas.style.height = s"${h * cell}px"
    val ctx = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
    ctx.scale(ratio * cell, ratio * cell)
    def square(i: Int, inset: Double, colour: String): Unit = {
      ctx.fillStyle = colour
      ctx.fillRect(i % w + inset, i / w + inset, 1 - 2 * inset, 1 - 2 * inset)
    }
    board.terrain.zipWithIndex.foreach {
      case (Mountain, i) => square(i, 0, "#6b6253")
      case (MountainPass, i) => square(i, 0, "#4a4438")
      case (Fortress, i) => square(i, 0, "#8a7a5a")
      case _ =>
    }
    RuleRepository.scenarioUnits.getOrElse(s.id, Nil).zipWithIndex.foreach { case (t, i) =>
      val colour = if (t.player == PlayerRepository.Red) "#ff6b6f" else "#5aa2ff"
      if (t == RedArsenal || t == BlueArsenal) {
        square(i, -0.35, "#0e1116")
        square(i, -0.15, colour)
      } else if (t != VoidTile) square(i, 0.05, colour)
    }
    canvas
  }

  private def content(s: Scenario): Seq[dom.Node] = {
    val text = el[html.Span]("span", "picker-text")
    val name = el[html.Span]("span", "picker-name")
    name.textContent = s.name
    val sub = el[html.Span]("span", "picker-sub")
    sub.textContent = s.description
    text.appendChild(name)
    text.appendChild(sub)
    val frame = el[html.Span]("span", "minimap-frame")
    frame.appendChild(minimap(s))
    Seq(frame, text)
  }

  private val button = el[html.Button]("button", "picker-button")
  button.id = "scenarioButton"
  button.setAttribute("aria-haspopup", "listbox")
  button.setAttribute("aria-expanded", "false")
  button.setAttribute("aria-labelledby", "scenarioLabel scenarioButton")
  private val list = el[html.UList]("ul", "picker-list")
  list.id = "scenarioList"
  list.setAttribute("role", "listbox")
  list.setAttribute("aria-labelledby", "scenarioLabel")
  list.tabIndex = -1
  private val options = scenarios.zipWithIndex.map { case (s, i) =>
    val option = el[html.LI]("li", "picker-option")
    option.id = s"scenario-${s.id}"
    option.setAttribute("role", "option")
    content(s).foreach(option.appendChild)
    option.appendChild(el[html.Span]("span", "picker-check"))
    option.onmousemove = (_: MouseEvent) => if (active != i) highlight(i)
    option.onclick = (_: MouseEvent) => choose(i)
    list.appendChild(option)
    option
  }
  root.appendChild(button)
  root.appendChild(list)

  private def isOpen = root.classList.contains("open")

  private def renderButton(): Unit = {
    button.innerHTML = ""
    content(value).foreach(button.appendChild)
    button.appendChild(el[html.Span]("span", "picker-chevron"))
    options.zipWithIndex.foreach { case (o, i) => o.setAttribute("aria-selected", (i == selected).toString) }
  }

  private def highlight(i: Int): Unit = {
    active = i
    options.zipWithIndex.foreach { case (o, j) => o.classList.toggle("active", i == j) }
    list.setAttribute("aria-activedescendant", options(i).id)
    val o = options(i)
    if (o.offsetTop < list.scrollTop) list.scrollTop = o.offsetTop
    else if (o.offsetTop + o.offsetHeight > list.scrollTop + list.clientHeight)
      list.scrollTop = o.offsetTop + o.offsetHeight - list.clientHeight
    onPreview(scenarios(i))
  }

  private def open(): Unit = {
    root.classList.add("open")
    button.setAttribute("aria-expanded", "true")
    list.focus()
    highlight(selected)
  }

  private def close(focusButton: Boolean = true): Unit = if (isOpen) {
    root.classList.remove("open")
    button.setAttribute("aria-expanded", "false")
    if (focusButton) button.focus()
    if (active != selected) onPreview(value)
  }

  private def choose(i: Int): Unit = {
    val changed = i != selected
    selected = i
    active = i
    renderButton()
    close()
    if (changed) onChange(value)
  }

  button.onclick = (_: MouseEvent) => if (isOpen) close() else open()
  button.onkeydown = (e: KeyboardEvent) => e.key match {
    case "ArrowDown" | "ArrowUp" =>
      e.preventDefault()
      e.stopPropagation()
      open()
    case " " | "Enter" => e.stopPropagation()
    case _ =>
  }
  list.onkeydown = (e: KeyboardEvent) => {
    e.stopPropagation()
    e.key match {
      case "ArrowDown" => e.preventDefault(); highlight((active + 1) min (scenarios.size - 1))
      case "ArrowUp" => e.preventDefault(); highlight((active - 1) max 0)
      case "Home" => e.preventDefault(); highlight(0)
      case "End" => e.preventDefault(); highlight(scenarios.size - 1)
      case "Enter" | " " => e.preventDefault(); choose(active)
      case "Escape" => e.preventDefault(); close()
      case "Tab" => close(focusButton = false)
      case _ =>
    }
  }
  list.addEventListener("focusout", (e: dom.FocusEvent) =>
    if (e.relatedTarget == null || !root.contains(e.relatedTarget.asInstanceOf[dom.Node])) close(focusButton = false))
  dom.document.addEventListener("mousedown", (e: Event) =>
    if (isOpen && !root.contains(e.target.asInstanceOf[dom.Node])) close(focusButton = false))

  renderButton()
}
