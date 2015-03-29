package inc.pyc.bill
package acceptor
package apex

import States._
import Implicits._
import inc.pyc.currency._
import scala.collection.immutable.SortedMap

private[apex] object ApexDecoder {

  // order of the mapping matters

  private val statusBits = SortedMap(
    6 -> Returned,
    5 -> Returning,
    4 -> Stacked,
    3 -> Stacking,
    2 -> Escrow,
    1 -> Accepting,
    0 -> Idle)(implicitly[Ordering[Int]].reverse)

  private val events1Bits = SortedMap(
    0 -> Cheated,
    1 -> Rejecting,
    2 -> JamInAcceptor,
    3 -> StackerFull)(implicitly[Ordering[Int]].reverse)

  private val events2Bits = SortedMap(
    2 -> Failure,
    1 -> CommunicationError,
    0 -> PowerUp)(implicitly[Ordering[Int]].reverse)

  /**
   *  Status codes
   *
   *  Scan byte 0 of the data fields received by operator.
   */
  def findStatus(byte: Byte) =
    statusBits find (byte bit _._1) map (_._2)

  /**
   *  Event codes
   *
   *  Although not a status by APEX definition,
   *  FSM will translate it as a status.
   *
   *  Scan byte 1 of the data fields received by operator.
   */
  def findEvents_1(byte: Byte) = {
    // bit 4 should eq 1 if bill cassette is present
    if (byte bit 4) {
      events1Bits find (byte bit _._1) map (_._2)
    } else {
      Some(StackerOpen)
    }
  }

  /**
   *  Event codes
   *
   *  Although not a status by APEX definition,
   *  FSM will translate it as a status.
   *
   *  Scan byte 2 of the data fields received by operator.
   */
  def findEvents_2(byte: Byte) =
    events2Bits find (byte bit _._1) map (_._2)

  /**
   * Country specific data codes.
   * The values of the array is the value of the bill.
   * If value is zero, bit is reserved or unknown.
   */
  val CURRENCY: Map[Currency, Array[Int]] = Map(
    USD -> Array(0, 1, 2, 5, 10, 20, 50, 100))

}