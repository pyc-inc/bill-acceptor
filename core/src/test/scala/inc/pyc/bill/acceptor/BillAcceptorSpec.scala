package inc.pyc.bill
package acceptor

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

class BillAcceptorSpec extends BaseSpec(MockConfig.config)
  with SpecHelper
  with BillAcceptorTest {

  "A bill acceptor" should {
    testBillAcceptor
  }
}

trait BillAcceptorTest {
  this: BaseSpec with SpecHelper =>

  def testBillAcceptor {
    var driver = TestProbe()
    var fsm = TestFSMRef(new MockBillAcceptor(testActor, driver.ref))
    def driverData = Driver(driver.ref)
    def billData(inserted: Inserted) = Bill(driverData, inserted)

    def reset {
      driver = TestProbe()
      fsm = TestFSMRef(new MockBillAcceptor(testActor, driver.ref))
    }

    def state(s: State, d: Data = NullData)(f: => Unit) {
      fsm.setState(s, d); f
    }

    "be disconnected when it starts" in {
      fsm.stateName should be(Disconnected)
      fsm.stateData should be(NullData)
    }

    "disconnect when driver disconnects unexpectedly" in
      state(Idle, driverData) {
        within(500 millis) {
          fsm ! Closed
          expectMsg(Disconnected)
          fsm.stateName should be(Disconnected)
          fsm.stateData should be(NullData)
        }
      }

    "report an error when driver disconnects unexpectedly" in
      state(Idle) {
        error("Driver unexpectedly closed port") {
          fsm ! Closed
          expectMsg(Disconnected)
        }
      }

    "disconnect when driver is terminated unexpectedly" in
      state(Idle, driverData) {
        within(500 millis) {
          fsm.watch(driver.ref)
          driver.ref ! PoisonPill
          expectMsg(Disconnected)
          fsm.stateName should be(Disconnected)
          fsm.stateData should be(NullData)
        }
        reset
      }

    "warn about unknown message" in {
      def test = warn("unhandled") {
        fsm ! "unknown"
      }

      test
      fsm.setState(Escrow)
      test
      fsm.setState(Connecting)
      test
    }

    "warn when someone cheats" in {
      warn("Someone was caught cheating") {
        fsm.setState(Cheated)
      }
    }

    "warn when there is an error communicating" in {
      warn("Communication error") {
        fsm ! CommunicationError
      }
    }

    "be connecting after listen command is sent" in
      state(Disconnected) {
        expectMsg(Disconnected)
        fsm ! Listen
        driver.expectMsg(Listen)
        fsm.stateName should be(Connecting)
        fsm.stateData should be(driverData)
      }

    "be able to go to idle state after connecting" in
      state(Connecting, driverData) {
        fsm ! Idle
        fsm.stateName should be(Idle)
        fsm.stateData should be(driverData)
      }

    "notify when it is idle after connecting" in {
      expectMsg(Ready)
    }

    "be able to go to power up state after connecting" in
      state(Connecting, driverData) {
        fsm ! PowerUp
        fsm.stateName should be(PowerUp)
        fsm.stateData should be(driverData)
      }

    "be able to go to idle state after powering up" in
      state(PowerUp, driverData) {
        fsm ! Idle
        fsm.stateName should be(Idle)
        fsm.stateData should be(driverData)
      }

    "notify when it is idle after powering up" in {
      expectMsg(Ready)
    }

    "be able to start accepting bills in idle state" in
      state(Idle, driverData) {
        fsm ! Accepting
        fsm.stateName should be(Accepting)
        fsm.stateData should be(driverData)
      }

    "be able to go to escrow state with inserted bill while accepting" in
      state(Accepting, driverData) {
        val bill = Inserted(USD(5))
        fsm ! bill
        fsm.stateName should be(Escrow)
        fsm.stateData should be(billData(bill))
      }

    "notify when there is an inserted bill" in {
      expectMsg(Inserted(USD(5)))
    }

    "return bill if it is invalid currency value" in
      state(Accepting, driverData) {
        fsm ! Inserted(USD(0))
        driver.expectMsg(Return)
      }

    "set a state timer in escrow" in
      state(Escrow) {
        fsm.isStateTimerActive should be(true)
      }

    "return bill if escrow timer goes off" in
      state(Escrow, billData(Inserted(USD(100)))) {
        fsm ! StateTimeout
        driver.expectMsg(Return)
      }

    "warn if escrow timer goes off" in
      state(Escrow) {
        warn("Escrow timed out") {
          fsm ! StateTimeout
        }
      }

    "stack when stacking" in {
      val data = billData(Inserted(USD(50)))
      state(Stacking, data) {
        fsm ! Stacked
        fsm.stateName should be(Stacked)
        fsm.stateData should be(data)
      }
    }

    "return when returning" in {
      val data = billData(Inserted(USD(50)))
      state(Returning, data) {
        fsm ! Returned
        fsm.stateName should be(Returned)
        fsm.stateData should be(data)
      }
    }

    "be able to idle after bill has been stacked" in {
      val data = billData(Inserted(USD(50)))
      state(Stacked, data) {
        fsm ! Idle
        fsm.stateName should be(Idle)
        fsm.stateData should be(data.driver)
      }
    }

    "be able to idle after bill has been returned" in {
      val data = billData(Inserted(USD(50)))
      state(Returned, data) {
        fsm ! Idle
        fsm.stateName should be(Idle)
        fsm.stateData should be(data.driver)
      }
    }

    "be able to unlisten only when idle or disabled" in {
      def test(should: Boolean, s: State) = state(s, driverData) {
        fsm ! UnListen

        if (should) {
          driver.expectMsg(UnListen)
          fsm.stateName should be(Disconnecting)
        } else {
          driver.expectNoMsg
          fsm.stateName should be(s)
        }

      }

      test(should = true, Idle)
      test(should = true, Disabled)
      test(should = false, Escrow)
      test(should = false, Stacking)
      test(should = false, PowerUp)
    }

    "disconnect when connection closes" in
      state(Disconnecting) {
        fsm ! Closed
        fsm.stateName should be(Disconnected)
        fsm.stateData should be(NullData)
      }

    "notify when bill acceptor disconnects" in {
      expectMsg(Disconnected)
    }

    "set a state timer when connecting" in
      state(Connecting) {
        fsm.isStateTimerActive should be(true)
      }

    "set a state timer when disconnecting" in
      state(Disconnecting) {
        fsm.isStateTimerActive should be(true)
      }

    "set a state timer when powering up" in
      state(PowerUp) {
        fsm.isStateTimerActive should be(true)
      }

    "stay by default when there is a communication error" in {
      def test(s: State) = state(s, driverData) {
        fsm ! CommunicationError
        fsm.stateName should be(s)
        fsm.stateData should be(driverData)
      }

      test(Idle)
      test(Escrow)
      test(Accepting)
      test(Returning)
    }

    def timeoutTest(s: State): Unit = state(s, driverData) {
      fsm ! StateTimeout
      driver.expectMsg(Shutdown)
      fsm.stateName should be(s)
    }

    "shutdown the driver when connecting times out" in
      timeoutTest(Connecting)

    "shutdown the driver when disconnecting times out" in
      timeoutTest(Disconnecting)

    "shutdown the driver when powering up times out" in
      timeoutTest(PowerUp)

    "call handleEvents function" in {
      fsm ! "event-test"
      expectMsg("event-test")
    }

    "call handleTransitions function" in {
      fsm.setState(Disconnecting)
      fsm.setState(Cheated)
      expectMsg("transition-test")
    }
  }
}