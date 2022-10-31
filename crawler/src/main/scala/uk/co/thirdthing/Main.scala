package uk.co.thirdthing

import cats.effect.{ExitCode, IO, IOApp}

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    IO(println("hello world")).as(ExitCode.Success)
}
