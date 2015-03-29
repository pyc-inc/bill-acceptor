package inc.pyc.bill
package acceptor
package driver

import States._
import Commands._
import inc.pyc.currency._
import akka.actor._
import akka.io.IO
import akka.util.ByteString
import com.github.jodersky.flow._
import com.github.jodersky.flow.Serial._
import scala.concurrent.duration._

/**
 * Main Bill Acceptor Driver trait.
 */
private[acceptor] trait Driver {
  this: FSM[State, DriverData] with LoggingFSM[State, DriverData] =>

  import context.{ dispatcher, system }

  /** The Bill Acceptor FSM is the the parent actor */
  val fsm = context parent

  /**
   * Serial Connection Actor
   */
  def serial: ActorRef = IO(Serial)

  /** Currency that is currently being accepted. */
  val currency = findCurrency(Settings(system).currency)

  /** Command to poll bill acceptor's status. */
  def poll: Input

  /** Serial port (/dev/tty*) */
  val port: String = Settings(system).port

  /** Size of buffer */
  val bufferSize: Int = Settings(system).bufferSize

  /** Settings for serial */
  val settings = SerialSettings(
    baud = Settings(system).baud,
    characterSize = Settings(system).characterSize,
    twoStopBits = Settings(system).twoStopBits,
    parity = Parity(Settings(system).parity))

  /**
   * Timer for disconnecting state
   */
  val disconnectingTimeout = 2 seconds

  startWith(Disconnected, NullData)

  when(Disconnected) {
    case Event(Listen, _) =>
      log debug "Requesting operator to open port"
      serial ! Open(port, settings, bufferSize)
      stay

    case Event(CommandFailed(cmd, reason), _) =>
      throw new CommandFailedException(reason)

    case Event(Opened(port), _) =>
      log debug ("Port {} is now open", port)
      val operator = sender
      context watch operator
      goto(Idle) using (Operator(operator))
  }

  when(Idle) {
    case Event(Terminated(ref), Operator(op)) =>
      if (op equals ref) throw new OperatorCrashException
      else stay

    case Event(Shutdown, Operator(op)) =>
      op ! Kill
      stop(FSM.Failure("Terminated driver while idle"))

    case Event(UnListen, Operator(op)) =>
      log debug "Driver's serial connection is closing"
      op ! Close
      goto(Disconnecting)

    case Event(Input(data), Operator(op)) =>
      op ! Write(data)
      stay
  }

  when(Disconnecting, stateTimeout = disconnectingTimeout) {
    case Event(Closed, _) =>
      log debug "Operator closed port normally"
      fsm ! Closed
      stay

    case Event(Terminated(ref), Operator(op)) =>
      if (ref equals op) {
        log debug "Operator terminated normally, exiting driver"
        context unwatch op
        goto(Disconnected) using NullData
      } else stay

    case Event(StateTimeout, _) =>
      log warning "Driver left hanging while disconnecting"
      stop()
  }

  onTransition {
    case Disconnected -> Idle =>
      setTimer("poll", poll, 100 milliseconds, true)

    case Idle -> _ =>
      cancelTimer("poll")
  }

  whenUnhandled {
    case Event(Input(_), _) =>
      stay // ignore

    case e =>
      log warning ("unhandled request {} in state {}", e, stateName)
      stay
  }
  /*
  override def logDepth = 12

  onTermination {
    case StopEvent(FSM.Failure(_), state, data) =>
      val lastEvents = getLog.mkString("\n\t")
      log.warning("Failure in state {} with data {}\n" +
        "Events leading up to this point:\n\t{}",
        state, data, lastEvents)
  }
  */
  protected def formatData(data: ByteString) =
    data.map("0x" + Integer.toHexString(_)) mkString ("[", ",", "]")

}