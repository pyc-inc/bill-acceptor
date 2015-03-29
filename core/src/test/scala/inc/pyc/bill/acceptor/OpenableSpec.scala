package inc.pyc.bill.acceptor

import Commands._
import States._
import Data._
import Events._
import driver.Shutdown
import inc.pyc.currency._
import akka.actor._
import akka.testkit._
import akka.actor.SupervisorStrategy._
import com.github.jodersky.flow.Serial.Closed
import concurrent.duration._
import FSM.StateTimeout

class OpenableSpec extends BaseSpec(MockConfig.config)
  with SpecHelper
  with OpenableTest {

  "A bill acceptor with openable stacker support" should {
    testOpenable
  }
}

trait OpenableTest {
  this: BaseSpec with SpecHelper =>

  def testOpenable {
    val driver = TestProbe()
    val fsm = TestFSMRef(new MockOpenableBillAcceptor(testActor, driver.ref))
    def driverData = Driver(driver.ref)

    def state(s: State, d: Data = NullData)(f: => Unit) {
      fsm.setState(s, d); f
    }

    "warn when stacker opens" in state(Idle) {
      warn("Stacker is open") {
        fsm.setState(StackerOpen)
      }
    }

    "warn when stacker closes" in state(StackerOpen) {
      warn("Stacker is closed") {
        fsm ! Idle
      }
    }

    "be disconnecting when stacker closes" in
      state(StackerOpen, driverData) {
        fsm ! Idle
        fsm.stateName should be(Disconnecting)
      }

    "report an error if stacker never closes" in
      state(StackerOpen) {
        error("Stacker is still open") {
          fsm ! StateTimeout
        }
      }

    "go to stacker open state only when idle and disabled" in {
      state(Idle) {
        fsm ! StackerOpen
        fsm.stateName should be(StackerOpen)
      }

      state(Disabled) {
        fsm ! StackerOpen
        fsm.stateName should be(StackerOpen)
      }

      state(Escrow) {
        fsm ! StackerOpen
        fsm.stateName should be(Escrow)
      }

      state(Stacking) {
        fsm ! StackerOpen
        fsm.stateName should be(Stacking)
      }
    }
  }
}