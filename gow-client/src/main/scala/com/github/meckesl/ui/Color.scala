package com.github.meckesl.ui

import com.github.meckesl.repo.PlayerRepository
import PlayerRepository.Player

object Color {
  private case class Rgb(r: Int, g: Int, b: Int) {
    def css: String = s"rgb($r, $g, $b)"
    def alpha(a: Double): String = s"rgba($r, $g, $b, $a)"
  }
  private val red = Rgb(229, 72, 77)
  private val blue = Rgb(62, 142, 247)
  private val neutral = Rgb(139, 141, 152)
  private val move = Rgb(61, 214, 140)
  private val arsenal = Rgb(245, 165, 36)
  private val highlight = Rgb(255, 209, 102)

  val Black: String = Rgb(0, 0, 0).css
  val White: String = Rgb(255, 255, 255).css
  val Red: String = red.css
  val Blue: String = blue.css
  val Gray: String = neutral.css
  val Move: String = move.css
  val Arsenal: String = arsenal.css
  val Highlight: String = highlight.css
  val Shadow: String = Rgb(10, 12, 16).alpha(0.45)

  private def rgbOf(pl: Player): Rgb = pl match {
    case PlayerRepository.Red => red
    case PlayerRepository.Blue => blue
    case _ => neutral
  }

  def fromPlayer(pl: Player): String = rgbOf(pl).css
  def fromPlayer(pl: Player, alpha: Double): String = rgbOf(pl).alpha(alpha)
}
