package kamon.jmx.collector

import scala.io.Source

object HttpClient {
  def getLinesFromURL(url: String): List[String] = Source.fromURL(url).getLines.toList
}
