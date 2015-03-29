package inc.pyc.bill
package acceptor

import Commands._
import States._
import akka.actor._

/**
 * Handles errors in the acceptor
 * while and after the bill is moving.
 */
trait BillAcceptorErrors {
  this: BillAcceptor with FSM[State, Data] =>

  // Bill may be in transit 
  // during any of these states

  when(PowerUp)(handleError)
  when(Accepting)(handleError)
  when(Stacking)(handleError)
  when(Returning)(handleError)
  when(Rejecting)(handleError)
  when(Cheated)(handleError)

  /**
   * Handles the possible errors for the
   * failure of the bill acceptor.
   *
   * Include this when powering up or
   * when bill is moving in the acceptor.
   */
  def handleError: StateFunction = {
    case Event(JamInAcceptor, _) =>
      log error ("Jammed in {}/{}", stateName, stateData)
      goto(JamInAcceptor)

    case Event(StackerFull, _) =>
      log error ("Stacker Full in {}/{}", stateName, stateData)
      goto(StackerFull)

    case Event(Failure, _) =>
      log error ("Failure in {}/{}", stateName, stateData)
      goto(Failure)
  }

  when(JamInAcceptor)(unlisten orElse {
    case Event(JamInAcceptor, _)     => stay
    case Event(s: acceptor.State, _) => unlistenIfOtherState
  })

  when(StackerFull)(unlisten orElse {
    case Event(StackerFull, _)       => stay
    case Event(s: acceptor.State, _) => unlistenIfOtherState
  })

  when(Failure)(unlisten orElse {
    case Event(Failure, _)           => stay
    case Event(s: acceptor.State, _) => unlistenIfOtherState
  })

  /**
   * Stop listening if any state is sent.
   */
  def unlistenIfOtherState: State = {
    log info ("Operating again after state {}", stateName)
    self ! UnListen
    stay
  }
}