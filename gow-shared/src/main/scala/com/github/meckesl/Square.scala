package com.github.meckesl

import com.github.meckesl.repo.{CardinalityRepository, TileRepository}
import com.github.meckesl.repo.CardinalityRepository._
import com.github.meckesl.repo.PlayerRepository.{Blue, Neutral, Player, Red}
import com.github.meckesl.repo.TileRepository._

import scala.collection.{Seq, immutable, mutable}

case class Square(index: Int, terrain: Tile, g: Game) {

  var unit: Tile = VoidTile
  var lastMoveDir: Cardinality = CardinalityRepository.SOURCE
  val coords: Point = Point.fromLinear(index, g.board.width)
  val com: Map[Player, mutable.Set[Cardinality]] = immutable.HashMap(Blue -> mutable.Set[Cardinality](), Red -> mutable.Set[Cardinality]())

  def isOnline: Boolean = com(unit.player).nonEmpty || unit.isCom

  def isCurrentTurn: Boolean = g.turnPlayer.equals(unit.player)

  /** Only combat units in communication can start an attack. */
  def canStartAttack: Boolean =
    isCurrentTurn && g.forcedRetreat.isEmpty && !unit.isCom && isOnline

  def canAttack(dest: Square): Boolean =
    canStartAttack && targetsInAttackRange.contains(dest)

  def mustRetreat: Boolean = g.forcedRetreat.contains(this)

  def retreatSquares: Set[Square] = inRange(1).filter(_.unit.equals(VoidTile))

  def canTakeArsenal(dest: Square): Boolean =
    !unit.isCom && canMoveTo(dest, Set(BlueArsenal, RedArsenal)
      .filterNot(_.player.equals(unit.player)).head)

  /** While a retreat is pending, only the unit that must retreat may move. */
  def canMove: Boolean =
    if (g.forcedRetreat.isDefined)
      mustRetreat && isCurrentTurn && g.turnRemainingMoves > 0
    else
      isCurrentTurn &&
        g.turnRemainingMoves > 0 &&
        !g.turnMovedUnits.exists(_.equals(this)) &&
        unit.speed > 0 &&
        isOnline

  def canMoveTo(dest: Square, allowedDest: Tile = VoidTile): Boolean =
    if (g.forcedRetreat.isDefined)
      allowedDest.equals(VoidTile) && canMove && retreatSquares.contains(dest)
    else
      canMove &&
        dest.unit.equals(allowedDest) &&
        !dest.terrain.equals(Mountain) &&
        reachableSquares.contains(dest)

  /** A mounted combat unit must stop on the first square outside communication. */
  def reachableSquares: Set[Square] = {
    var frontier = inRange(1)
    var reached = frontier
    2 to unit.speed foreach { _ =>
      frontier = frontier.filter(sq => unit.isCom || isSupplied(sq)).flatMap(_.inRange(1))
      reached ++= frontier
    }
    reached
  }

  private def isSupplied(sq: Square): Boolean = {
    val pl = unit.player
    sq.com(pl).nonEmpty || sq.inRange(1).exists(n => n != this && n.unit.player.equals(pl) && n.com(pl).nonEmpty)
  }

  def moveUnitTo(dest: Square): Boolean = {
    if (canMoveTo(dest)) {
      if (mustRetreat) g.forcedRetreat = None
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
      if (result > 1 || (result == 1 && retreatSquares.isEmpty)) {
        g.capturedUnits :+ unit
        unit = VoidTile
        g.refreshComLayer()
        if (result > 1) 2 else 3
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

  def canBeTargetOfStrength: Int =
    canBeTargetOf.toSeq.map(_.unit.attack).sum + chargingCavalry.size * TileRepository.cavalryChargeBonus

  /** Cavalry charges when adjacent to the target or in a straight line of up to 4 cavalry leading to it,
    * except against a fort or pass and from a fort. */
  def chargingCavalry: Set[Square] = {
    def charges(sq: Square) =
      null != sq &&
        Seq(BlueCavalry, RedCavalry).contains(sq.unit) &&
        !sq.unit.player.equals(unit.player) &&
        !sq.terrain.equals(Fortress) &&
        sq.isOnline
    if (Set(Fortress, MountainPass).contains(terrain) || unit.player.equals(Neutral)) Set.empty
    else (CardinalityRepository.all - SOURCE).flatMap { d =>
      Iterator.iterate(adjacentSquare(d))(sq => if (null == sq) null else sq.adjacentSquare(d))
        .take(TileRepository.cavalryChargeLength)
        .takeWhile(charges)
    }
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
    enemies.toSet ++ chargingCavalry
  }

  /** Communication units have no range but defend their own square. */
  def alliesInRange: Set[Square] = {
    val allies = mutable.Set[Square]()
    if (unit.isCom) allies += this
    1 to TileRepository.units.maxBy(_.range).range foreach (attackRange => {
      allies ++= inCombatRange(attackRange)
        .filter(_.unit.player.equals(unit.player))
        .filter(_.unit.range >= attackRange)
        .filter(_.isOnline)
    })
    allies.toSet
  }

  def adjacentSquare(c: Cardinality): Square = {
    val i = (new Point(c.x, c.y) + coords).toLinear(g.board.width)
    if ((i < g.gameSquares.size && i >= 0) &&
      (!(coords.x == 0 && Set(NW, SW, W).contains(c))) &&
      (!(coords.x == g.board.width - 1 && Seq(NE, SE, E).contains(c))))
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