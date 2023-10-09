package com.evolutiongaming.catshelper

import cats.data.NonEmptyMap
import cats.effect.Sync
import cats.{Applicative, Semigroup, ~>}
import org.slf4j.{Logger, MDC}

import scala.collection.immutable.SortedMap

trait Log[F[_]] {

  @inline def trace(msg: => String): F[Unit] = trace(msg, mdc = Log.Mdc.empty)

  @inline def debug(msg: => String): F[Unit] = debug(msg, mdc = Log.Mdc.empty)

  @inline def info(msg: => String): F[Unit] = info(msg, mdc = Log.Mdc.empty)

  @inline def warn(msg: => String): F[Unit] = warn(msg, mdc = Log.Mdc.empty)

  @inline def warn(msg: => String, cause: Throwable): F[Unit] = warn(msg, cause, mdc = Log.Mdc.empty)

  @inline def error(msg: => String): F[Unit] = error(msg, mdc = Log.Mdc.empty)

  @inline def error(msg: => String, cause: Throwable): F[Unit] = error(msg, cause, mdc = Log.Mdc.empty)

  def trace(msg: => String, mdc: Log.Mdc): F[Unit]

  def debug(msg: => String, mdc: Log.Mdc): F[Unit]

  def info(msg: => String, mdc: Log.Mdc): F[Unit]

  def warn(msg: => String, mdc: Log.Mdc): F[Unit]

  def warn(msg: => String, cause: Throwable, mdc: Log.Mdc): F[Unit]

  def error(msg: => String, mdc: Log.Mdc): F[Unit]

  def error(msg: => String, cause: Throwable, mdc: Log.Mdc): F[Unit]
}

object Log {

  sealed trait Mdc
  object Mdc {

    private object Empty extends Mdc
    private final case class Context(getValues: () => Map[String, String]) extends Mdc {
      lazy val values: SortedMap[String, String] = SortedMap(getValues().toSeq: _*)

      override def toString: String = s"MDC(${values.mkString(", ")})"

      override def hashCode(): Int = values.hashCode()

      override def equals(obj: Any): Boolean = obj match {
        case that: Context => this.values.equals(that.values)
        case _ => false
      }
    }

    val empty: Mdc = Empty

    type Record = (String, String)

    @deprecated("Use Mdc.Lazy.apply. If it's not enough - Mdc.Lazy.fromMap", "3.9.0")
    def apply(head: Record, tail: Record*): Mdc = Lazy.fromMap( (head +: tail).toMap)

    @deprecated("Use Mdc.Lazy.fromSeq", "3.9.0")
    def fromSeq(seq: Seq[Record]): Mdc = Lazy.fromSeq(seq)

    @deprecated("Use Mdc.Lazy.fromMap", "3.9.0")
    def fromMap(map: Map[String, String]): Mdc = Lazy.fromMap(map)

    object Lazy {
      def apply(v1: => Record): Mdc = fromMap(Map(v1))
      def apply(v1: => Record, v2: => Record): Mdc = fromMap(Map(v1, v2))
      def apply(v1: => Record, v2: => Record, v3: => Record): Mdc = fromMap(Map(v1, v2, v3))
      def apply(v1: => Record, v2: => Record, v3: => Record, v4: => Record): Mdc = fromMap(Map(v1, v2, v3, v4))
      def apply(v1: => Record, v2: => Record, v3: => Record, v4: => Record, v5: => Record): Mdc =
        fromMap(Map(v1, v2, v3, v4, v5))
      def apply(v1: => Record, v2: => Record, v3: => Record, v4: => Record, v5: => Record, v6: => Record): Mdc =
        fromMap(Map(v1, v2, v3, v4, v5, v6))
      def apply(v1: => Record, v2: => Record, v3: => Record, v4: => Record, v5: => Record, v6: => Record, v7: => Record): Mdc =
        fromMap(Map(v1, v2, v3, v4, v5, v6, v7))
      def apply(v1: => Record, v2: => Record, v3: => Record, v4: => Record, v5: => Record, v6: => Record, v7: => Record, v8: => Record): Mdc =
        fromMap(Map(v1, v2, v3, v4, v5, v6, v7, v8))
      def apply(v1: => Record, v2: => Record, v3: => Record, v4: => Record, v5: => Record, v6: => Record, v7: => Record, v8: => Record, v9: => Record): Mdc =
        fromMap(Map(v1, v2, v3, v4, v5, v6, v7, v8, v9))
      def apply(v1: => Record, v2: => Record, v3: => Record, v4: => Record, v5: => Record, v6: => Record, v7: => Record, v8: => Record, v9: => Record, v10: => Record): Mdc =
        fromMap(Map(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10))

      def fromSeq(seq: => Seq[Record]): Mdc = fromMap(seq.toMap)

      def fromMap(map: => Map[String, String]): Mdc = Context(() => map)
    }

    implicit final val mdcSemigroup: Semigroup[Mdc] = Semigroup.instance {
      case (Empty, right) => right
      case (left, Empty) => left
      case (Context(v1), Context(v2)) => Context(() => v1() ++ v2())
    }

    implicit final class MdcOps(val mdc: Mdc) extends AnyVal {

      def context: Option[NonEmptyMap[String, String]] = mdc match {
        case Empty => None
        case c: Context => NonEmptyMap.fromMap(c.values)
      }
    }
  }

  def apply[F[_]](implicit F: Log[F]): Log[F] = F

  def summon[F[_]](implicit F: Log[F]): Log[F] = F

  def apply[F[_]: Sync](logger: Logger): Log[F] = new Log[F] {

    def withMDC(mdc: Log.Mdc)(log: => Unit): Unit = {
      import Mdc.MdcOps
      mdc.context match {
        case None => log
        case Some(mdc) =>
          val backup = MDC.getCopyOfContextMap
          MDC.clear()
          mdc.toSortedMap foreach { case (k, v) => MDC.put(k, v) }
          log
          if (backup == null) MDC.clear() else MDC.setContextMap(backup)
      }
    }

    def trace(msg: => String, mdc: Log.Mdc) = {
      Sync[F].delay {
        if (logger.isTraceEnabled) withMDC(mdc) { logger.trace(msg) }
      }
    }

    def debug(msg: => String, mdc: Log.Mdc) = {
      Sync[F].delay {
        if (logger.isDebugEnabled) withMDC(mdc) { logger.debug(msg) }
      }
    }

    def info(msg: => String, mdc: Log.Mdc) = {
      Sync[F].delay {
        if (logger.isInfoEnabled) withMDC(mdc) { logger.info(msg) }
      }
    }

    def warn(msg: => String, mdc: Log.Mdc) = {
      Sync[F].delay {
        if (logger.isWarnEnabled) withMDC(mdc) { logger.warn(msg) }
      }
    }

    def warn(msg: => String, cause: Throwable, mdc: Log.Mdc) = {
      Sync[F].delay {
        if (logger.isWarnEnabled) withMDC(mdc) { logger.warn(msg, cause) }
      }
    }

    def error(msg: => String, mdc: Log.Mdc) = {
      Sync[F].delay {
        if (logger.isErrorEnabled) withMDC(mdc) { logger.error(msg) }
      }
    }

    def error(msg: => String, cause: Throwable, mdc: Log.Mdc) = {
      Sync[F].delay {
        if (logger.isErrorEnabled) withMDC(mdc) { logger.error(msg, cause) }
      }
    }
  }

  def const[F[_]](unit: F[Unit]): Log[F] = new Log[F] {

    def trace(msg: => String, mdc: Log.Mdc) = unit

    def debug(msg: => String, mdc: Log.Mdc) = unit

    def info(msg: => String, mdc: Log.Mdc) = unit

    def warn(msg: => String, mdc: Log.Mdc) = unit

    def warn(msg: => String, cause: Throwable, mdc: Log.Mdc) = unit

    def error(msg: => String, mdc: Log.Mdc) = unit

    def error(msg: => String, cause: Throwable, mdc: Log.Mdc) = unit
  }

  def empty[F[_]: Applicative]: Log[F] = const(Applicative[F].unit)

  implicit class LogOps[F[_]](val self: Log[F]) extends AnyVal {

    def mapK[G[_]](f: F ~> G): Log[G] = new Log[G] {

      def trace(msg: => String, mdc: Log.Mdc) = f(self.trace(msg, mdc))

      def debug(msg: => String, mdc: Log.Mdc) = f(self.debug(msg, mdc))

      def info(msg: => String, mdc: Log.Mdc) = f(self.info(msg, mdc))

      def warn(msg: => String, mdc: Log.Mdc) = f(self.warn(msg, mdc))

      def warn(msg: => String, cause: Throwable, mdc: Log.Mdc) = f(self.warn(msg, cause, mdc))

      def error(msg: => String, mdc: Log.Mdc) = f(self.error(msg, mdc))

      def error(msg: => String, cause: Throwable, mdc: Log.Mdc) = f(self.error(msg, cause, mdc))
    }

    def mapMsg(f: String => String): Log[F] = new Log[F] {

      def trace(msg: => String, mdc: Log.Mdc) = self.trace(f(msg), mdc)

      def debug(msg: => String, mdc: Log.Mdc) = self.debug(f(msg), mdc)

      def info(msg: => String, mdc: Log.Mdc) = self.info(f(msg), mdc)

      def warn(msg: => String, mdc: Log.Mdc) = self.warn(f(msg), mdc)

      def warn(msg: => String, cause: Throwable, mdc: Log.Mdc) = self.warn(f(msg), cause, mdc)

      def error(msg: => String, mdc: Log.Mdc) = self.error(f(msg), mdc)

      def error(msg: => String, cause: Throwable, mdc: Log.Mdc) = self.error(f(msg), cause, mdc)
    }

    def prefixed(prefix: String): Log[F] = mapMsg(msg => s"$prefix $msg")
  }
}
