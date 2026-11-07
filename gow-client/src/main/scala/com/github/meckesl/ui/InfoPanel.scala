package com.github.meckesl.ui

import com.github.meckesl.Square
import com.github.meckesl.io.Loader
import com.github.meckesl.repo.PlayerRepository
import com.github.meckesl.repo.PlayerRepository.{Neutral, Player}
import com.github.meckesl.repo.TileRepository.{Fortress, MountainPass, Tile, VoidTile}
import org.scalajs.dom.html

/** Side-panel card describing the hovered square, or the odds of the attack being aimed. */
class InfoPanel(root: html.Element) {

  private val placeholder =
    """<div class="info-empty">Hover a unit to see its strength.<br>Drag one of yours to move or attack.</div>"""

  def name(t: Tile): String = t.char.toUpper match {
    case 'A' => "Arsenal"
    case 'R' => "Relay"
    case 'E' => "Swift relay"
    case 'C' => "Cannon"
    case 'N' => "Swift cannon"
    case 'I' => "Infantry"
    case 'V' => "Cavalry"
    case 'F' => "Fortress"
    case 'M' => "Mountain"
    case '=' => "Mountain pass"
    case _ => "Open ground"
  }

  private def team(p: Player): String = p match {
    case PlayerRepository.Red => "red"
    case PlayerRepository.Blue => "blue"
    case _ => "neutral"
  }

  private def teamName(p: Player): String = p match {
    case PlayerRepository.Red => "Red"
    case PlayerRepository.Blue => "Blue"
    case _ => "Neutral"
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

  def clear(): Unit = root.innerHTML = placeholder

  def showSquare(sq: Square): Unit = {
    if (sq.unit == VoidTile && sq.terrain == VoidTile) clear()
    else if (sq.unit == VoidTile) {
      val bonus = if (Set(Fortress, MountainPass).contains(sq.terrain)) s"+${sq.terrain.defense} defence for infantry and artillery"
                  else if (sq.terrain.defense == 0 && sq.terrain != Fortress) "Impassable"
                  else ""
      root.innerHTML =
        s"""<div class="unit-head">${portrait(sq)}<div class="meta">
           |<div class="unit-name">${name(sq.terrain)}</div><div class="unit-sub">Terrain</div>
           |<div class="unit-status">$bonus</div></div></div>""".stripMargin
    } else {
      val u = sq.unit
      val status =
        if (!sq.isOnline) """<span class="badge warn">Cut off</span>"""
        else if (sq.canMove) """<span class="badge ok">Ready</span>"""
        else if (sq.g.turnMovedUnits.contains(sq)) """<span class="badge">Moved</span>"""
        else """<span class="badge">In communication</span>"""
      val where = if (sq.terrain == VoidTile) "" else s" &middot; on ${name(sq.terrain)}"
      val stats =
        if (u.isCom) stat("Movement", u.speed, 2) + stat("Defence", u.defense, 8)
        else stat("Attack", u.attack, 8) + stat("Defence", u.defense, 8) + stat("Movement", u.speed, 2) + stat("Range", u.range, 3)
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
      case d if d > 1 => ("Destroyed", "win")
      case 1 if target.retreatSquares.isEmpty => ("Destroyed (no retreat)", "win")
      case 1 => ("Forced to retreat", "retreat")
      case _ => ("Attack fails", "fail")
    }
    val attackerTeam = target.canBeTargetOf.headOption.map(s => team(s.unit.player)).getOrElse(team(Neutral))
    def thumbs(squares: Iterable[Square]) = squares.toSeq.map(portrait(_, "thumb")).mkString
    val share = if (attack + defence == 0) 50 else 100 * attack / (attack + defence)
    root.innerHTML =
      s"""<div class="battle-title">Attack on ${name(target.unit)}</div>
         |<div class="battle">
         |<div class="side $attackerTeam"><div class="side-label">Attack</div><div class="side-value">$attack</div><div class="thumbs">${thumbs(target.canBeTargetOf)}</div></div>
         |<div class="vs">vs</div>
         |<div class="side ${team(target.unit.player)}"><div class="side-label">Defence</div><div class="side-value">$defence</div><div class="thumbs">${thumbs(target.alliesInRange)}</div></div>
         |</div>
         |<div class="odds $attackerTeam"><span style="width:$share%"></span></div>
         |<div class="outcome $cls">$outcome</div>""".stripMargin
  }
}
