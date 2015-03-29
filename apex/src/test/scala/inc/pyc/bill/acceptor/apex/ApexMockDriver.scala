package inc.pyc.bill.acceptor
package apex

import akka.actor._

class MockApexDriver(testActor: ActorRef) extends ApexDriver {
  override val fsm: ActorRef = testActor
}