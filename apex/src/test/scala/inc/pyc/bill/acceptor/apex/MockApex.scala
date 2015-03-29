package inc.pyc.bill.acceptor
package apex

import akka.actor._

class MockApex(testActor: ActorRef, driverRef: ActorRef)
  extends Apex {

  override val host: ActorRef = testActor
  override def createDriver: ActorRef = driverRef
}