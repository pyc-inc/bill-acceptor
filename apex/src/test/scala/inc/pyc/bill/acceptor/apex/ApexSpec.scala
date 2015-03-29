package inc.pyc.bill
package acceptor
package apex

import inc.pyc.currency._
import Events._
import States._
import Data._
import akka.actor._
import com.typesafe.config._
import akka.testkit._
import concurrent.duration._
import akka.actor.FSM.StateTimeout

class ApexSpec extends BaseSpec(ApexConfig.config)
  with SpecHelper
  with ApexTest {

  "An Apex" should {
    testApex
  }
}

trait ApexTest {
  this: BaseSpec with SpecHelper =>

  def testApex {

    val driver = TestProbe()
    val fsm = TestFSMRef(new MockApex(testActor, driver.ref))

    def aDriver = Driver(driver.ref)
    def aBill = Bill(Driver(driver.ref), Inserted(USD(5)))

    def state(s: State, d: Data = NullData)(func: => Unit) {
      fsm.setState(s, d); func
    }

    type Event = (State, Data)

    val allEvents: List[Event] = {
      val allStates: List[State] = List(
        Disconnected,
        Disconnecting,
        Connecting,
        PowerUp,
        Disabled,
        Idle,
        Accepting,
        Escrow,
        Stacking,
        Stacked,
        Rejecting,
        Returning,
        Returned,
        StackerFull,
        StackerOpen,
        JamInAcceptor,
        Cheated,
        Failure,
        CommunicationError)

      val allData: List[Data] = List(
        NullData,
        aDriver,
        aBill)

      for {
        state <- allStates
        data <- allData
      } yield (state, data)
    }

    def expectTransitions(from: Event)(to: Event*) {

      def test(should: Boolean, e: Event) {
        fsm.setState(from._1, from._2)
        fsm ! e._1
        if (should) {
          fsm.stateName should be(e._1)
          fsm.stateData should be(e._2)
        } else {
          fsm.stateName should be(from._1)
          fsm.stateData should be(from._2)
        }
      }

      allEvents foreach { event =>
        if (to.exists(_ == event)) {
          test(should = true, event)
        } else if (event != from) {
          test(should = false, event)
        }
      }
    }

    /*
    "only go to rejecting and cheated states when Accepting" in 
    	expectTransitions ((Accepting, aDriver)) (
    	    (Rejecting, aDriver), 
    	    (Cheated, aDriver))
    	    */

    "be able to reject when accepting" in
      state(Accepting, aDriver) {
        fsm ! Rejecting
        fsm.stateName should be(Rejecting)
        fsm.stateData should be(aDriver)
      }

    "be able to report a cheater when accepting" in
      state(Accepting, aDriver) {
        fsm ! Cheated
        fsm.stateName should be(Cheated)
        fsm.stateData should be(aDriver)
      }

    "set state timer when returning" in
      state(Returning) {
        fsm.isStateTimerActive should be(true)
      }

    "go to returned if returning timer expires" in
      state(Returning, aBill) {
        fsm ! StateTimeout
        fsm.stateName should be(Returned)
        fsm.stateData should be(aBill)
      }

    "set state timer when returned" in
      state(Returned) {
        fsm.isStateTimerActive should be(true)
      }

    "go to idle if returned timer expires" in
      state(Returned, aBill) {
        fsm ! StateTimeout
        fsm.stateName should be(Idle)
        fsm.stateData should be(aBill.driver)
      }

    "notify confirmed bill when stacked" in
      state(Stacking, aBill) {
        fsm ! Stacked
        val confirmation = Confirmed(aBill.inserted.bill)
        expectMsg(confirmation)
      }
  }
}