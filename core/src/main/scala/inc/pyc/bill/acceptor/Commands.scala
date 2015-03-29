package inc.pyc.bill
package acceptor


/**
 * Command from the app to the bill acceptor.
 */
sealed trait Command


object Commands {
  /**
   *  Command to turn on the driver and
   *  listen for inserted bills.
   */
  case object Listen extends Command

  /**
   *  Command to turn off the driver and
   *  stop listening for inserted bills.
   */
  case object UnListen extends Command

  /**
   *  Accept a bill in bill acceptor (escrow mode).
   */
  case object Stack extends Command

  /**
   *  Reject a bill in bill acceptor (escrow mode).
   */
  case object Return extends Command

  /**
   * Turn on acceptance of all currency.
   */
  case object Inhibit extends Command

  /**
   * Turn off acceptance of all currency.
   */
  case object UnInhibit extends Command
}