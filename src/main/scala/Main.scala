import scala.concurrent.Await
import scala.concurrent.duration._

import monix.eval.Task
import monix.eval.TaskLocal
import monix.execution.Scheduler
import monix.execution.atomic.AtomicInt

object Main {
  implicit val ec: Scheduler = monix.execution.Scheduler.Implicits.global
  implicit val opts = Task.defaultOptions.enableLocalContextPropagation

  def main(args: Array[String]): Unit = {
    val memoizedTask = Task.delay {
      5
    }.memoize

    val i = AtomicInt(0)

    val t = for {
      local <- TaskLocal(0)
      ii <- Task.shift.flatMap(_ => Task.delay {
        i.incrementAndGet()
      })
      _ <- local.write(ii)
      result <- Task.parZip2(
        memoizedTask.flatMap { _ =>
          Task.sleep(100.millis).flatMap { _ =>
            local.read
          }
        },
        memoizedTask.flatMap { _ =>
          Task.sleep(100.millis).flatMap { _ =>
            local.read
          }
        },
      )
    } yield result

    val f = scala.concurrent.Future.sequence(List(
      t.runToFutureOpt,
      t.runToFutureOpt,
      t.runToFutureOpt
    ).map(f => f : scala.concurrent.Future[(Int, Int)])).map(println)
    Await.result(f, 10.seconds)
  }
}
