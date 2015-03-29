package inc.pyc.bill
package acceptor
package apex

import driver._
import States._
import Commands._
import Events._
import inc.pyc.currency._
import Implicits._
import driver.Input
import akka.actor._
import akka.testkit._
import akka.util.ByteString
import concurrent.duration._
import com.github.jodersky.flow._
import Serial._

class ApexDriverSpec extends BaseSpec(ApexConfig.config)
  with SpecHelper
  with ApexDriverTest {

  "The Apex Driver" should {
    testApexDriver

    "Implicit byte manipulations" should {
      "be able to extract bits from a byte" in {
        val byte = 0xAF toByte //            -> 1 0 1 0 1 1 1 1  OR 175/-81
        val nib1 = 0x0F toByte // 1st nibble -> - - - - 1 1 1 1  OR 15
        val nib2 = 0xA0 toByte // 2nd nibble -> 1 0 1 0 - - - -  OR 160/-96
        val bit1 = 0x28 toByte // bits       -> - 0 1 0 1 - - -  OR 40

        byte bits (0, 4) should be(nib1)
        byte bits (4, 8) should be(nib2)
        byte bits (3, 7) should be(bit1)
        byte bits (0, 8) should be(byte)
      }
    }
  }

}

trait ApexDriverTest {
  this: BaseSpec with SpecHelper =>

  def testApexDriver {
    val operator = TestProbe()
    val ref = TestFSMRef(new MockApexDriver(testActor))
    val driver = ref.underlyingActor

    def b(bytes: Int*) = ByteString(bytes: _*)

    def expectAllBillsEnabled(byte: Byte) {
      byte should be(0x7F)
    }

    def expectAllBillsDisabled(byte: Byte) {
      byte should be(0x00)
    }

    def idle(func: => Unit) {
      ref.setState(Idle, Operator(operator.ref))
      ref.cancelTimer("poll")
      func
    }

    // if message is processed, it should send ack to operator
    def expectMsgParsed(data: ByteString, should: Boolean = true) {
      idle {
        ref ! Received(data)
        val ack = driver.convertToAck(data)
        if (should) operator.expectMsg(Write(ack))
        else operator.expectNoMsg
      }
    }

    def expectNoMsgParsed(data: ByteString) {
      expectMsgParsed(data, should = false)
    }

    // builds a packet that operator can send to driver
    // (function too bloated)
    def packet(
      data0: Int,
      data1: Int = 0,
      data2: Int = 0,
      len: Option[Byte] = None,
      meta: Byte = driver.msgType(toSlave = false),
      sum: Option[Byte] = None,
      etx: Boolean = true): ByteString = {

      val data = Seq(data0, data1, data2)
      val _data = b(meta) ++ b(data: _*)
      val dataWithETX = if (etx) _data ++ driver.ETX else _data
      val altlen: Byte = (dataWithETX.size + 3) toByte
      val length: Byte = len.getOrElse(altlen)
      val payload = b(length) ++ _data
      val sumval: Byte = sum.getOrElse(driver.checksum(payload))
      driver.STX ++ b(length) ++ dataWithETX ++ b(sumval)
    }

    // byte 0 of data field (Operator -> Driver) 
    val idling = 0x01
    val accepting = 0x02
    val escrowed = 0x04
    val stacking = 0x08
    val stacked = 0x10
    val returning = 0x20
    val returned = 0x40

    // byte 1 of data field (Operator -> Driver) 
    val cheated = 0x01
    val rejected = 0x02
    val jammed = 0x04
    val stackerFull = 0x08
    val stackerClosed = 0x10

    // byte 2 of data field (Operator -> Driver) 
    val powerup = 0x01
    val invalid = 0x02
    val failure = 0x04
    val dollar1 = 0x08
    val dollar2 = 0x10
    val dollar5 = 0x18
    val dollar10 = 0x20
    val dollar20 = 0x28
    val dollar50 = 0x30
    val dollar100 = 0x38

    "define the message start code" in {
      driver.STX should be(ByteString(0x02))
    }

    "define the message end code" in {
      driver.ETX should be(ByteString(0x03))
    }

    "set baud rate to 9600" in {
      driver.settings.baud should be(9600)
    }

    "set even parity bit" in {
      driver.settings.parity should be(Parity.Even)
    }

    "set buffer size to 10 bits" in {
      driver.bufferSize should be(10)
    }

    "set one stop bit" in {
      driver.settings.twoStopBits should be(false)
    }

    "set character size to 7 bits" in {
      driver.settings.characterSize should be(7)
    }

    "enable all the bills by default" in {
      driver.enabledBills should be(Bills.enabled)
    }

    "fill the buffer when data is received from the operator" in
      idle {
        ref ! Received(b(0x34, 0x21, 0xD6))
        List(0x88, 0xA2).map(byte => ref ! Received(b(byte)))
        driver.buffer should be(b(0x34, 0x21, 0xD6, 0x88, 0xA2))
      }

    "synchronize the buffer" in idle {
      ref ! Received(b(0x11, 0x06, driver.STX(0), 0x07, 0x7F))
      driver.buffer should be(driver.STX ++ b(0x07, 0x7F))
    }

    "parse the packet when it is ready in buffer" in idle {
      driver.bufferIsReady should be(false)
      ref ! Received(b(0x86, 0xF4, 0x87))
      driver.bufferIsReady should be(false)
      driver.buffer ++= b(0x23, 0xB2, 0xB4)
      driver.bufferIsReady should be(true)
      ref ! Received(b(0x1A))
      driver.buffer should be(b(0xB2, 0xB4, 0x1A))
    }

    def make(meta: ByteString = b(0x00), data: ByteString = b(0x00)) = {
      val payload = b(0x07) ++ meta ++ data ++ driver.ETX
      val sum = b(driver.checksum(payload))
      driver.STX ++ payload ++ sum
    }

    "be able to check if packet is from bill acceptor or slave" in {
      val fromAcceptor = b(0x1A) // bit 4 is set
      val fromSlave = b(0x2A)    // bit 5 is set
      driver.fromSlave(make(fromSlave)) should be(true)
      driver.fromSlave(make(fromAcceptor)) should be(false)
    }

    "be able to check if packet is an ackowledgment" in {
      val ack = b(0x01)
      val nonack = b(0x00)
      driver.nonAck(make(ack)) should be(false)
      driver.nonAck(make(nonack)) should be(true)
    }

    "be able to send acknowledgments to the operator" in idle {
      val packet = make(data = b(0x40))
      driver.acknowledge(packet)
      operator.expectMsgPF(200 millis) {
        case Write(data, _) =>
          data(3) should be(0x40)
          driver.nonAck(data) should be(false)
      }
    }

    "be able to validate a packet" in {
      val retTest = b(2, 8, 16, 125, 80, 0, 3, 53)
      val stackTest = b(2, 8, 16, 125, 16, 0, 3, 117)
      val error = b(2, 8, 16, 125, 16, 0, 3, 50)

      driver.validPacket(retTest) should be(true)
      driver.validPacket(stackTest) should be(true)
      driver.validPacket(error) should be(false)
    }

    "uninhibit bills" in {
      ref ! UnInhibit
      expectAllBillsDisabled(driver.enabledBills)
    }

    "inhibit bills" in {
      ref ! Inhibit
      expectAllBillsEnabled(driver.enabledBills)
    }

    "stack bill" in {
      ref ! Stack
      operator.expectMsgPF(500 millis) {
        case Write(data, _) =>
          val byte = data(4)
          byte bit (5) should be(true)
          byte bit (6) should be(false)
      }
    }

    "return bill" in {
      ref ! Return
      operator.expectMsgPF(500 millis) {
        case Write(data, _) =>
          val byte = data(4)
          byte bit (5) should be(false)
          byte bit (6) should be(true)
      }
    }

    // IMPORTANT TESTS! 
    // The tests below check if malformed packets 
    // are ignored and if packets are parsed correctly. 
    // This reduces the risk of 
    // malicious incoming data from EMPs.

    "receive idling packet from operator" in {
      expectMsgParsed(packet(idling, stackerClosed))
      expectMsg(Idle)
    }

    "receive accepting packet from operator" in {
      expectMsgParsed(packet(accepting, stackerClosed))
      expectMsg(Accepting)
    }

    "prioritize accepting bit over idling bit " in {
      expectMsgParsed(packet(idling + accepting, stackerClosed))
      expectMsg(Accepting)
    }

    "receive escrow packet for unkown amount" in {
      expectMsgParsed(packet(escrowed, stackerClosed, 0x00))
      expectMsg(Inserted(USD.invalid))
    }

    "receive escrow packet for $1" in {
      expectMsgParsed(packet(escrowed, stackerClosed, dollar1))
      expectMsg(Inserted(USD(1)))
    }

    "receive escrow packet for $2" in {
      expectMsgParsed(packet(escrowed, stackerClosed, dollar2))
      expectMsg(Inserted(USD(2)))
    }

    "receive escrow packet for $5" in {
      expectMsgParsed(packet(escrowed, stackerClosed, dollar5))
      expectMsg(Inserted(USD(5)))
    }

    "receive escrow packet for $10" in {
      expectMsgParsed(packet(escrowed, stackerClosed, dollar10))
      expectMsg(Inserted(USD(10)))
    }

    "receive escrow packet for $20" in {
      expectMsgParsed(packet(escrowed, stackerClosed, dollar20))
      expectMsg(Inserted(USD(20)))
    }

    "receive escrow packet for $50" in {
      expectMsgParsed(packet(escrowed, stackerClosed, dollar50))
      expectMsg(Inserted(USD(50)))
    }

    "receive escrow packet for $100" in {
      expectMsgParsed(packet(escrowed, stackerClosed, dollar100))
      expectMsg(Inserted(USD(100)))
    }

    "receive stacking packet from operator" in {
      expectMsgParsed(packet(stacking, stackerClosed))
      expectMsg(Stacking)
    }

    "prioritize stacking bit over escrow bit " in {
      expectMsgParsed(packet(escrowed + stacking, stackerClosed))
      expectMsg(Stacking)
    }

    "receive stacked packet from operator" in {
      expectMsgParsed(packet(stacked, stackerClosed))
      expectMsg(Stacked)
    }

    "prioritize stacked bit over escrow bit " in {
      expectMsgParsed(packet(escrowed + stacked, stackerClosed))
      expectMsg(Stacked)
    }

    "prioritize stacked bit over stacking bit " in {
      expectMsgParsed(packet(stacked + stacking, stackerClosed))
      expectMsg(Stacked)
    }

    "receive returning packet from operator" in {
      expectMsgParsed(packet(returning, stackerClosed))
      expectMsg(Returning)
    }

    "prioritize returning bit over escrow bit " in {
      expectMsgParsed(packet(escrowed + returning, stackerClosed))
      expectMsg(Returning)
    }

    "receive returned packet from operator" in {
      expectMsgParsed(packet(returned, stackerClosed))
      expectMsg(Returned)
    }

    "prioritize returned bit over escrow bit " in {
      expectMsgParsed(packet(escrowed + returned, stackerClosed))
      expectMsg(Returned)
    }

    "prioritize returned bit over returning bit " in {
      expectMsgParsed(packet(returned + returning, stackerClosed))
      expectMsg(Returned)
    }

    "receive cheated packet from operator" in {
      expectMsgParsed(packet(0x00, cheated + stackerClosed))
      expectMsg(Cheated)
    }

    "receive bill rejected packet from operator" in {
      expectMsgParsed(packet(0x00, rejected + stackerClosed))
      expectMsg(Rejecting)
    }

    "prioritize rejected bit over cheated bit " in {
      expectMsgParsed(packet(0x00, cheated + rejected + stackerClosed))
      expectMsg(Rejecting)
    }

    "receive bill jammed packet from operator" in {
      expectMsgParsed(packet(0x00, jammed + stackerClosed))
      expectMsg(JamInAcceptor)
    }

    "prioritize jammed bit over rejected and cheated bit" in {
      expectMsgParsed(packet(0x00, cheated + rejected + jammed + stackerClosed))
      expectMsg(JamInAcceptor)
    }

    "receive stacker full packet from operator" in {
      expectMsgParsed(packet(0x00, stackerFull + stackerClosed))
      expectMsg(StackerFull)
    }

    "prioritize stacker full bit over jammed bit" in {
      expectMsgParsed(packet(0x00, stackerFull + jammed + stackerClosed))
      expectMsg(StackerFull)
    }

    "receive power up packet from operator" in {
      expectMsgParsed(packet(0x00, stackerClosed, powerup))
      expectMsg(PowerUp)
    }

    "receive invalid command packet from operator" in {
      expectMsgParsed(packet(0x00, stackerClosed, invalid))
      expectMsg(CommunicationError)
    }

    "prioritize invalid command bit over power up bit" in {
      expectMsgParsed(packet(0x00, stackerClosed, powerup + invalid))
      expectMsg(CommunicationError)
    }

    "receive failure packet from operator" in {
      expectMsgParsed(packet(0x00, stackerClosed, failure))
      expectMsg(Failure)
    }

    "prioritize failure bit over power up and invalid command bit" in {
      expectMsgParsed(packet(0x00, stackerClosed, powerup + invalid + failure))
      expectMsg(Failure)
    }

    "ignore all statuses and events, except for power up and failures, when stacker is open" in {
      expectMsgParsed(packet(idling))
      expectMsg(StackerOpen)

      expectMsgParsed(packet(idling, stackerFull))
      expectMsg(StackerOpen)

      expectMsgParsed(packet(escrowed, stackerFull + cheated, dollar100))
      expectMsg(StackerOpen)

      expectMsgParsed(packet(idling, 0x00, powerup))
      expectMsg(PowerUp)

      expectMsgParsed(packet(0x00, 0x00, failure))
      expectMsg(Failure)

      expectMsgParsed(packet(idling, 0x00, invalid))
      expectMsg(CommunicationError)
    }

    "prioritize Apex event over Apex status as the official FSM status" in {
      expectMsgParsed(packet(returned + returning, cheated + stackerClosed))
      expectMsg(Cheated)

      expectMsgParsed(packet(escrowed, cheated + stackerClosed, dollar10 + powerup))
      expectMsg(PowerUp)

      expectMsgParsed(packet(returned, stackerClosed, failure))
      expectMsg(Failure)
    }

    "not parse packets with invalid checksum" in {
      expectNoMsgParsed(packet(idling, sum = Some(0x01)))
      expectNoMsg

      expectNoMsgParsed(packet(0x00, 0x00, failure, sum = Some(0x01)))
      expectNoMsg
    }

    "parse packets with only the slave to master bit turned on in the meta byte" in {
      expectMsgParsed(packet(idling, stackerClosed, meta = driver.msgType(toSlave = false)))
      expectMsg(Idle)

      expectNoMsgParsed(packet(idling, meta = driver.msgType(toSlave = true)))
      expectNoMsg

      expectNoMsgParsed(packet(idling, meta =
        (driver.msgType(toSlave = false) + 1 toByte)))
      expectNoMsg

      expectNoMsgParsed(packet(idling, meta =
        (driver.msgType(toSlave = false) + 2 toByte)))
      expectNoMsg

      expectNoMsgParsed(packet(idling, meta =
        (driver.msgType(toSlave = false) + 83 toByte)))
      expectNoMsg
    }

    "not parse packets with acknowledgment bit on" in {
      expectNoMsgParsed(packet(idling, meta =
        (driver.msgType(toSlave = false) + 1) toByte))
      expectNoMsg
    }

    "not parse packets with missing end of message code" in {
      expectNoMsgParsed(packet(idling, etx = false))
      expectNoMsg
    }
  }
}