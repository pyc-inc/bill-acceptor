package inc.pyc.bill
package acceptor
package driver

import akka.AkkaException
import scala.util.control.NoStackTrace
import akka.util.ByteString
import com.github.jodersky.flow.Serial.Event

/**
 * Shutdown driver
 */
case object Shutdown

/**
 *  Command to send data to serial.
 */
case class Input(input: ByteString)

/**
 * Command when writing to the serial actor.
 */
private[acceptor] case class Wrote(data: ByteString) extends Event

// exceptions
private[acceptor] class CommandFailedException(reason: Throwable)
  extends AkkaException("Connection failed, stopping driver. Reason: " + reason, reason)
  with NoStackTrace

private[acceptor] class OperatorCrashException
  extends AkkaException("Operator crashed, exiting driver", new Throwable)
  with NoStackTrace
