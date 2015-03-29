package inc.pyc.bill
package acceptor
package driver

import akka.actor.ActorRef

/**
 * Data used by driver
 */
sealed trait DriverData

/**
 * Null
 */
case object NullData extends DriverData

/**
 * Container to hold the serial operator actor.
 */
case class Operator(ref: ActorRef) extends DriverData