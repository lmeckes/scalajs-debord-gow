package com.github.meckesl.lobby

import com.github.meckesl.BoardLoader
import com.github.meckesl.repo.ScenarioRepository

import scala.io.Source

object Boards {
  def loadDefault(): Unit = {
    def read(file: String) = {
      val src = Source.fromResource(s"web/target/scala-2.13/classes/$file")
      try src.mkString finally src.close()
    }
    BoardLoader.loadScenarios(ScenarioRepository.files.map(f => f -> read(f)).toMap)
  }
}
