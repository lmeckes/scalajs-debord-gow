package com.github.meckesl.ui

import com.github.meckesl.Square
import com.github.meckesl.io.Loader
import com.github.meckesl.repo.PlayerRepository
import com.github.meckesl.repo.PlayerRepository.{Neutral, Player}
import com.github.meckesl.repo.TileRepository.{Fortress, MountainPass, Tile, VoidTile}
import org.scalajs.dom
import org.scalajs.dom.html

/** Side-panel card describing the hovered square, or the odds of the attack being aimed. */
class InfoPanel(root: html.Element) {

  private def placeholder =
    if (dom.window.matchMedia("(hover: none)").matches)
      I18n.t("""<div class="info-empty">Touchez une unité pour voir sa force.<br>Touchez l'une des vôtres, puis sa destination ou sa cible.</div>""",
        """<div class="info-empty">Tap a unit to see its strength.<br>Tap one of yours, then where to move or attack.</div>""")
    else
      I18n.t("""<div class="info-empty">Survolez une unité pour voir sa force.<br>Faites glisser l'une des vôtres pour la déplacer ou attaquer.</div>""",
        """<div class="info-empty">Hover a unit to see its strength.<br>Drag one of yours to move or attack.</div>""")

  def name(t: Tile): String = t.char.toUpper match {
    case 'A' => "Arsenal"
    case 'R' => I18n.t("Relais", "Relay")
    case 'E' => I18n.t("Relais rapide", "Swift relay")
    case 'C' => I18n.t("Canon", "Cannon")
    case 'N' => I18n.t("Canon rapide", "Swift cannon")
    case 'I' => I18n.t("Infanterie", "Infantry")
    case 'V' => I18n.t("Cavalerie", "Cavalry")
    case 'F' => I18n.t("Forteresse", "Fortress")
    case 'M' => I18n.t("Montagne", "Mountain")
    case '=' => I18n.t("Passe de montagne", "Mountain pass")
    case _ => I18n.t("Terrain dégagé", "Open ground")
  }

  private def team(p: Player): String = p match {
    case PlayerRepository.Red => "red"
    case PlayerRepository.Blue => "blue"
    case _ => "neutral"
  }

  private def teamName(p: Player): String = p match {
    case PlayerRepository.Red => I18n.colour('r')
    case PlayerRepository.Blue => I18n.colour('b')
    case _ => I18n.t("Neutre", "Neutral")
  }

  private def portrait(sq: Square, cls: String = "portrait"): String = {
    val terrain = if (sq.terrain == VoidTile) "" else s"""<img class="terrain" src="${Loader.tileUrl(sq.terrain)}" alt="">"""
    val unit = if (sq.unit == VoidTile) "" else s"""<img class="unit" src="${Loader.tileUrl(sq.unit)}" alt="">"""
    s"""<div class="$cls ${team(sq.unit.player)}">$terrain$unit</div>"""
  }

  private def stat(label: String, value: Int, max: Int): String =
    s"""<div class="stat"><span class="stat-label">$label</span>
       |<span class="stat-bar"><span style="width:${100 * value.min(max) / max}%"></span></span>
       |<span class="stat-value">$value</span></div>""".stripMargin

  private def attackLabel = I18n.t("Attaque", "Attack")
  private def defenceLabel = I18n.t("Défense", "Defence")
  private def movementLabel = I18n.t("Mouvement", "Movement")

  def clear(): Unit = root.innerHTML = placeholder

  I18n.onChange(clear())

  def showSquare(sq: Square): Unit = {
    if (sq.unit == VoidTile && sq.terrain == VoidTile) clear()
    else if (sq.unit == VoidTile) {
      val bonus = if (Set(Fortress, MountainPass).contains(sq.terrain)) I18n.t(s"+${sq.terrain.defense} en défense pour l'infanterie et l'artillerie", s"+${sq.terrain.defense} defence for infantry and artillery")
                  else if (sq.terrain.defense == 0 && sq.terrain != Fortress) I18n.t("Infranchissable", "Impassable")
                  else ""
      root.innerHTML =
        s"""<div class="unit-head">${portrait(sq)}<div class="meta">
           |<div class="unit-name">${name(sq.terrain)}</div><div class="unit-sub">${I18n.t("Terrain", "Terrain")}</div>
           |<div class="unit-status">$bonus</div></div></div>""".stripMargin
    } else {
      val u = sq.unit
      val status =
        if (!sq.isOnline) s"""<span class="badge warn">${I18n.t("Coupée", "Cut off")}</span>"""
        else if (sq.canMove) s"""<span class="badge ok">${I18n.t("Prête", "Ready")}</span>"""
        else if (sq.g.turnMovedUnits.contains(sq)) s"""<span class="badge">${I18n.t("Déplacée", "Moved")}</span>"""
        else s"""<span class="badge">${I18n.t("En communication", "In communication")}</span>"""
      val where = if (sq.terrain == VoidTile) "" else s" &middot; ${I18n.t("sur", "on")} ${name(sq.terrain)}"
      val stats =
        if (u.isCom) stat(movementLabel, u.speed, 2) + stat(defenceLabel, u.defense, 8)
        else stat(attackLabel, u.attack, 8) + stat(defenceLabel, u.defense, 8) + stat(movementLabel, u.speed, 2) + stat(I18n.t("Portée", "Range"), u.range, 3)
      root.innerHTML =
        s"""<div class="unit-head">${portrait(sq)}<div class="meta">
           |<div class="unit-name">${name(u)}</div>
           |<div class="unit-sub"><span class="chip ${team(u.player)}">${teamName(u.player)}</span>$where</div>
           |<div class="unit-status">$status</div></div></div>
           |<div class="stats">$stats</div>""".stripMargin
    }
  }

  def showAttack(target: Square): Unit = {
    val attack = target.canBeTargetOfStrength
    val defence = target.defenseStrength
    val (outcome, cls) = attack - defence match {
      case d if d > 1 => (I18n.t("Détruite", "Destroyed"), "win")
      case 1 if target.retreatSquares.isEmpty => (I18n.t("Détruite (pas de retraite)", "Destroyed (no retreat)"), "win")
      case 1 => (I18n.t("Contrainte à la retraite", "Forced to retreat"), "retreat")
      case _ => (I18n.t("L'attaque échoue", "Attack fails"), "fail")
    }
    val attackerTeam = target.canBeTargetOf.headOption.map(s => team(s.unit.player)).getOrElse(team(Neutral))
    def thumbs(squares: Iterable[Square]) = squares.toSeq.map(portrait(_, "thumb")).mkString
    val share = if (attack + defence == 0) 50 else 100 * attack / (attack + defence)
    root.innerHTML =
      s"""<div class="battle-title">${I18n.t("Attaque sur", "Attack on")} ${name(target.unit)}</div>
         |<div class="battle">
         |<div class="side $attackerTeam"><div class="side-label">${attackLabel}</div><div class="side-value">$attack</div><div class="thumbs">${thumbs(target.canBeTargetOf)}</div></div>
         |<div class="vs">${I18n.t("contre", "vs")}</div>
         |<div class="side ${team(target.unit.player)}"><div class="side-label">${defenceLabel}</div><div class="side-value">$defence</div><div class="thumbs">${thumbs(target.alliesInRange)}</div></div>
         |</div>
         |<div class="odds $attackerTeam"><span style="width:$share%"></span></div>
         |<div class="outcome $cls">$outcome</div>""".stripMargin
  }
}
