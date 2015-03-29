package inc.pyc.bill
package acceptor

import Commands._
import States._
import Data._
import Events._
import inc.pyc.currency._
import akka.actor._
import akka.actor.SupervisorStrategy._
import scala.concurrent._
import duration._
import com.github.jodersky.flow.Serial.Closed

object BillAcceptor {
  def props(system: ActorSystem) = Props(Class forName Settings(system).driver)
}

/**
 * The interface to communicate with the Bill Acceptor.
 */
trait BillAcceptor extends StateFunctions {
  this: FSM[State, Data] with LoggingFSM[State, Data] =>

  override val supervisorStrategy =
    OneForOneStrategy() {
      case ex =>
        log error ("Driver crashed while {}: {}", stateName, ex getMessage)
        self ! Closed
        Stop
    }

  /**
   * Creates a driver.
   */
  def createDriver: ActorRef

  /**
   * The actor that receives events.
   * This is the actor that initialized this actor.
   */
  val host: ActorRef = context parent

  /**
   * Handles events that are sent to this FSM.
   */
  def handleEvents: StateFunction = FSM NullFunction

  /**
   * Handles state transitions.
   */
  def handleTransitions: TransitionHandler = FSM NullFunction

  /**
   * Time it takes for `Connecting` state to timeout.
   */
  val connectingTimeout: FiniteDuration = 3 seconds

  /**
   * Time it takes for `PowerUp` state to timeout.
   */
  val powerupTimeout: FiniteDuration = 3 seconds

  /**
   * Time it takes for `Disconnecting` state to timeout.
   */
  val disconnectingTimeout: FiniteDuration = 3 seconds

  /**
   * Time it takes for `Escrow` state to timeout.
   */
  val escrowTimeout: FiniteDuration = 4 seconds

  /**
   * Function to execute after `Escrow` state times out.
   */
  val escrowTimeoutHandler: () => State = () => {
    log warning "Escrow timed out"
    self ! Return
    stay
  }

  startWith(Disconnected, NullData)

  when(Disconnected) {
    case Event(Listen, _) =>
      val driver = createDriver
      context watch driver
      driver ! Listen
      goto(Connecting) using Driver(driver)
  }

  when(Connecting, stateTimeout = connectingTimeout)(
    shutdownOnTimeout orElse {
      case Event(PowerUp, _) =>
        goto(PowerUp)

      case Event(Idle, _) =>
        host ! Ready
        goto(Idle)
    })

  when(Disconnecting, stateTimeout = disconnectingTimeout)(
    shutdownOnTimeout orElse {
      case Event(Closed, Driver(ref)) =>
        context stop ref
        goto(Disconnected) using NullData
    })

  when(PowerUp, stateTimeout = powerupTimeout)(
    shutdownOnTimeout orElse {
      case Event(Idle, _) =>
        host ! Ready
        goto(Idle)
    })

  when(Disabled)(
    unlisten orElse {
      case Event(s: State, _) =>
        stay
    })

  when(Idle)(
    unlisten orElse {
      case Event(Accepting, _) =>
        goto(Accepting)
    })

  when(Accepting) {
    case Event(ins: Inserted, Driver(ref)) =>
      if (invalid(ins.bill)) self ! Return
      else host ! ins
      goto(Escrow) using Bill(Driver(ref), ins)
  }

  when(Escrow, stateTimeout = escrowTimeout) {
    case Event(Stack, Bill(Driver(ref), _)) =>
      ref ! Stack
      goto(Stacking)

    case Event(Return, Bill(Driver(ref), _)) =>
      ref ! Return
      goto(Returning)

    case Event(StateTimeout, _) =>
      escrowTimeoutHandler()
  }

  when(Stacking) {
    case Event(Stacked, _) =>
      goto(Stacked)
  }

  when(Returning) {
    case Event(Returned, _) =>
      goto(Returned)
  }

  when(Returned) {
    case Event(Idle, Bill(driver, _)) =>
      goto(Idle) using driver
  }

  when(Stacked) {
    case Event(Idle, Bill(driver, _)) =>
      goto(Idle) using driver
  }

  onTransition(
    handleTransitions orElse {
      case _ -> Disconnected =>
        host ! Disconnected

      case _ -> Cheated =>
        log warning "Someone was caught cheating"
    })

  whenUnhandled(
    handleEvents orElse {
      case Event(CommunicationError, _) =>
        log warning ("Communication error in {}/{}", stateName, stateData)
        stay

      case Event(Closed, _) =>
        log error ("Driver unexpectedly closed port while {}", stateName)
        goto(Disconnected) using NullData

      case Event(Terminated(driver), Driver(ref)) =>
        if (driver equals ref) {
          self ! Closed
          stay
        } else stay

      case e =>
        log warning ("unhandled request {} in state {}", e, stateName)
        stay
    })

  override def logDepth = 12

  onTermination {
    case StopEvent(FSM.Failure(_), state, data) =>
      val lastEvents = getLog.mkString("\n\t")
      log.warning("Failure in state {} with data {}\n" +
        "Events leading up to this point:\n\t{}",
        state, data, lastEvents)
  }

  // These states will be initialized by the bill acceptor model.
  when(Rejecting)(FSM.NullFunction)
  when(StackerFull)(FSM.NullFunction)
  when(JamInAcceptor)(FSM.NullFunction)
  when(Cheated)(FSM.NullFunction)
  when(Failure)(FSM.NullFunction)
  when(CommunicationError)(FSM.NullFunction)
  when(StackerOpen)(FSM.NullFunction)

  initialize()
}