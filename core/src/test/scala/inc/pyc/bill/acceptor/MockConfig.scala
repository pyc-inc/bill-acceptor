package inc.pyc.bill.acceptor

import com.typesafe.config.ConfigFactory

object MockConfig {
  val config = ConfigFactory.parseString("""
akka {
  loglevel = "WARNING"
  loggers = [akka.testkit.TestEventListener]
  stdout-loglevel = "OFF"
}
bill-acceptor {
  currency = "USD"
  driver = "inc.pyc.bill.acceptor.MockBillAcceptor"
  port = "/dev/null"
  buffer-size = 0
  baud = 0
  char-size = 0
  two-stop-bits = off
  parity = 0
}""")
}