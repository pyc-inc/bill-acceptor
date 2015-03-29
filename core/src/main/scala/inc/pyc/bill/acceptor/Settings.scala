package inc.pyc.bill
package acceptor

import akka.actor._
import com.typesafe.config._

class SettingsImpl(config: Config) extends Extension {
  val currency = config getString "bill-acceptor.currency"
  val driver = config getString "bill-acceptor.driver"
  val port = config getString "bill-acceptor.port"
  val bufferSize = config getInt "bill-acceptor.buffer-size"
  val baud = config getInt "bill-acceptor.baud"
  val characterSize = config getInt "bill-acceptor.char-size"
  val twoStopBits = config getBoolean "bill-acceptor.two-stop-bits"
  val parity = config getInt "bill-acceptor.parity"
}

object Settings extends ExtensionId[SettingsImpl]
  with ExtensionIdProvider {

  override def lookup = Settings

  override def createExtension(system: ExtendedActorSystem) =
    new SettingsImpl(system.settings.config)

  override def get(system: ActorSystem): SettingsImpl = super.get(system)
}

