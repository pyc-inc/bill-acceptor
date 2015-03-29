package inc.pyc.bill
package acceptor
package driver

import Commands._
import States._
import inc.pyc.currency._
import akka.actor._
import akka.testkit._
import akka.util.ByteString
import concurrent.duration._
import com.github.jodersky.flow._
import Serial._

class DriverSpec extends BaseSpec(MockConfig.config)
  with SpecHelper
  with DriverTest {

  "A bill acceptor rs-232 driver" should {
    testDriver
  }
}

trait DriverTest {
  this: BaseSpec with SpecHelper =>

  def testDriver {

    var operator = TestProbe()
    val serial = TestProbe()
    var driver = TestFSMRef(new MockDriver(testActor, serial.ref))
    def boxed = Operator(operator.ref)

    def reset {
      operator = TestProbe()
      driver = TestFSMRef(new MockDriver(testActor, serial.ref))
    }

    def state(s: State, d: DriverData = NullData)(f: => Unit) {
      driver.setState(s, d); f
    }

    "be disconnected when it starts" in {
      driver.stateName should be(Disconnected)
      driver.stateData should be(NullData)
    }

    "load currency from configurations" in {
      driver.underlyingActor.currency should be(USD)
    }

    "warn about unknown message" in {

      def test = warn("unhandled") {
        driver ! "unknown"
      }

      driver.setState(Disconnected)
      test
      driver.setState(Idle)
      test
      driver.setState(Disconnecting)
      test
    }

    "be able to open the serial port" in
      state(Disconnected) {
        val du = driver.underlyingActor
        driver ! Listen
        serial.expectMsg(Open(du.port, du.settings, du.bufferSize))
      }

    "crash if opening the serial port fails" in
      state(Disconnected) {
        val du = driver.underlyingActor
        val cmd = Open(du.port, du.settings, du.bufferSize)

        intercept[CommandFailedException] {
          driver receive CommandFailed(cmd, new Throwable)
        }
      }

    "be idle when port is open" in
      state(Disconnected) {
        driver ! Opened("port")
        driver.stateName should be(Idle)
        driver.stateData should be(Operator(testActor))
      }

    "set timer to poll operator every 100 millis when idle" in
      state(Disconnected) {
        driver.isTimerActive("poll") should be(false)
        driver.setState(Idle, boxed)
        driver.isTimerActive("poll") should be(true)
        val time = 3
        val bs: ByteString = driver.underlyingActor.poll.input
        operator.receiveN((time - 1) * 10, time seconds)
        operator.awaitAssert(operator.expectMsg(Write(bs)), time seconds, 100 millis)
      }

    "stop the poll timer when disconnecting" in
      state(Disconnected) {
        driver.setState(Idle, boxed)
        driver.isTimerActive("poll") should be(true)
        driver.setState(Disconnecting, boxed)
        driver.isTimerActive("poll") should be(false)
      }

    "crash if operator fails while idle" in
      state(Idle, boxed) {
        EventFilter[OperatorCrashException](occurrences = 1) intercept {
          driver.watch(operator.ref)
          operator.ref ! Kill
        }
        reset
      }

    "send input data to operator when idle" in
      state(Idle, boxed) {
        val data = ByteString(0x01)

        driver ! Input(data)
        operator.expectMsg(Write(data))
        driver.stateName should be(Idle)
        driver.stateData should be(boxed)

        driver.setState(Disconnecting, boxed)
        driver ! Input(data)
        driver.setState(Disconnected, boxed)
        driver ! Input(data)
        operator.expectNoMsg
      }

    "be able to unlisten when idle" in
      state(Idle, boxed) {
        driver ! UnListen
        operator.expectMsg(Close)
        driver.stateName should be(Disconnecting)
        driver.stateData should be(boxed)
      }

    "notify when port is closed while disconnecting" in
      state(Disconnecting, boxed) {
        driver ! Closed
        expectMsg(Closed)
      }

    "disconnect when operator is terminated while disconnecting" in
      state(Disconnecting, boxed) {
        driver.watch(operator.ref)
        operator.ref ! PoisonPill
        awaitAssert(driver.stateName should be(Disconnected), 800 millis)
        awaitAssert(driver.stateData should be(NullData), 800 millis)
        reset
      }

    "terminate the operator if forced to shutdown when idle" in
      state(Idle, boxed) {
        terminates(operator.ref) {
          driver ! Shutdown
        }
        reset
      }

    "terminate if forced to shutdown when idle" in
      state(Idle, boxed) {
        terminates(driver) {
          driver ! Shutdown
        }
        reset
      }

    "report an error if forced to shutdown when idle" in
      state(Idle, boxed) {
        error("Terminated driver while idle") {
          driver ! Shutdown
        }
        reset
      }

    "set a state timer when disconnecting" in
      state(Disconnecting) {
        driver.isStateTimerActive should be(true)
      }

    "terminate if state timer goes off while disconnecting" in
      state(Disconnecting) {
        terminates(driver) {
          driver ! FSM.StateTimeout
        }
        reset
      }

    "warn if state timer goes off while disconnecting" in
      state(Disconnecting) {
        warn("Driver left hanging while disconnecting") {
          driver ! FSM.StateTimeout
        }
        reset
      }
  }
}