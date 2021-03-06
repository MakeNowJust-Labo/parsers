package codes.quine.labo.parsers
package funcparse

import scala.annotation.tailrec

import common.{Util, Optioner, Repeater, Sequencer}

sealed trait Parser[+T] extends (Parsing => Parsing.Step[T]) {
  def ! : Parser[String] = Parser.Capture(this)

  def ~[U, V](parser2: Parser[U])(implicit seq: Sequencer.Aux[T, U, V]): Parser[V] =
    Parser.Sequence(this, parser2, false, seq)

  def ~/[U, V](parser2: Parser[U])(implicit seq: Sequencer.Aux[T, U, V]): Parser[V] =
    Parser.Sequence(this, parser2, true, seq)

  def / : Parser[T] = Parser.Cut(this)

  def rep[V](implicit rep: Repeater.Aux[T, V]): Parser[V] =
    Parser.Repeat(this, 0, Int.MaxValue, rep)

  def rep[V](min: Int, max: Int = Int.MaxValue)(implicit rep: Repeater.Aux[T, V]): Parser[V] =
    Parser.Repeat(this, min, max, rep)

  def count[V](times: Int)(implicit rep: Repeater.Aux[T, V]): Parser[V] =
    Parser.Count(this, times, rep)

  def ?[V](implicit opt: Optioner.Aux[T, V]): Parser[V] =
    Parser.Optional(this, opt)

  def |[U >: T](parser2: Parser[U]): Parser[U] =
    Parser.Alternative(this, parser2)

  def map[U](f: T => U): Parser[U] =
    Parser.Map(this, f)

  def flatMap[U](f: T => Parser[U]): Parser[U] =
    Parser.FlatMap(this, f)

  def filter(f: T => Boolean): Parser[T] =
    Parser.Filter(this, f)

  def named(name: String): Parser[T] = Parser.Named(this, name)
}

object Parser {
  final case class Literal(string: String) extends Parser[Unit] {
    def apply(p: Parsing): Parsing.Step[Unit] =
      if (p.input.startsWith(string, p.pos)) Parsing.Success((), p.advance(string.length), false)
      else Parsing.Failure(p.unexpected(name), false)

    lazy val name: String = "\"" + Util.escape(string) + "\""
    override def toString: String = name
  }

  final case class CharIn(string: String) extends Parser[Unit] {
    def apply(p: Parsing): Parsing.Step[Unit] =
      if (p.pos < p.input.length && string.contains(p.input.charAt(p.pos))) Parsing.Success((), p.advance(1), false)
      else Parsing.Failure(p.unexpected(name), false)

    lazy val name: String = "CharIn(\"" + Util.escape(string) + "\")"
    override def toString: String = name
  }

  final case class CharPred(f: Char => Boolean) extends Parser[Unit] {
    def apply(p: Parsing): Parsing.Step[Unit] =
      if (p.pos < p.input.length && f(p.input.charAt(p.pos))) Parsing.Success((), p.advance(1), false)
      else Parsing.Failure(p.unexpected(toString), false)

    override def toString: String = "CharPred(...)"
  }

  final case class CharsWhileIn(string: String, min: Int) extends Parser[Unit] {
    def apply(p: Parsing): Parsing.Step[Unit] = {
      var pos = p.pos
      while (pos < p.input.length && string.contains(p.input.charAt(pos))) {
        pos += 1
      }
      if (min <= pos - p.pos) Parsing.Success((), p.reset(pos), false)
      else Parsing.Failure(p.unexpected(toString), false)
    }

    lazy val name: String = "CharsWhileIn(\"" + Util.escape(string) + "\")"
    override def toString: String = name
  }

  final case class CharsWhile(f: Char => Boolean, min: Int) extends Parser[Unit] {
    def apply(p: Parsing): Parsing.Step[Unit] = {
      var pos = p.pos
      while (pos < p.input.length && f(p.input.charAt(pos))) {
        pos += 1
      }
      if (min <= pos - p.pos) Parsing.Success((), p.reset(pos), false)
      else Parsing.Failure(p.unexpected(toString), false)
    }

    override def toString: String = "CharsWhile(...)"
  }

  case object AnyChar extends Parser[Unit] {
    def apply(p: Parsing): Parsing.Step[Unit] =
      if (p.pos < p.input.length) Parsing.Success((), p.advance(1), false)
      else Parsing.Failure(p.unexpected(toString), false)

    override def toString: String = "AnyChar"
  }

  case object Start extends Parser[Unit] {
    def apply(p: Parsing): Parsing.Step[Unit] =
      if (p.pos == 0) Parsing.Success((), p, false)
      else Parsing.Failure(p.unexpected(toString), false)

    override def toString: String = "Start"
  }

  case object End extends Parser[Unit] {
    def apply(p: Parsing): Parsing.Step[Unit] =
      if (p.pos == p.input.length) Parsing.Success((), p, false)
      else Parsing.Failure(p.unexpected(toString), false)

    override def toString: String = "End"
  }

  final case class Capture(parser: Parser[Any]) extends Parser[String] {
    def apply(p0: Parsing): Parsing.Step[String] =
      parser.apply(p0) match {
        case Parsing.Success(_, p1, cut) => Parsing.Success(p0.input.slice(p0.pos, p1.pos), p1, cut)
        case Parsing.Failure(p1, cut)    => Parsing.Failure(p1, cut)
      }

    override def toString: String =
      parser match {
        case _: Sequence[_, _, _] | _: Alternative[_] => s"($parser).!"
        case _                                        => s"$parser.!"
      }
  }

  final case class Sequence[T, U, V](parser1: Parser[T], parser2: Parser[U], cut: Boolean, seq: Sequencer.Aux[T, U, V])
      extends Parser[V] {
    def apply(p0: Parsing): Parsing.Step[V] = parser1.apply(p0) match {
      case Parsing.Success(v1, p1, cut1) =>
        parser2.apply(p1) match {
          case Parsing.Success(v2, p2, cut2) => Parsing.Success(seq.apply(v1, v2), p2, cut1 || cut || cut2)
          case Parsing.Failure(p2, cut2)     => Parsing.Failure(p2, cut1 || cut || cut2)
        }
      case Parsing.Failure(p1, cut1) => Parsing.Failure(p1, cut1)
    }

    override def toString: String = {
      def paren(p: Parser[Any]): String =
        p match {
          case _: Sequence[_, _, _] | _: Alternative[_] => "(" + p + ")"
          case _                                        => p.toString
        }
      @tailrec def loop(p1: Parser[Any], s: String, cut: Boolean): String =
        p1 match {
          case Sequence(p1, p2, _, _) => loop(p1, s"${paren(p2)} ${if (cut) "~/" else "~"} $s", cut)
          case _                      => s"${paren(p1)} ${if (cut) "~/" else "~"} $s"
        }
      loop(parser1, paren(parser2), cut)
    }
  }

  final case class Cut[T](parser: Parser[T]) extends Parser[T] {
    def apply(p0: Parsing): Parsing.Step[T] =
      parser.apply(p0) match {
        case Parsing.Success(v, p1, _) => Parsing.Success(v, p1, true)
        case Parsing.Failure(p1, cut1) => Parsing.Failure(p1, cut1)
      }

    override def toString: String =
      parser match {
        case _: Sequence[_, _, _] | _: Alternative[_] => s"($parser)./"
        case _                                        => s"$parser./"
      }
  }

  final case class NoCut[T](parser: Parser[T]) extends Parser[T] {
    def apply(p0: Parsing): Parsing.Step[T] =
      parser.apply(p0) match {
        case Parsing.Success(v, p1, _) => Parsing.Success(v, p1, false)
        case Parsing.Failure(p1, _)    => Parsing.Failure(p1, false)
      }

    override def toString: String = s"NoCut($parser)"
  }

  private def parseRepeat[T, V](
      parser: Parser[T],
      min: Int,
      max: Int,
      rep: Repeater.Aux[T, V],
      p: Parsing
  ): Parsing.Step[V] = {
    @tailrec def loop(p0: Parsing, as: V, cut: Boolean, n: Int): Parsing.Step[V] =
      if (n == max) Parsing.Success(as, p0, cut)
      else if (n < min) parser.apply(p0) match {
        case Parsing.Success(a, p1, cut1) => loop(p1, rep.append(as, a), cut || cut1, n + 1)
        case Parsing.Failure(p1, cut1)    => Parsing.Failure(p1, cut || cut1)
      }
      else
        parser.apply(p0) match {
          case Parsing.Success(_, p1, true) if p0.pos == p1.pos  => Parsing.Failure(p1.fail("null repeat"), true)
          case Parsing.Success(_, p1, false) if p0.pos == p1.pos => Parsing.Success(as, p1, cut)
          case Parsing.Success(a, p1, cut1)                      => loop(p1, rep.append(as, a), cut || cut1, n + 1)
          case Parsing.Failure(p1, true)                         => Parsing.Failure(p1, true)
          case Parsing.Failure(p1, false)                        => Parsing.Success(as, p1.reset(p0.pos), cut)
        }
    loop(p, rep.empty, false, 0)
  }

  final case class Repeat[T, V](parser: Parser[T], min: Int, max: Int, rep: Repeater.Aux[T, V]) extends Parser[V] {
    def apply(p: Parsing): Parsing.Step[V] = parseRepeat(parser, min, max, rep, p)

    override def toString: String = {
      val method = (min, max) match {
        case (0, Int.MaxValue) => "rep"
        case (m, Int.MaxValue) => s"rep($m)"
        case (m, n)            => s"rep($m, $n)"
      }
      parser match {
        case _: Sequence[_, _, _] | _: Alternative[_] => s"($parser).$method"
        case _                                        => s"$parser.$method"
      }
    }
  }

  final case class Count[T, V](parser: Parser[T], times: Int, rep: Repeater.Aux[T, V]) extends Parser[V] {
    def apply(p: Parsing): Parsing.Step[V] = parseRepeat(parser, times, times, rep, p)

    override def toString: String =
      parser match {
        case _: Sequence[_, _, _] | _: Alternative[_] => s"($parser).count($times)"
        case _                                        => s"$parser.count($times)"
      }
  }

  final case class Optional[T, V](parser: Parser[T], opt: Optioner.Aux[T, V]) extends Parser[V] {
    def apply(p0: Parsing): Parsing.Step[V] =
      parser.apply(p0) match {
        case Parsing.Success(v, p1, cut) => Parsing.Success(opt.some(v), p1, cut)
        case Parsing.Failure(p1, true)   => Parsing.Failure(p1, true)
        case Parsing.Failure(p1, false)  => Parsing.Success(opt.none, p1.reset(p0.pos), false)
      }

    override def toString: String =
      parser match {
        case _: Sequence[_, _, _] | _: Alternative[_] => s"($parser).?"
        case _                                        => s"$parser.?"
      }
  }

  final case class Alternative[T](parser1: Parser[T], parser2: Parser[T]) extends Parser[T] {
    def apply(p0: Parsing): Parsing.Step[T] =
      parser1.apply(p0) match {
        case Parsing.Success(a, p1, cut1) => Parsing.Success(a, p1, cut1)
        case Parsing.Failure(p1, true)    => Parsing.Failure(p1, true)
        case Parsing.Failure(p1, false) =>
          parser2.apply(p1.reset(p0.pos)) match {
            case Parsing.Success(a, p2, cut2) => Parsing.Success(a, p2, cut2)
            case Parsing.Failure(p2, cut2)    => Parsing.Failure(p2, cut2)
          }
      }

    override def toString: String = s"$parser1 | $parser2"
  }

  final case class LookAhead[T](parser: Parser[T]) extends Parser[T] {
    def apply(p0: Parsing): Parsing.Step[T] =
      parser.apply(p0) match {
        case Parsing.Success(a, p1, cut1) => Parsing.Success(a, p1.reset(p0.pos), cut1)
        case Parsing.Failure(p1, cut1)    => Parsing.Failure(p1, cut1)
      }

    override def toString: String = s"&?($parser)"
  }

  final case class NegativeLookAhead(parser: Parser[Any]) extends Parser[Unit] {
    def apply(p0: Parsing): Parsing.Step[Unit] =
      // Set `p0.errorPos` as `Int.MaxValue` for preventing to override an error message.
      // Generally an error message in negative look-ahead is not useful.
      parser.apply(p0.copy(errorPos = Int.MaxValue)) match {
        case Parsing.Success(_, p1, cut1) =>
          Parsing.Failure(p1.copy(errorPos = p0.errorPos).fail(message, p0.pos), cut1)
        case Parsing.Failure(p1, cut1) => Parsing.Success((), p1.copy(errorPos = p0.errorPos).reset(p0.pos), cut1)
      }

    lazy val message: String = s"unexpected: $parser"
    override def toString: String = s"&!($parser)"
  }

  final case class Map[T, U](parser: Parser[T], f: T => U) extends Parser[U] {
    def apply(p0: Parsing): Parsing.Step[U] =
      parser.apply(p0) match {
        case Parsing.Success(a, p1, cut1) => Parsing.Success(f(a), p1, cut1)
        case Parsing.Failure(p1, cut1)    => Parsing.Failure(p1, cut1)
      }

    override def toString: String =
      parser match {
        case _: Sequence[_, _, _] | _: Alternative[_] => s"($parser).map(...)"
        case _                                        => s"$parser.map(...)"
      }
  }

  final case class FlatMap[T, U](parser: Parser[T], f: T => Parser[U]) extends Parser[U] {
    def apply(p0: Parsing): Parsing.Step[U] =
      parser.apply(p0) match {
        case Parsing.Success(v1, p1, cut1) =>
          f(v1).apply(p1) match {
            case Parsing.Success(v2, p2, cut2) => Parsing.Success(v2, p2, cut1 || cut2)
            case Parsing.Failure(p2, cut2)     => Parsing.Failure(p2, cut1 || cut2)
          }
        case Parsing.Failure(p1, cut1) => Parsing.Failure(p1, cut1)
      }

    override def toString: String =
      parser match {
        case _: Sequence[_, _, _] | _: Alternative[_] => s"($parser).flatMap(...)"
        case _                                        => s"$parser.flatMap(...)"
      }
  }

  final case class Filter[T](parser: Parser[T], f: T => Boolean) extends Parser[T] {
    def apply(p0: Parsing): Parsing.Step[T] = parser.apply(p0) match {
      case Parsing.Success(a, p1, cut1) if f(a) => Parsing.Success(a, p1, cut1)
      case Parsing.Success(_, p1, cut1)         => Parsing.Failure(p1.fail("filter", p0.pos), cut1)
      case Parsing.Failure(p1, cut1)            => Parsing.Failure(p1, cut1)
    }

    override def toString: String = {
      parser match {
        case _: Sequence[_, _, _] | _: Alternative[_] => s"($parser).filter(...)"
        case _                                        => s"$parser.filter(...)"
      }
    }
  }

  final case class Delay[T](f: () => Parser[T], name: String) extends Parser[T] {
    lazy val parser: Parser[T] = f()

    def apply(p: Parsing): Parsing.Step[T] = parser.apply(p)

    override def toString: String = name
  }

  final case class Named[T](parser: Parser[T], name: String) extends Parser[T] {
    def apply(p0: Parsing): Parsing.Step[T] =
      parser.apply(p0.named(name)) match {
        case Parsing.Success(a, p1, cut1) => Parsing.Success(a, p1.named(p0.name, p0.namePos), cut1)
        case Parsing.Failure(p1, cut1)    => Parsing.Failure(p1.named(p0.name, p0.namePos), cut1)
      }

    override def toString: String = {
      val args = "\"" + Util.escape(name) + "\""
      parser match {
        case _: Sequence[_, _, _] | _: Alternative[_] => s"($parser).named($args)"
        case _                                        => s"$parser.named($args)"
      }
    }
  }

  final case class Pass[T](value: T) extends Parser[T] {
    def apply(p: Parsing): Parsing.Step[T] = Parsing.Success(value, p, false)

    override def toString: String = if (value == ()) "Pass" else s"Pass($value)"
  }

  final case class Fail(message: String) extends Parser[Nothing] {
    def apply(p: Parsing): Parsing.Step[Nothing] = Parsing.Failure(p.fail(message), false)

    override def toString: String =
      if (message == "fail") "Fail" else "Fail(\"" + Util.escape(message) + "\")"
  }

}
