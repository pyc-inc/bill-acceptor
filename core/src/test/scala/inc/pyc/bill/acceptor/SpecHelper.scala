package inc.pyc.bill.acceptor

import akka.actor._
import akka.testkit._

trait SpecHelper {
  this: BaseSpec =>

  def terminates(ref: ActorRef)(func: => Unit) {
    val test = TestProbe()
    test.watch(ref)
    func
    test.expectTerminated(ref)
  }

  def warn(start: String)(func: => Unit) {
    EventFilter.warning(occurrences = 1, start = start) intercept {
      func
    }
  }

  def error(start: String)(func: => Unit) {
    EventFilter.error(occurrences = 1, start = start) intercept {
      func
    }
  }

  def info(start: String)(func: => Unit) {
    EventFilter.info(occurrences = 1, start = start) intercept {
      func
    }
  }
}