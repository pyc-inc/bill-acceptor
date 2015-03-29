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

class InhibitableSpec extends BaseSpec(MockConfig.config)
  with SpecHelper
  with InhibitableTest {

  "An inhibitable bill acceptor" should {
    testInhibitable
  }
}

trait InhibitableTest {
  this: BaseSpec with SpecHelper =>

  def testInhibitable {
    val driver = TestProbe()
    val fsm = TestFSMRef(new MockInhibitBillAcceptor(testActor, driver.ref))
    def driverData = Driver(driver.ref)

    def state(s: State, d: Data = NullData)(f: => Unit) {
      fsm.setState(s, d); f
    }

    "only be able to inhibit when disabled" in {
      state(Disabled, driverData) {
        fsm ! Inhibit
        fsm.stateName should be(Idle)
        fsm.stateData should be(driverData)
      }

      state(Idle, driverData) {
        fsm ! Inhibit
        fsm.stateName should be(Idle)
        fsm.stateData should be(driverData)
      }

      state(Escrow) {
        fsm ! Inhibit
        fsm.stateName should be(Escrow)
      }
    }

    "only be able to uninhibit when idle" in {
      state(Idle, driverData) {
        fsm ! UnInhibit
        fsm.stateName should be(Disabled)
        fsm.stateData should be(driverData)
      }

      state(Disabled, driverData) {
        fsm ! UnInhibit
        fsm.stateName should be(Disabled)
        fsm.stateData should be(driverData)
      }

      state(Escrow) {
        fsm ! UnInhibit
        fsm.stateName should be(Escrow)
      }
    }
  }
}