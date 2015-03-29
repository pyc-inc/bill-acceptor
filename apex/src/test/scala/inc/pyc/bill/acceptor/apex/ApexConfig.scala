package inc.pyc.bill.acceptor.apex

import com.typesafe.config._

object ApexConfig {
  val config =
    ConfigFactory.load().getConfig("bill-acceptor")
}