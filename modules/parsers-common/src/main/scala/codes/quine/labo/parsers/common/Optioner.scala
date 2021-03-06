package codes.quine.labo.parsers.common

trait Optioner[-A] {
  type Result
  def none: Result
  def some(a: A): Result
}

object Optioner extends LowPriorityOptioner {
  type Aux[-A, R] = Optioner[A] {
    type Result = R
  }

  def apply[A](implicit opt: Optioner[A]): Aux[A, opt.Result] = opt

  implicit val unit: Aux[Unit, Unit] = new Optioner[Unit] {
    type Result = Unit
    def none: Unit = ()
    def some(a: Unit): Unit = ()
  }
}

private[common] trait LowPriorityOptioner {
  implicit def option[A]: Optioner.Aux[A, Option[A]] = OptionOptioner.asInstanceOf[Optioner.Aux[A, Option[A]]]

  private object OptionOptioner extends Optioner[Any] {
    type Result = Option[Any]
    def none: Option[Any] = None
    def some(a: Any): Option[Any] = Some(a)
  }
}
