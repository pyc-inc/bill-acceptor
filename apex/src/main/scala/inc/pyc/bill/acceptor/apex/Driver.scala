package inc.pyc.bill
package acceptor
package apex

import Events._
import Commands._
import States._
import driver._
import Implicits._
import ApexDecoder._
import akka.actor._
import com.github.jodersky.flow.Serial._
import akka.util.ByteString

/**
 * Pyramid Acceptor's Apex 7000 series bill acceptor:
 * http://www.pyramidacceptors.com/files/RS_232.pdf
 */
class ApexDriver extends FSM[State, DriverData]
  with LoggingFSM[State, DriverData]
  with Driver {

  when(Idle) {
    case Event(Received(data), _) =>
      buffer ++= data
      buffer = syncrhronize(buffer)
      if (bufferIsReady) parsePacket(buffer(1))
      stay

    case Event(UnInhibit, _) =>
      enabledBills = Bills.disabled
      stay

    case Event(Inhibit, _) =>
      enabledBills = Bills.enabled
      stay

    case Event(Stack, _) =>
      self ! Input(buildPacket(Cmd.Stack))
      stay

    case Event(Return, _) =>
      self ! Input(buildPacket(Cmd.Return))
      stay
  }

  def poll = Input(buildPacket())

  /** Data buffer received from operator */
  var buffer = ByteString()

  /** Enabled bills */
  var enabledBills = Bills.enabled

  /** Message start code */
  val STX = ByteString(0x02)

  /** Message end code */
  val ETX = ByteString(0x03)

  /** Msg type */
  def msgType(toSlave: Boolean = true): Byte = {
    val msgType = if (toSlave) 0x10 else 0x20
    msgType toByte
  }

  /**
   * Checks if buffer is ready to be parsed.
   * Byte after `STX` is length of packet.
   *
   * Requirements are:
   * 1. Buffer data must begin with the legitimate packet `STX`
   * 2. Entire packet must be available in the buffer, according to packet length.
   */
  def bufferIsReady =
    buffer.length > 1 &&
      buffer.length >= buffer(1) &&
      ByteString(buffer(0)) == STX

  /**
   * Tries to parse the next available packet in the buffer.
   * @param length the length of the packet
   */
  def parsePacket(length: Int): Unit = {
    val packet = buffer slice (0, length)
    buffer = buffer drop length
    parse(packet)
  }

  /**
   * Tries to parse the packet.
   *
   * TODO re-send messages not acknowledged
   */
  def parse(packet: ByteString): Unit = {
    if (validPacket(packet) && nonAck(packet) && fromSlave(packet) && hasETX(packet)) {

      val data = packet drop 3 dropRight 2
      val byte0 = data(0)
      val byte1 = data(1)
      val byte2 = data(2)

      // Order matters
      val response = (findEvents_2(byte2)) orElse
        (findEvents_1(byte1)) orElse
        (findStatus(byte0))

      response map (_ match {
        case Escrow =>
          acknowledge(packet)
          val billValue = (byte2 bits (3, 6)) >> 3
          val value = CURRENCY(currency)(billValue)
          val bill = currency(value)
          fsm ! Inserted(bill)

        case event =>
          acknowledge(packet)
          fsm ! event
      })
    }
  }

  /**
   * Validates the packet by computing the checksum.
   * The checksum is calculated on all bytes
   * (except: STX, ETX and the checksum byte itself)
   */
  def validPacket(packet: ByteString): Boolean = {
    val payload = packet drop 1 dropRight 2
    val sum = packet takeRight 1
    val realsum = ByteString(checksum(payload))
    sum == realsum
  }

  /**
   * One byte checksum. The checksum is calculated on all bytes
   * (except: STX, ETX and the checksum byte itself). This is done
   * by bitwise Exclusive OR-ing (XOR) the bytes.
   */
  def checksum(payload: ByteString): Byte =
    payload.foldLeft(0x00)((a, b) => a ^ b) toByte

  /**
   * Synchronizes the data in the buffer by finding
   * `STX` in data and returning all bytes after that.
   *
   * e.g.
   * jiberish + garbage + stx + data => stx + data
   */
  def syncrhronize(buffer: ByteString): ByteString = {
    val i = buffer indexOf STX(0)
    if (i < 0) buffer
    else buffer drop i
  }

  /**
   * Checks if packet is not an acknowledgment.
   */
  def nonAck(packet: ByteString): Boolean = {
    val byte2 = packet(2)
    val nibble = byte2 bits (0, 4)
    nibble == 0x00
  }

  /**
   * Checks if packet is from the "slave" or bill acceptor 
   * messages coming from Flow's Serial Operator actor.
   */
  def fromSlave(packet: ByteString): Boolean = {
    val byte2 = packet(2)
    val nibble = byte2 bits (4, 8)
    nibble == 0x20
  }

  /**
   * Checks if the second to last byte in the
   * packet is an end of message code.
   */
  def hasETX(packet: ByteString): Boolean = {
    ETX(0) == packet(packet.size - 2)
  }

  /**
   * Builds a packet.
   *
   * | STX | length | meta | data | ETX | checksum |
   *
   * @param cmd command given to the bill acceptor. Escrow MUST be enabled.
   */
  def buildPacket(cmd: ByteString = Cmd.Escrow): ByteString = {
    val length = 0x08
    val payload = ByteString(length, msgType(), enabledBills, cmd(0), 0x00)
    val sum = ByteString(checksum(payload))
    STX ++ payload ++ ETX ++ sum
  }

  /**
   * Echoes a packet with the acknowledgment bit
   * enabled to the operator.
   */
  def acknowledge(packet: ByteString): Unit = {
    val ack = convertToAck(packet)
    self ! Input(ack)
  }

  /**
   * Converts a packet into an echo by
   * setting the acknowledge bit on.
   */
  def convertToAck(packet: ByteString): ByteString = {
    val _length = ByteString(packet(1))
    val _meta = ByteString(msgType(toSlave = true) + 1) // +1 ack
    val _body = packet takeRight (packet.size - 3) dropRight 2
    val payload = _length ++ _meta ++ _body
    val sum = ByteString(checksum(payload))
    STX ++ payload ++ ETX ++ sum
  }
}

/**
 * Byte 0 of the data fields for messages to the operator.
 */
object Bills {
  private val `1` = 0x01
  private val `2` = 0x02
  private val `5` = 0x04
  private val `10` = 0x08
  private val `20` = 0x10
  private val `50` = 0x20
  private val `100` = 0x40

  val disabled: Byte = 0
  val enabled: Byte =
    (`1` + `2` + `5` + `10` + `20` + `50` + `100`) toByte
}

/**
 * Byte 1 of the data fields for messages to the operator.
 */
object Cmd {
  val _escrow = 0x10
  val _stack = 0x20
  val _return = 0x40

  val Escrow = ByteString(_escrow)
  val Stack = ByteString(_escrow + _stack)
  val Return = ByteString(_escrow + _return)
}