package org.http4s
package parser

import org.parboiled2._
import java.nio.charset.Charset
import java.net.URLDecoder
import scalaz.NonEmptyList
import shapeless.HNil
import scalaz.syntax.std.option._

private[parser] trait Rfc3986Parser { this: Parser =>
  import CharPredicate.{Alpha, Digit, HexAlpha => Hexdig}

  def charset: Charset

  def Uri = rule { Scheme ~ ":" ~ HierPart ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) }

  def HierPart: Rule2[Option[org.http4s.Uri.Authority], org.http4s.Uri.Path] = rule {
    "//" ~ Authority ~ PathAbempty ~> {(auth: org.http4s.Uri.Authority, path: org.http4s.Uri.Path) => auth.some :: path :: HNil} |
      PathAbsolute ~> (None :: _ :: HNil) |
      PathRootless ~> (None :: _ :: HNil) |
      PathEmpty ~> {(e: org.http4s.Uri.Path) => None :: e :: HNil}
  }

  def UriReference = rule { Uri | RelativeRef }

  def AbsoluteUri = rule {
    Scheme ~ HierPart ~ optional("?" ~ Query) ~>
      ((scheme, auth, path, query) => org.http4s.Uri(scheme = Some(scheme), authority = auth, path = path, query = query))
  }

  def RelativeRef = rule { RelativePart ~ optional("?" ~ Query) ~ optional("#" ~ Fragment) }

  def RelativePart: Rule2[Option[org.http4s.Uri.Authority], org.http4s.Uri.Path] = rule {
    "//" ~ Authority ~ PathAbempty ~> {(auth: org.http4s.Uri.Authority, path: org.http4s.Uri.Path) => auth.some :: path :: HNil} |
      PathAbsolute ~> (None :: _ :: HNil) |
      PathNoscheme ~> (None :: _ :: HNil) |
      PathEmpty ~> {(e: org.http4s.Uri.Path) => None :: e :: HNil}
  }

  def Scheme = rule {
    capture(CharPredicate.Alpha | zeroOrMore(Alpha | Digit | "+" | "-" | ".")) ~> (_.ci)
  }

  def Authority = rule { optional(UserInfo ~ "@") ~ Host ~ optional(":" ~ Port) ~> (org.http4s.Uri.Authority.apply _) }

  def UserInfo = rule { capture(zeroOrMore(Unreserved | PctEncoded | SubDelims | ":")) ~> (decode _) }

  def Host = rule { capture(IpLiteral | IpV4Address | IpV6Address) ~> (s => decode(s).ci) }

  def Port = rule { capture(zeroOrMore(Digit)) ~> (_.toInt)}

  def IpLiteral = rule { "[" ~ (IpV6Address | IpVFuture) ~ "]" }

  def IpVFuture = rule { "v" ~ oneOrMore(Hexdig) ~ "." ~ oneOrMore(Unreserved | SubDelims | ":" ) }

  def IpV6Address = rule {
                                                       6.times(H16 ~ ":") ~ LS32 |
                                                "::" ~ 5.times(H16 ~ ":") ~ LS32 |
    optional(                            H16) ~ "::" ~ 4.times(H16 ~ ":") ~ LS32 |
    optional((0 to 1).times(H16 ~ ":") ~ H16) ~ "::" ~ 3.times(H16 ~ ":") ~ LS32 |
    optional((0 to 2).times(H16 ~ ":") ~ H16) ~ "::" ~ 2.times(H16 ~ ":") ~ LS32 |
    optional((0 to 3).times(H16 ~ ":") ~ H16) ~ "::" ~         H16 ~ ":"  ~ LS32 |
    optional((0 to 4).times(H16 ~ ":") ~ H16) ~ "::" ~                      LS32 |
    optional((0 to 5).times(H16 ~ ":") ~ H16) ~ "::" ~                      H16  |
    optional((0 to 6).times(H16 ~ ":") ~ H16) ~ "::"
  }

  def H16 = rule { (1 to 4).times(Hexdig) }

  def LS32 = rule { (H16 ~ ":" ~ H16) | IpV4Address }

  def IpV4Address = rule { 3.times(DecOctet ~ ".") ~ DecOctet }

  def DecOctet = rule {
    Digit |
      ("1" - "9") ~ Digit |
      "1" ~ 2.times(Digit) |
      "2" ~ ("0" - "4") ~ Digit |
      "25" ~ ("0" - "5")
  }

  def RegName = rule { zeroOrMore(Unreserved | PctEncoded | SubDelims) }

  def Path = rule { PathAbempty | PathAbsolute | PathNoscheme | PathRootless | PathEmpty }

  def PathAbempty: Rule1[org.http4s.Uri.Path] = rule { zeroOrMore("/" ~ Segment) ~> 
    {(t: Seq[String]) => t.foldLeft(org.http4s.Uri.Path.empty) { _ :+ "/" :+ _ }}
  }

  def PathAbsolute: Rule1[org.http4s.Uri.Path] = rule { "/" ~ SegmentNz ~ zeroOrMore("/" ~ Segment) ~> 
    {(h: String, t: Seq[String]) => t.foldLeft(org.http4s.Uri.Path("/", h)) { _ :+ "/" :+ _ }}
  }

  def PathNoscheme: Rule1[org.http4s.Uri.Path] = rule { SegmentNzNc ~ zeroOrMore("/" ~ Segment) ~> 
    {(h: String, t: Seq[String]) => t.foldLeft(org.http4s.Uri.Path(h)) { _ :+ "/" :+ _ }}
  }

  def PathRootless: Rule1[org.http4s.Uri.Path] = rule { SegmentNz ~ zeroOrMore("/" ~ Segment) ~> 
    {(h: String, t: Seq[String]) => t.foldLeft(org.http4s.Uri.Path(h)) { _ :+ "/" :+ _ }}
  } 

  def PathEmpty: Rule1[org.http4s.Uri.Path] = rule { push(org.http4s.Uri.Path.empty) }

  def Segment = rule { capture(zeroOrMore(Pchar)) ~> (decode _) }

  def SegmentNz = rule { capture(oneOrMore(Pchar)) ~> (decode _) }

  def SegmentNzNc = rule { capture(oneOrMore(Unreserved | PctEncoded | SubDelims | "@")) ~> (decode _) }

  def Pchar = rule { Unreserved | PctEncoded | SubDelims | ":" | "@" }

  def Query = rule { capture(zeroOrMore(Pchar | "/" | "?")) ~> (decode _) }

  def Fragment = rule { capture(zeroOrMore(Pchar | "/" | "?")) ~> (decode _) }

  def PctEncoded = rule { "%" ~ 2.times(Hexdig) }

  def Unreserved = rule { Alpha | Digit | "-" | "." | "_" | "~" }

  def Reserved = rule { GenDelims | SubDelims }

  def GenDelims = rule { ":" | "/" | "?" | "#" | "[" | "]" | "@" }

  def SubDelims = rule { "!" | "$" | "&" | "'" | "(" | ")" | "*" | "+" | "," | ";" | "=" }

  private[this] def decode(s: String) = URLDecoder.decode(s, charset.name)
}
