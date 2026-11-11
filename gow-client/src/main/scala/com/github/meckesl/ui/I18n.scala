package com.github.meckesl.ui

import com.github.meckesl.repo.ScenarioRepository.Scenario
import org.scalajs.dom
import org.scalajs.dom.{html, Event}

/** UI language (French by default, English on request). Static HTML text is tagged with `data-lang`
  * and hidden by CSS from the `lang` attribute of `<html>`; Scala code picks its strings with [[t]]. */
object I18n {

  val languages: Seq[String] = Seq("fr", "en")
  private val storageKey = "gow.lang"

  private var current: String =
    Option(dom.window.localStorage.getItem(storageKey)).filter(languages.contains).getOrElse("fr")
  private var listeners = Vector.empty[() => Unit]

  def lang: String = current
  def isFrench: Boolean = current == "fr"

  def t(fr: String, en: String): String = if (isFrench) fr else en

  /** Runs `f` again whenever the language changes. */
  def onChange(f: => Unit): Unit = listeners :+= (() => f)

  def set(code: String): Unit = if (languages.contains(code) && code != current) {
    current = code
    dom.window.localStorage.setItem(storageKey, code)
    applyStatic()
    listeners.foreach(_())
  }

  /** Applies the language to `<html lang>`, the language switch and the `data-<lang>-*` attributes. */
  def applyStatic(): Unit = {
    val doc = dom.document
    doc.documentElement.setAttribute("lang", current)
    for (attr <- Seq("placeholder", "aria-label", "title")) {
      val nodes = doc.querySelectorAll(s"[data-$current-$attr]")
      (0 until nodes.length).foreach { i =>
        val e = nodes(i).asInstanceOf[dom.Element]
        e.setAttribute(attr, e.getAttribute(s"data-$current-$attr"))
      }
    }
    val buttons = doc.querySelectorAll("[data-set-lang]")
    (0 until buttons.length).foreach { i =>
      val b = buttons(i).asInstanceOf[html.Element]
      b.setAttribute("aria-pressed", (b.getAttribute("data-set-lang") == current).toString)
    }
  }

  def init(): Unit = {
    applyStatic()
    val buttons = dom.document.querySelectorAll("[data-set-lang]")
    (0 until buttons.length).foreach { i =>
      val b = buttons(i).asInstanceOf[html.Button]
      b.onclick = (_: Event) => {
        set(b.getAttribute("data-set-lang"))
        b.blur()
      }
    }
  }

  def colour(c: Char): String = if (c == 'r') t("Rouge", "Red") else t("Bleu", "Blue")

  def scenarioName(s: Scenario): String = s.id match {
    case "vanilla" => t("Classique", s.name)
    case _ => s.name
  }

  def scenarioDescription(s: Scenario): String = if (!isFrench) s.description else s.id match {
    case "vanilla" => "Ouverture par défaut"
    case "marengo" => "Class Wargames · Italie"
    case "austerlitz" => "Class Wargames · Moravie"
    case "escarmouche" => "Petit plateau · un fort"
    case _ => s.description
  }

  def playerStatus(status: String): String = status match {
    case "idle" => t("disponible", "idle")
    case "searching" => t("en recherche", "searching")
    case "playing" => t("en partie", "playing")
    case other => other
  }

  /** The server speaks English; known messages are translated here. */
  def serverMessage(message: String): String = if (!isFrench) message else message match {
    case "Choose a nickname first" => "Choisissez d'abord un pseudo"
    case "Already identified" => "Déjà identifié"
    case "You are already searching or playing" => "Vous êtes déjà en recherche ou en partie"
    case "Unknown scenario" => "Position de départ inconnue"
    case "No open game with this code" => "Aucune partie ouverte avec ce code"
    case "You cannot join your own game" => "Vous ne pouvez pas rejoindre votre propre partie"
    case "You are already playing" => "Vous êtes déjà en partie"
    case "You are not in a game" => "Vous n'êtes pas en partie"
    case "The game is over" => "La partie est terminée"
    case "Not your turn" => "Ce n'est pas votre tour"
    case "Illegal move" => "Déplacement interdit"
    case "Illegal arsenal capture" => "Prise d'arsenal interdite"
    case "Illegal attack" => "Attaque interdite"
    case "The attacked unit must retreat first" => "L'unité attaquée doit d'abord battre en retraite"
    case other => other
  }
}
