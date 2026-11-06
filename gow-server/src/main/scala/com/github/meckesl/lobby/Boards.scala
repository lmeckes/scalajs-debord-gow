package com.github.meckesl.lobby

import com.github.meckesl.BoardLoader

import scala.io.Source

object Boards {
  def loadDefault(): Unit = {
    def read(file: String) = {
      val src = Source.fromResource(s"web/target/scala-2.13/classes/$file")
      try src.mkString finally src.close()
    }
    BoardLoader.load(read("init.board"), read("init.units"), 25)
  }
}
