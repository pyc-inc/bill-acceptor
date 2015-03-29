package inc.pyc.bill.acceptor.apex

object Implicits {

  implicit class ByteImplicit(byte: Byte) {

    /**
     * Checks if bit in byte is set.
     */
    def bit(i: Int): Boolean = ((byte >> i) & 1) == 1

    /**
     * Gets the value of a number by exclusively clearing all bits
     * less than the lower bit mark and greater than the higher bit mark.
     */
    def bits(lower: Int, higher: Int): Byte = {

      // clear bits i to most significant bit
      val i_MSB_Mask = ((1 << higher) - 1)

      // clear bits i to least significant bit
      val i_LSB_Mask = ~((1 << lower) - 1)

      (byte & i_MSB_Mask & i_LSB_Mask) toByte
    }
  }
}