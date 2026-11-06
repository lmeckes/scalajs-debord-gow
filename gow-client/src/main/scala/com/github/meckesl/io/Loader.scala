package com.github.meckesl.io

import com.github.meckesl.{BoardLoader, Square}
import com.github.meckesl.repo.{PlayerRepository, TileRepository}
import com.github.meckesl.repo.TileRepository.{Tile, VoidTile}
import org.scalajs.dom
import org.scalajs.dom._
import org.scalajs.dom.{HTMLImageElement, XMLHttpRequest}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

object Loader {

  val imageCache: mutable.HashMap[Tile, HTMLImageElement] = new mutable.HashMap()
  private val resourcePath = "target/scala-2.13/classes"

  def getSoundUrl(sq: Square, sound: String): String = {
    s"$resourcePath/sounds/${sq.unit.char.toLower}/$sound.mp3"
  }

  def getSoundUrl(sound: String): String = {
    s"$resourcePath/sounds/$sound.mp3"
  }

  def tileUrl(tile: Tile): String = {
    if (tile.equals(VoidTile))
      s"$resourcePath/tiles/dot/0.png"
    else if (TileRepository.units.contains(tile)) {
      val playerTag = if (tile.player.equals(PlayerRepository.Red)) "r" else ""
      s"$resourcePath/tiles/${tile.char.toString.toLowerCase}/0$playerTag.png"
    } else
      s"$resourcePath/tiles/${tile.char.toString.toLowerCase}/0.png"
  }

  def loadTileAsync(t: Tile, callback: HTMLImageElement => Unit): Unit = {
    if (imageCache.contains(t)) {
      callback(imageCache(t))
    } else {
      val image = dom.document.createElement("img").asInstanceOf[HTMLImageElement]
      image.src = tileUrl(t)
      image.onload = (_: dom.Event) => {
        imageCache.put(t, image)
        callback(image)
      }
    }
  }

  def getStartingGamePosition(boardFile: String, unitFile: String, xWidth: Int): Future[Boolean] = {

    def fetch(file: String): Future[String] = {
      val promise: Promise[String] = Promise()
      val xhr = new XMLHttpRequest()
      xhr.open("GET", s"$resourcePath/$file")
      xhr.onload = { (_: Event) =>
        if (xhr.status == 200) promise.success(xhr.responseText)
        else promise.failure(new RuntimeException(s"cannot read $file"))
      }
      xhr.send()
      promise.future
    }

    for {
      board <- fetch(boardFile)
      units <- fetch(unitFile)
    } yield {
      BoardLoader.load(board, units, xWidth)
      true
    }
  }

}
