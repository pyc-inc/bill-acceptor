package inc.pyc.bill
package acceptor

/**
 * State of the bill acceptor
 */
sealed trait State

object States {
  /**
   * When bill acceptor is not connected or
   * listening to a serial port.
   */
  case object Disconnected extends State

  /**
   * The driver is closing the serial port.
   */
  case object Disconnecting extends State

  /**
   * The driver is opening serial port.
   */
  case object Connecting extends State

  /**
   * Ready to accept currency.
   */
  case object Idle extends State

  /**
   * The Acceptor is drawing the bill in and
   * examining it with validation sensors
   */
  case object Accepting extends State

  /**
   * Bill validation is complete.
   * One byte of data is sent with this status
   * to indicate the inserted denomination.
   */
  case object Escrow extends State

  /**
   * A bill is being transported to the stacker
   */
  case object Stacking extends State

  /**
   * Status is reported after cash has been stacked.
   */
  case object Stacked extends State

  /**
   * The Acceptor judged a bill invalid or the Host
   * disabled acceptance of a specific denomination.
   */
  case object Rejecting extends State

  /**
   * During Escrow status, the acceptor received a Return
   * command from the Host; the bill is being returned.
   */
  case object Returning extends State

  /**
   * Status is reported after cash has been returned.
   */
  case object Returned extends State

  /**
   * The Acceptor will not draw any notes into
   * the path during this status.
   */
  case object Disabled extends State

  /**
   * The stacker is either removed or not completely installed.
   */
  case object StackerOpen extends State

  /**
   * Standard response when the acceptor receives power.
   */
  case object PowerUp extends State

  /**
   * The stacker is full.
   */
  case object StackerFull extends State

  /**
   * A bill is jammed inside the Acceptor head
   */
  case object JamInAcceptor extends State

  /**
   * The Acceptor has detected an action thought to be mischievous
   */
  case object Cheated extends State

  /**
   * Normal Acceptor operation cannot continue because of a
   * failure, abnormal condition or incorrect setting
   */
  case object Failure extends State

  /**
   * An error has developed in the communication data
   */
  case object CommunicationError extends State
}