package inc.pyc.bill
package acceptor

import States._
import akka.actor._

class MockBillAcceptor(testActor: ActorRef, driver: ActorRef)
  extends FSM[State, Data]
  with LoggingFSM[State, Data]
  with BillAcceptor {

  def createDriver: ActorRef = driver
  override val host: ActorRef = testActor

  override def handleEvents: StateFunction = {
    case Event("event-test", _) =>
      testActor ! "event-test"
      stay
  }

  override def handleTransitions: TransitionHandler = {
    case Disconnecting -> Cheated =>
      testActor ! "transition-test"
  }
}

class MockOpenableBillAcceptor(testActor: ActorRef, driver: ActorRef)
  extends FSM[State, Data]
  with LoggingFSM[State, Data]
  with BillAcceptor
  with OpenableStacker {

  def createDriver: ActorRef = driver
  override val host: ActorRef = testActor
}

class MockErrorBillAcceptor(testActor: ActorRef, driver: ActorRef)
  extends FSM[State, Data]
  with LoggingFSM[State, Data]
  with BillAcceptor
  with BillAcceptorErrors {

  def createDriver: ActorRef = driver
  override val host: ActorRef = testActor
}

class MockInhibitBillAcceptor(testActor: ActorRef, driver: ActorRef)
  extends FSM[State, Data]
  with LoggingFSM[State, Data]
  with BillAcceptor
  with Inhibitable {

  def createDriver: ActorRef = driver
  override val host: ActorRef = testActor
}