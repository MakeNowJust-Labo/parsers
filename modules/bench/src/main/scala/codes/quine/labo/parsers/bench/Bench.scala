package codes.quine.labo.parsers
package bench

import org.openjdk.jmh.annotations._

class Bench {
  @Benchmark
  def measureAtto(): atto.ParseResult[JSON] =
    AttoJSONParser.parse(Bench.source)

  @Benchmark
  def measureContparse(): contparse.Parsed[JSON] =
    ContparseJSONParser.parse(Bench.source)

  @Benchmark
  def measureFastparse(): fastparse.Parsed[JSON] =
    FastparseJSONParser.parse(Bench.source)

  @Benchmark
  def measureFuncparse(): funcparse.Parsed[JSON] =
    FuncparseJSONParser.parse(Bench.source)

  @Benchmark
  def measureInlineparse(): inlineparse.Parsed[JSON] =
    InlineparseJSONParser.parse(Bench.source)

  @Benchmark
  def measureParserCombinators(): ParserCombinatorsJSONParser.ParseResult[JSON] =
    ParserCombinatorsJSONParser.parse(Bench.source)

  @Benchmark
  def measureStackparse(): stackparse.Parsed[JSON] =
    StackparseJSONParser.parse(Bench.source)
}

object Bench {
  val source: String = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/posts.json")).mkString
}
