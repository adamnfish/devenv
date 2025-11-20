package com.gu.devenv

import cats.data.EitherT

import scala.util.Try

/** Program helper types and functions
  *
  * These allow us to write for-comps that can exit early when a condition is met.
  *
  * We use Try to represent failure throughout the application, and EitherT to handle conditional
  * early exit from the for-comp, with a result value.
  *
  * Usage:
  *   - Use [[withConditions]] to wrap a for-comprehension that may exit early
  *   - Use [[condition]] to check a condition and exit early with a result value if not met
  *   - Add a [[liftF]] suffix to normal Try-returning operations so they can be used alongside
  *     condition checks.
  *
  * {{{
  * for {
  *   // exit early if some condition is not met
  *   _ <- condition(someConditionIsMet, earlyResultValue)
  *   // continue with normal operations
  *   value <- someOperation.liftF
  * } yield ...
  * }}}
  */
object Utils {
  def withConditions[Res](block: => EitherT[Try, Res, Res]): Try[Res] =
    block.value.map(_.merge)

  def condition[Res, A](condition: => Boolean, orElse: Res): EitherT[Try, Res, Unit] =
    if (condition)
      EitherT.rightT(())
    else
      EitherT.leftT(orElse)

  extension [A](ta: Try[A]) {
    // expose liftF on Try, so we don't need to write EitherT.liftF up front on every step
    def liftF[Res]: EitherT[Try, Res, A] =
      EitherT.liftF(ta)
  }

  extension [Res, A](op: EitherT[Try, Res, A]) {
    // this is not used, but needs to exist to allow for-comp steps that unpack tuples
    // (thing1, thing2) <- operationReturningTuple
    def withFilter(p: A => Boolean): EitherT[Try, Res, A] =
      op.subflatMap { a =>
        if p(a) then Right(a)
        else
          throw new RuntimeException(
            "Internal error: withFilter predicate did not match - impossible state"
          )
      }
  }
}
