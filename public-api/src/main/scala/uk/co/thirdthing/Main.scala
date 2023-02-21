package uk.co.thirdthing

import cats.effect.*

object Main extends IOApp:
  override def run(args: List[String]): IO[ExitCode] =
    ApplicationBuilder.build.use(_ => IO.never).as(ExitCode.Success)
