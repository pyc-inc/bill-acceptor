package inc.pyc.bill
package acceptor

import Commands._
import States._
import Data._
import akka.actor._

/**
 * Bill acceptor that can inhibit
 * and uninhibit bills.
 */
trait Inhibitable {
  this: BillAcceptor with FSM[State, Data] =>

  when(Disabled) {
    case Event(Inhibit, Driver(ref)) =>
      ref ! Inhibit
      goto(Idle)
  }

  when(Idle) {
    case Event(UnInhibit, Driver(ref)) =>
      ref ! UnInhibit
      goto(Disabled)
  }
}