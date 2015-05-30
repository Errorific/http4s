package org.http4s
package headers

import org.http4s.Header.Raw

import scalaz.NonEmptyList

object `Accept-Language` extends HeaderKey.Internal[`Accept-Language`] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[`Accept-Language`.HeaderT] =
    parser.AcceptLanguageHeader.ACCEPT_LANGUAGE(raw.value).toOption
}

final case class `Accept-Language`(values: NonEmptyList[LanguageTag]) extends Header.RecurringRenderable {
  def key = `Accept-Language`
  type Value = LanguageTag
  def preferred: LanguageTag = values.tail.fold(values.head)((a, b) => if (a.q >= b.q) a else b)
  def satisfiedBy(languageTag: LanguageTag) = values.list.exists(_.satisfiedBy(languageTag))
}
