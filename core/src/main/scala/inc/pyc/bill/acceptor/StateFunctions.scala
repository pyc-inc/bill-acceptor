package inc.pyc.bill
package acceptor

import Data._
import Commands._
import States._
import akka.actor._

/**
 * Helper functions that return `StateFunction`
 */
trait StateFunctions {
  this: BillAcceptor with FSM[State, Data] =>

  /**
   * Sends the shutdown signal to the driver when it times out.
   */
  def shutdownOnTimeout: StateFunction = {
    case Event(StateTimeout, Driver(ref)) =>
      ref ! driver.Shutdown // left hanging, kill it
      stay
  }

  /**
   * Can stop listening and disconnect the driver.
   */
  def unlisten: StateFunction = {
    case Event(UnListen, Driver(ref)) =>
      ref ! UnListen
      goto(Disconnecting)
  }
}