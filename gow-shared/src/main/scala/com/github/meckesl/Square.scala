package com.github.meckesl

import com.github.meckesl.repo.{CardinalityRepository, RuleRepository, TileRepository}
import com.github.meckesl.repo.CardinalityRepository._
import com.github.meckesl.repo.PlayerRepository.{Blue, Neutral, Player, Red}
import com.github.meckesl.repo.TileRepository._

import scala.collection.{Seq, immutable, mutable}

case class Square(index: Int, terrain: Tile, g: Game) {

  var unit: Tile = VoidTile
  var lastMoveDir: Cardinality = CardinalityRepository.SOURCE
  val coords: Point = Point.fromLinear(index, RuleRepository.squareX)
  val com: Map[Player, mutable.Set[Cardinality]] = immutable.HashMap(Blue -> mutable.Set[Cardinality](), Red -> mutable.Set[Cardinality]())

  def isOnline: Boolean = com(unit.player).nonEmpty || unit.isCom

  def isCurrentTurn: Boolean = g.turnPlayer.equals(unit.player)

  def canAttack(dest: Square): Boolean = {
    targetsInAttackRange.contains(dest)
  }

  def canTakeArsenal(dest: Square): Boolean = {
    canMoveTo(dest, Set(BlueArsenal, RedArsenal)
      .filterNot(_.player.equals(unit.player)).head)
  }

  def canMove: Boolean =
    isCurrentTurn &&
      g.turnRemainingMoves > 0 &&
      !g.turnMovedUnits.exists(_.equals(this)) &&
      unit.speed > 0 &&
      isOnline

  def canMoveTo(dest: Square, allowedDest: Tile = VoidTile): Boolean =
    canMove &&
      dest.unit.equals(allowedDest) &&
      !dest.terrain.equals(Mountain) &&
      inRange(unit.speed).contains(dest)

  def moveUnitTo(dest: Square): Boolean = {
    if (canMoveTo(dest)) {
      dest.unit = unit
      if (dest.coords.x > coords.x)
        dest.lastMoveDir = CardinalityRepository.E
      else if (dest.coords.x < coords.x)
        dest.lastMoveDir = CardinalityRepository.W
      else
        dest.lastMoveDir = lastMoveDir
      lastMoveDir = CardinalityRepository.SOURCE
      unit = VoidTile
      g.refreshComLayer()
      g.turnMovedUnits add dest
      g.turnRemainingMoves -= 1
      if (g.turnRemainingMoves == 0)
        g.nextTurn()
      true
    }
    else
      false
  }

  def takeArsenal(arsenal: Square): Unit = {
    arsenal.unit = unit
    unit = VoidTile
    g.refreshComLayer()
    g.nextTurn()
  }

  def launchAttackOn(): Int = {
    val as = canBeTargetOfStrength
    val ds = defenseStrength
    val result = as - ds

    val outcome =
      if (result > 1) {
        g.capturedUnits :+ unit
        unit = VoidTile
        g.refreshComLayer()
        2
      } else if (result == 1) {
        g.forcedRetreat = Some(this)
        1
      }
      else
        0
    g.nextTurn()
    outcome
  }

  def targetsInAttackRange: mutable.Set[Square] = inCombatRange(unit.range).filterNot(ir => {
    Set(unit.player, Neutral).contains(ir.unit.player)
  }).filterNot(ir => Set(RedArsenal, BlueArsenal).contains(ir.unit))

  def canBeTargetOfStrength: Int = {
    val attackers = canBeTargetOf
    val baseStrength = attackers.toSeq.map(_.unit.attack).sum
    if (!Set(Fortress, MountainPass).contains(terrain)) {
      val cavalryBonus = inRange(1)
        .intersect(attackers)
        .filterNot(_.terrain.equals(Fortress))
        .toSeq
        .map(_.unit)
        .count(Seq(BlueCavalry, RedCavalry).contains(_)) * TileRepository.cavalryChargeBonus
      baseStrength + cavalryBonus
    } else baseStrength
  }

  def defenseStrength: Int = alliesInRange.toSeq.map(sq => {
    var d = sq.unit.defense
    if (Set(Fortress, MountainPass).contains(sq.terrain))
      if (Set(BlueInfantry, RedInfantry, BlueCannon,
        RedCannon, BlueSwiftCannon, RedSwiftCannon).contains(sq.unit))
        d += sq.terrain.defense
    d
  }).sum

  def canBeTargetOf: Set[Square] = {
    val enemies = mutable.Set[Square]()
    1 to TileRepository.units.maxBy(_.range).range foreach (range => {
      enemies ++= inCombatRange(range)
        .filterNot(_.unit.player.equals(Neutral))
        .filterNot(_.unit.player.equals(unit.player))
        .filter(_.unit.range >= range)
        .filter(_.isOnline)
    })
    enemies.toSet
  }

  def alliesInRange: Set[Square] = {
    val allies = mutable.Set[Square]()
    1 to TileRepository.units.maxBy(_.range).range foreach (attackRange => {
      allies ++= inCombatRange(attackRange)
        .filter(_.unit.player.equals(unit.player))
        .filter(_.unit.range >= attackRange)
        .filter(_.isOnline)
    })
    allies.toSet
  }

  def adjacentSquare(c: Cardinality): Square = {
    val i = (new Point(c.x, c.y) + coords).toLinear(RuleRepository.squareX)
    if ((i < g.gameSquares.size && i >= 0) &&
      (!(coords.x == 0 && Set(NW, SW, W).contains(c))) &&
      (!(coords.x == RuleRepository.squareX - 1 && Seq(NE, SE, E).contains(c))))
      g.gameSquares(i)
    else
      null
  }

  private def inCombatRange(
                             range: Int,
                             dir: Set[Cardinality] = CardinalityRepository.all,
                             cursor: Square = this,
                             acc: mutable.Set[Square] = mutable.Set[Square]()): mutable.Set[Square] = {
    dir.foreach(d => {
      val cur = cursor.adjacentSquare(d)
      if (null != cur && !cur.terrain.equals(Mountain)) {
        acc += cur
        if (range > 1)
          acc ++= inCombatRange(range - 1, Set(d), cur, acc)
      }
    })
    acc
  }

  def inRange(r: Int): Set[Square] = {
    if (r.equals(1))
      CardinalityRepository.all.map(adjacentSquare)
        .filterNot(adj => null == adj || adj.terrain.equals(Mountain))
    else if (r.equals(2))
      inRange(1) flatMap (_.inRange(1))
    else if (r.equals(3))
      inRange(2) flatMap (_.inRange(1))
    else
      Set(this)
  }


}