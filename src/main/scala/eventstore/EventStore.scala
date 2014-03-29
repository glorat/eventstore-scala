package eventstore

import CQRS.DomainEvent
import com.novus.salat.annotations.raw.Salat

case class EventMessage(headers: Map[String, Object] = Map(), body: DomainEvent)

trait IEventStream {
  /** Gets the value which uniquely identifies the stream to which the stream belongs. */
  def streamId: Guid
  /** Gets the value which indiciates the most recent committed revision of event stream. */
  def streamRevision: Int
  /** Gets the value which indicates the most recent committed sequence identifier of the event stream. */
  def commitSequence: Int

  /** Gets the collection of events which have been successfully persisted to durable storage. */
  def committedEvents: Iterable[EventMessage]

  /** Gets the collection of committed headers associated with the stream. */
  def committedHeaders: Map[String, Object]

  /** Gets the collection of yet-to-be-committed events that have not yet been persisted to durable storage. */
  def uncommittedEvents: Iterable[EventMessage]

  /** Gets the collection of yet-to-be-committed headers associated with the uncommitted events. */
  def uncommittedHeaders: Map[String, Object]

  /** Adds the event messages provided to the session to be tracked. */
  def add(uncommittedEvent: EventMessage): Unit

  /** Commits the changes to durable storage */
  def commitChanges(commitId: Guid): Unit

  /** Clears the uncommitted changes. */
  def clearChanges(): Unit
}

trait IStoreEvents {
  def createStream(streamId: Guid): IEventStream
  def openStream(streamId: Guid, minRevision: Int, maxRevision: Int): IEventStream
  def openStream(snapshot: Snapshot, maxRevision: Int): IEventStream
  def advanced: IPersistStreams

  def saveEvents(streamId: Guid, events: Iterable[DomainEvent], expectedVersion: Int) = {
    // store.saveEvents(aggregate.id, aggregate.getUncommitedChanges, expectedVersion)
    val stream = openStream(streamId, expectedVersion, expectedVersion)

    events.foreach(ev => {
      stream.add(eventstore.EventMessage(body = ev))
    })
    stream.commitChanges(java.util.UUID.randomUUID)
  }

}

trait IAccessSnapshots {
  def getSnapshot(streamId: Guid, maxRevision: Int): Option[Snapshot]
  def addSnapshot(snapshot: Snapshot): Boolean
  def getStreamsToSnapshot(maxThreshold: Int): Iterable[StreamHead]
}

trait ICommitEvents {
  def getFrom(streamId: Guid, minRevision: Int, maxRevision: Int): Seq[Commit]
  def commit(attempt: Commit): Unit
}

case class Snapshot(streamId: Guid, streamRevision: Int, payload: Object)

case class Commit(streamId: Guid, streamRevision: Int, commitId: Guid, commitSequence: Int, commitStamp: EventDateTime, headers: Map[String, Object], events: List[EventMessage]) {
  // TODO: hashcode/equals issue? StreamId+CommitId only
  def isValid(): Boolean = {
    if (streamId == null || commitId == null)
      throw new IllegalArgumentException("Resources.CommitsMustBeUniquelyIdentified");

    if (commitSequence <= 0)
      throw new IllegalArgumentException("Resources.NonPositiveSequenceNumber");

    if (streamRevision <= 0)
      throw new IllegalArgumentException("Resources.NonPositiveRevisionNumber");

    if (streamRevision < commitSequence)
      throw new IllegalArgumentException("Resources.RevisionTooSmall");

    true
  }
}

case class StreamHead(streamId: Guid, headRevision: Int, snapshotRevision: Int)

trait IDocumentSerializer {
  def serialize[T](graph: T): Object
  def deserialize[T](document: Object): T
}

trait IPersistStreams extends ICommitEvents with IAccessSnapshots {
  def initialize: Unit
  def getFrom(start: EventDateTime): Seq[Commit]
  def getFromTo(start: EventDateTime, end: EventDateTime): Seq[Commit]
  def getUndispatchedCommits(): Seq[Commit]
  def markCommitAsDispatched(commit: Commit): Unit
  def purge: Unit
}

trait IPersistenceFactory {
  def build: IPersistStreams
}

class StorageException(val message: String) extends Exception(message)
class StorageUnavailableException(message: String) extends StorageException(message)
class ConcurrencyException(message: String) extends Exception(message)
class DuplicateCommitException(val message: String = toString) extends Exception(message)

object EventDateTime {
  /// <summary>
  /// The callback to be used to resolve the current moment in time.
  /// </summary>
  var resolver: () => EventDateTime = () => org.joda.time.Instant.now().getMillis()

  /// <summary>
  /// Gets the current moment in time.
  /// </summary>
  def now(): EventDateTime = {
    resolver()
  }

}

trait Logging {
  val loggerName = this.getClass.getName
  // lazy val log = org.apache.logging.log4j.LogManager.getLogger(loggerName)
  lazy val log = org.slf4j.LoggerFactory.getLogger(this.getClass)
}
