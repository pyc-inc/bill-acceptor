package inc.pyc.bill
package acceptor

import inc.pyc.currency.Currency

/**
 * When something occurs and the host actor needs to be
 * notified with information.
 */
sealed trait Event

object Events {

  /**
   *  When the bill acceptor is ready for service.
   */
  case object Ready

  /**
   *  Command to notify that a bill was inserted
   *  and the value of the bill is `bill`
   */
  case class Inserted(bill: Currency#Value) extends Event

  /**
   *  Command to notify that a bill has been accepted
   *  and cannot be returned.
   */
  case class Confirmed(bill: Currency#Value) extends Event
}