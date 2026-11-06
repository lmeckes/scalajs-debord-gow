package com.github.meckesl.ui

import com.github.meckesl.repo.CardinalityRepository._
import com.github.meckesl.repo.{CardinalityRepository, RuleRepository}
import com.github.meckesl.{Point, Square}
import com.github.meckesl.repo.PlayerRepository.Neutral
import org.scalajs.dom
import org.scalajs.dom.html.Canvas
import org.scalajs.dom.HTMLImageElement

import scala.scalajs.js

/** One stacked board canvas, drawn in CSS pixels and backed at the device pixel ratio. */
class UiLayer(canvas: Canvas) {

  private val l = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]

  private var logicalSize = new Point(canvas.width, canvas.height)
  private var ratio = 1.0

  private def boardSize = new Point(RuleRepository.squareX, RuleRepository.squareY)
  def size: Point = logicalSize
  def tileSize: Point = size / boardSize

  def resize(s: Point, pixelRatio: Double): Unit = {
    logicalSize = s
    ratio = pixelRatio
    canvas.width = math.round(s.x * pixelRatio).toInt
    canvas.height = math.round(s.y * pixelRatio).toInt
    canvas.style.width = s"${s.x}px"
    canvas.style.height = s"${s.y}px"
    l.setTransform(ratio, 0, 0, ratio, 0, 0)
  }

  // Shadow offsets and blur ignore the canvas transform, so they are scaled by hand.
  private def shadow(color: String, blur: Double, dx: Double = 0, dy: Double = 0): Unit = {
    l.shadowColor = color
    l.shadowBlur = blur * ratio
    l.shadowOffsetX = dx * ratio
    l.shadowOffsetY = dy * ratio
  }

  private def roundRect(x: Double, y: Double, w: Double, h: Double, r: Double): Unit = {
    l.beginPath()
    l.moveTo(x + r, y)
    l.arcTo(x + w, y, x + w, y + h, r)
    l.arcTo(x + w, y + h, x, y + h, r)
    l.arcTo(x, y + h, x, y, r)
    l.arcTo(x, y, x + w, y, r)
    l.closePath()
  }

  private def tileRect(sq: Square, inset: Double): Unit = {
    val t = tileSize
    val p = sq.coords * t
    roundRect(p.x + inset, p.y + inset, t.x - inset * 2, t.y - inset * 2, t.x * 0.18)
  }

  /** Draws an image scaled to fit (keeping its aspect ratio), bottom-centred in a box. */
  private def drawFitted(image: HTMLImageElement, pos: Point, box: Point, scale: Double, flip: Boolean = false): Unit = {
    val fit = math.min(box.x / image.naturalWidth, box.y / image.naturalHeight) * scale
    val w = image.naturalWidth * fit
    val h = image.naturalHeight * fit
    val x = pos.x + (box.x - w) / 2
    val y = pos.y + box.y - h - (box.y * (1 - scale)) / 2
    if (flip) {
      l.save()
      l.translate(x + w, y)
      l.scale(-1, 1)
      l.drawImage(image, 0, 0, w, h)
      l.restore()
    } else
      l.drawImage(image, x, y, w, h)
  }

  def clearLayer(): Unit = {
    l.clearRect(0, 0, size.x, size.y)
  }

  def boardGrid(): Unit = {
    clearLayer()
    val t = tileSize
    l.fillStyle = "rgba(40, 32, 20, 0.05)"
    for (i <- 0 until RuleRepository.squareCount) {
      val p = Point.fromLinear(i, RuleRepository.squareX)
      if ((p.x + p.y) % 2 == 0) l.fillRect(p.x * t.x, p.y * t.y, t.x, t.y)
    }
    l.strokeStyle = "rgba(40, 32, 20, 0.14)"
    l.lineWidth = 1
    l.beginPath()
    for (x <- 1 until RuleRepository.squareX) {
      l.moveTo(x * t.x, 0)
      l.lineTo(x * t.x, size.y)
    }
    for (y <- 1 until RuleRepository.squareY) {
      l.moveTo(0, y * t.y)
      l.lineTo(size.x, y * t.y)
    }
    l.stroke()
    val (cx, cy) = (size.x / 2, size.y / 2)
    val vignette = l.createRadialGradient(cx, cy, math.min(cx, cy) * 0.6, cx, cy, math.max(cx, cy) * 1.15)
    vignette.addColorStop(0, "rgba(0, 0, 0, 0)")
    vignette.addColorStop(1, "rgba(0, 0, 0, 0.35)")
    l.fillStyle = vignette
    l.fillRect(0, 0, size.x, size.y)
  }

  def tileTerrain(sq: Square, image: HTMLImageElement): Unit = {
    val t = tileSize
    l.save()
    shadow(Color.Shadow, t.x * 0.12, t.x * 0.04, t.y * 0.06)
    drawFitted(image, sq.coords * t, t, 1.0)
    l.restore()
  }

  private def hasUnitMovedEastwards(sq: Square) =
    (sq.lastMoveDir.equals(CardinalityRepository.SOURCE) && sq.coords.x < boardSize.x / 2) ||
      sq.lastMoveDir.equals(CardinalityRepository.E)

  /** Draws a unit on its coloured base; `at` is the (possibly fractional) board position. */
  def tileUnit(sq: Square, image: HTMLImageElement, at: Point): Unit = {
    val t = tileSize
    val pos = at * t
    l.save()
    if (!sq.isOnline) {
      l.globalAlpha = 0.4
      l.asInstanceOf[js.Dynamic].filter = "grayscale(1)"
    }
    tileUnitBase(sq, pos)
    shadow(Color.Shadow, t.x * 0.1, t.x * 0.04, t.y * 0.05)
    drawFitted(image, pos, t, 0.9, hasUnitMovedEastwards(sq))
    l.restore()
  }

  private def tileUnitBase(sq: Square, pos: Point): Unit = {
    val player = sq.unit.player
    if (player != Neutral) {
      val t = tileSize
      val (cx, cy, rx, ry) = (pos.x + t.x / 2, pos.y + t.y * 0.88, t.x * 0.42, t.y * 0.12)
      val gradient = l.createRadialGradient(cx, cy, 0, cx, cy, rx)
      gradient.addColorStop(0, Color.fromPlayer(player, 0.8))
      gradient.addColorStop(1, Color.fromPlayer(player, 0.2))
      l.save()
      l.fillStyle = gradient
      l.beginPath()
      l.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2)
      l.fill()
      if (sq.canMove) {
        shadow(Color.fromPlayer(player), 8)
        l.strokeStyle = Color.fromPlayer(player)
        l.lineWidth = math.max(1.5, t.x / 22)
        l.stroke()
      }
      l.restore()
    }
  }

  def tileUnitHighlight(sq: Square): Unit = {
    val t = tileSize
    l.save()
    shadow(Color.Highlight, 12)
    l.strokeStyle = Color.Highlight
    l.lineWidth = math.max(2, t.x / 18)
    tileRect(sq, t.x * 0.04)
    l.stroke()
    l.restore()
  }

  def tileCommunication(sq: Square): Unit = {

    class sqCoords(sq: Square) {
      val nw: Point = sq.coords * tileSize
      val se: Point = nw + tileSize
      val n: Point = nw + new Point(tileSize.x / 2, 0)
      val s: Point = se - new Point(tileSize.x / 2, 0)
      val ne: Point = nw + new Point(tileSize.x, 0)
      val sw: Point = se - new Point(tileSize.x, 0)
      val e: Point = ne + new Point(0, tileSize.y / 2)
      val w: Point = sw - new Point(0, tileSize.y / 2)
      val source: Point = n + new Point(0, tileSize.y / 2)
    }

    val co = new sqCoords(sq)

    def drawLine(a: Point, b: Point): Unit = {
      l.beginPath()
      l.moveTo(a.x, a.y)
      l.lineTo(b.x, b.y)
      l.stroke()
    }

    l.lineWidth = math.max(1.5, tileSize.x / 22)
    l.lineCap = "round"
    sq.com.foreach(com => {
      l.strokeStyle = Color fromPlayer com._1
      com._2.foreach {
        case N => drawLine(co.source, co.n)
        case NE => drawLine(co.source, co.ne)
        case E => drawLine(co.source, co.e)
        case SE => drawLine(co.source, co.se)
        case S => drawLine(co.source, co.s)
        case SW => drawLine(co.source, co.sw)
        case W => drawLine(co.source, co.w)
        case NW => drawLine(co.source, co.nw)
        case SOURCE => drawLine(co.n, co.s)
                       drawLine(co.e, co.w)
                       drawLine(co.ne, co.sw)
                       drawLine(co.se, co.nw)
        case _ =>
      }
    })
  }

  def tileHighlight(sq: Square, color: String): Unit = {
    val t = tileSize
    l.save()
    l.fillStyle = color
    tileRect(sq, t.x * 0.06)
    l.fill()
    l.restore()
  }

  def moveHint(sq: Square, color: String): Unit = {
    val t = tileSize
    val c = sq.coords * t + t / 2
    l.save()
    shadow(color, 8)
    l.fillStyle = color
    l.globalAlpha = 0.85
    l.beginPath()
    l.arc(c.x, c.y, t.x * 0.11, 0, Math.PI * 2)
    l.fill()
    l.restore()
  }

  def targetHint(sq: Square): Unit = {
    val t = tileSize
    val c = sq.coords * t + t / 2
    l.save()
    shadow(Color.Red, 10)
    l.strokeStyle = Color.Red
    l.lineWidth = math.max(2, t.x / 16)
    l.setLineDash(js.Array(t.x * 0.14, t.x * 0.1))
    l.beginPath()
    l.arc(c.x, c.y, t.x * 0.42, 0, Math.PI * 2)
    l.stroke()
    l.restore()
  }

  def drawActionArrow(source: Square, dest: Square): Unit = {
    val t = tileSize
    val from = source.coords * t + t / 2
    val to = dest.coords * t + t / 2
    val valid = source.canMoveTo(dest) || source.canTakeArsenal(dest) || source.canAttack(dest)
    val color =
      if (source.canMoveTo(dest)) Color.Move
      else if (source.canTakeArsenal(dest)) Color.Arsenal
      else if (source.canAttack(dest)) Color.Red
      else Color.Gray
    val angle = Math.atan2(to.y - from.y, to.x - from.x)
    val head = math.max(10, t.x * 0.34)
    val lineEnd = to - new Point(Math.cos(angle), Math.sin(angle)) * (head * 0.7)

    l.save()
    shadow(color, 12)
    l.strokeStyle = color
    l.fillStyle = color
    l.globalAlpha = if (valid) 0.95 else 0.6
    l.lineWidth = math.max(3, t.x / 9)
    l.lineCap = "round"
    if (!valid) l.setLineDash(js.Array(t.x * 0.15, t.x * 0.2))
    l.beginPath()
    l.arc(from.x, from.y, l.lineWidth, 0, Math.PI * 2)
    l.fill()
    l.beginPath()
    l.moveTo(from.x, from.y)
    l.lineTo(lineEnd.x, lineEnd.y)
    l.stroke()
    l.translate(to.x, to.y)
    l.rotate(angle)
    l.beginPath()
    l.moveTo(0, 0)
    l.lineTo(-head, -head * 0.6)
    l.lineTo(-head * 0.75, 0)
    l.lineTo(-head, head * 0.6)
    l.closePath()
    l.fill()
    l.restore()
  }

}
