package inc.pyc.bill
package acceptor

import Events._
import akka.actor.ActorRef

/**
 *  Payload data sent by the bill acceptor.
 */
sealed trait Data

object Data {
  /**
   * The driver sent a message with no data.
   */
  case object NullData extends Data

  /**
   * The bill acceptor driver actor.
   */
  case class Driver(ref: ActorRef) extends Data

  /**
   * Container to hold the driver actor and inserted bill.
   */
  case class Bill(driver: Driver, inserted: Inserted) extends Data
}