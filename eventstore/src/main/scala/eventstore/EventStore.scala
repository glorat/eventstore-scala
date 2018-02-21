package eventstore

import CQRS.DomainEvent
import com.novus.salat.annotations.raw.Salat

import scala.concurrent.Future

case class CommitedEvent(event:DomainEvent, streamId: Guid, streamRevision:Int)

class StorageException(val message: String) extends Exception(message)
class StorageUnavailableException(message: String) extends StorageException(message)
class ConcurrencyException(message: String) extends Exception(message)
class DuplicateCommitException(val message: String = toString) extends Exception(message)

trait Logging {
  val loggerName = this.getClass.getName
  // lazy val log = org.apache.logging.log4j.LogManager.getLogger(loggerName)
  lazy val log = org.slf4j.LoggerFactory.getLogger(this.getClass)
}
