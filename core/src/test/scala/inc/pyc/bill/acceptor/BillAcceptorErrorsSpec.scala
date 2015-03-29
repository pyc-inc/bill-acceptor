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

class BillAcceptorErrorsSpec extends BaseSpec(MockConfig.config)
  with SpecHelper
  with ErrorsTest {

  "A bill acceptor with error handling support" should {
    testBillAcceptorErrors
  }
}

trait ErrorsTest {
  this: BaseSpec with SpecHelper =>

  def testBillAcceptorErrors {
    val driver = TestProbe()
    val fsm = TestFSMRef(new MockErrorBillAcceptor(testActor, driver.ref))
    def driverData = Driver(driver.ref)

    def state(s: State, d: Data = NullData)(f: => Unit) {
      fsm.setState(s, d); f
    }

    def expectErrorHandling(s: State) {
      state(s) {
        error("Jammed") {
          fsm ! JamInAcceptor
          fsm.stateName should be(JamInAcceptor)
        }
      }

      state(s) {
        error("Stacker Full") {
          fsm ! StackerFull
          fsm.stateName should be(StackerFull)
        }
      }

      state(s) {
        error("Failure") {
          fsm ! Failure
          fsm.stateName should be(Failure)
        }
      }
    }

    def expectRecoveryHandling(s: State) {
      state(s, driverData) {
        within(1000 millis) {
          fsm ! s
          fsm.stateName should be(s)
          fsm ! Idle
          fsm.stateName should be(Disconnecting)
        }
      }
    }

    "handle errors when powering up" in
      expectErrorHandling(PowerUp)

    "handle errors when accepting" in
      expectErrorHandling(Accepting)

    "handle errors when stacking" in
      expectErrorHandling(Stacking)

    "handle errors when returning" in
      expectErrorHandling(Returning)

    "handle errors when rejecting" in
      expectErrorHandling(Rejecting)

    "handle errors when cheated" in
      expectErrorHandling(Cheated)

    "be able to operate again and unlisten when jammed" in
      state(JamInAcceptor, driverData) {
        within(2 second) {
          fsm ! JamInAcceptor
          fsm.stateName should be(JamInAcceptor)
          fsm ! Idle
          fsm.stateName should be(Disconnecting)
        }
      }

    "be able to operate again and unlisten when stacker is full" in
      expectRecoveryHandling(StackerFull)

    "be able to operate again and unlisten after failure" in
      expectRecoveryHandling(Failure)
  }
}