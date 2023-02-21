package uk.co.thirdthing

import cats.effect.{ExitCode, IO, IOApp}
import com.raquo.laminar.api.L.*
import org.scalajs.dom

object Main extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =

    val baseElement = dom.document.getElementById("appContainer")

    IO.delay(render(baseElement, UI.apply)) >>
      IO.pure(ExitCode.Success)
