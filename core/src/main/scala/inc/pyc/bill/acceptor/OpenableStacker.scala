package inc.pyc.bill
package acceptor

import Commands._
import States._
import akka.actor._
import concurrent.duration._

/**
 * When the bill acceptor can detect when the stacker
 * is open or not, use this trait.
 */
trait OpenableStacker extends StateFunctions {
  this: BillAcceptor with FSM[State, Data] =>

  when(Disabled)(stackerOpen)
  when(Idle)(stackerOpen)

  /**
   * Time it takes for `StackerOpen` state to timeout.
   */
  val stackerOpenTimeout: FiniteDuration = 5 minutes

  when(StackerOpen, stateTimeout = stackerOpenTimeout)(
    unlisten orElse {
      case Event(StackerOpen, _) =>
        stay

      case Event(s: acceptor.State, _) =>
        self ! UnListen
        log warning "Stacker is closed"
        stay

      case Event(StateTimeout, _) =>
        log error "Stacker is still open .... "
        stay
    })

  override def handleTransitions: TransitionHandler = {
    case _ -> StackerOpen =>
      log warning "Stacker is open"
  }

  /**
   * In the event when a state can
   * transition to `StackerOpen`.
   */
  def stackerOpen: StateFunction = {
    case Event(StackerOpen, _) =>
      goto(StackerOpen)
  }
}