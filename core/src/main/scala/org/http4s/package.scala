package org

import scalaz.{~>, Id, EitherT, \/}

import scalaz.concurrent.Task
import scalaz.stream.Process
import org.http4s.util.CaseInsensitiveString
import scodec.bits.ByteVector

package object http4s {

  type AuthScheme = CaseInsensitiveString

  type EntityBody = Process[Task, ByteVector]

  def EmptyBody = Process.halt

  type DecodeResult[T] = EitherT[Task, ParseFailure, T]

  val ApiVersion: Http4sVersion = Http4sVersion(BuildInfo.apiVersion._1, BuildInfo.apiVersion._2)

  type ParseResult[+A] = ParseFailure \/ A

  // Questionable taste
  implicit protected[http4s] val idToTask: Id.Id ~> Task = new (Id.Id ~> Task) {
    override def apply[A](fa: A): Task[A] = Task.now(fa)
  }
}
