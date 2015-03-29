package inc.pyc.bill
package acceptor
package apex

import Commands._
import States._
import Data._
import Events._
import akka.actor._
import concurrent.duration._

/**
 * Pyramid Acceptor's Apex Bill Acceptor.
 */
class Apex extends FSM[State, Data]
  with LoggingFSM[State, Data]
  with BillAcceptor
  with BillAcceptorErrors
  with OpenableStacker
  with Inhibitable {

  def createDriver: ActorRef =
    context.actorOf(Props[ApexDriver], "ApexDriver")

  when(Accepting) {
    case Event(Rejecting, _) =>
      goto(Rejecting)

    case Event(Cheated, _) =>
      goto(Cheated)
  }

  // BUG in Apex: Returning never goes to Returned
  // I may have an old firmware
  // Use timeouts to continue transitions
  when(Returning, stateTimeout = 500 milli) {
    case Event(StateTimeout, _) =>
      goto(Returned)
  }

  when(Returned, stateTimeout = 200 millis) {
    case Event(StateTimeout, Bill(driver, _)) =>
      goto(Idle) using driver
  }

  when(Rejecting)(gotoIdle)
  when(Cheated)(gotoIdle)

  override def handleTransitions: TransitionHandler = {
    case Stacking -> Stacked =>
      // Give Credit when Stacked
      nextStateData match {
        case Bill(_, Inserted(bill)) =>
          host ! Confirmed(bill)

        case _ =>
      }
  }

  /**
   * State function whose only option is
   * to go to Idle state.
   */
  def gotoIdle: StateFunction = {
    case Event(Idle, _) =>
      goto(Idle)
  }
}