package inc.pyc.bill
package acceptor
package driver

import akka.actor._
import akka.util.ByteString
import com.github.jodersky.flow._
import com.github.jodersky.flow.Serial._
import inc.pyc.bill.acceptor.Data
import inc.pyc.bill.acceptor.State

class MockDriver(testActor: ActorRef, serialr: ActorRef)
  extends FSM[State, DriverData]
  with LoggingFSM[State, DriverData]
  with Driver {

  def poll = Input(ByteString(0x05))

  override val fsm = testActor

  override def serial = serialr

}