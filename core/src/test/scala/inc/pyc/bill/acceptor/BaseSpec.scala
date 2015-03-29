package inc.pyc.bill.acceptor

import akka.actor._
import akka.testkit._
import org.scalatest._
import MockConfig._
import com.typesafe.config._

abstract class BaseSpec(config: Config)
  extends TestKit(ActorSystem("BaseSpec", config))
  with ImplicitSender
  with WordSpecLike
  with ShouldMatchers
  with BeforeAndAfterAll {

  override def afterAll = TestKit.shutdownActorSystem(system)
}