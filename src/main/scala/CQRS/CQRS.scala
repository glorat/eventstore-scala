package CQRS

import scala.reflect.runtime.universe._
import eventstore.Logging
import com.novus.salat.annotations.raw.Salat
import scala.reflect._

trait Message
@Salat
trait DomainEvent extends Message
trait Command extends Message

trait IEventStore {
  def saveEvents(aggregateId: GUID, events: Traversable[DomainEvent], expectedVersion: Int)
  def getEventsForAggregate(aggregateId: GUID): List[DomainEvent]
}
trait IEventPublisher

abstract class EventStore extends IEventStore {
  private val current = Map[GUID, List[Object]]()

}

trait IMemento extends Product
//  val store: IStoreEvents = new OptimisticEventStore(new InMemoryPersistenceEngine, Seq())
class EventStoreRepository(val store: eventstore.IStoreEvents, val bus: IEventPublisher = null) extends IRepository with Logging {

  def save(aggregate: AggregateRoot, expectedVersion: Int) = {
    store.saveEvents(aggregate.id, aggregate.getUncommittedChanges, expectedVersion)
    //aggregate.markChangesCommitted
    // FIXME: Publish to bus
  }

  private def openStream(id: GUID, version: Int, snapshot: Option[eventstore.Snapshot]): eventstore.IEventStream = {
    snapshot match {
      case None => store.openStream(id, 0, version)
      case Some(s: eventstore.Snapshot) => store.openStream(s, version)
    }
  }

  private def getAggregate[T <: AggregateRoot: ClassTag](atype: Class[_], snapshot: Option[eventstore.Snapshot], stream: eventstore.IEventStream): T = {
    log.debug("Trying to create a {}", atype.getName())
    val aggregate = snapshot match {
      case None => {
        atype.newInstance().asInstanceOf[T]
      }
      case Some(s: eventstore.Snapshot) => {
        val aggr = atype.newInstance().asInstanceOf[T]
        val mem = s.payload.asInstanceOf[IMemento]
        aggr.loadFromMemento(mem, s.streamId, s.streamRevision)
        aggr
      }
    }
    aggregate
  }

  def getById[T <: AggregateRoot: ClassTag](id: GUID, tmpl: T): T = {
    val atype = classTag[T].runtimeClass
    log.debug("getById {}", atype.getName)
    val versionToLoad = Int.MaxValue
    val snapshot = store.advanced.getSnapshot(id, versionToLoad)
    val stream = openStream(id, versionToLoad, snapshot)

    val aggregate: T = getAggregate(atype, snapshot, stream)

    val evs = stream.committedEvents.map(ev => {
      val evb = ev.body.asInstanceOf[DomainEvent]
      evb
    })

    aggregate.loadFromHistory(evs, stream.streamRevision)
    aggregate
  }
}

trait IRepository {
  def save(aggregate: AggregateRoot, expectedVersion: Int)
  def getById[T <: AggregateRoot: ClassTag](id: GUID, tmpl: T): T
}

abstract class Repository(private val storage: IEventStore) extends IRepository {
  def save(aggregate: AggregateRoot, expectedVersion: Int) = {
    storage.saveEvents(aggregate.id, aggregate.getUncommittedChanges, expectedVersion)
  }

  def getById[T <: AggregateRoot](id: GUID, tmpl: T): T = {
    ???
    /*
    val obj = tmpl
    var e = storage.getEventsForAggregate(id)
    obj.loadFromHistory(e)
    obj*/
  }
}

abstract class AggregateRoot extends Logging {

  private var changes = List[DomainEvent]()
  private var revision: Int = 0

  def id: GUID

  private def uncommittedChanges = changes

  def getUncommittedChanges = uncommittedChanges.toIterable
  def getRevision = revision

  // aka raiseEvent
  def applyChange(e: DomainEvent, isNew: Boolean = true) = {
    try {
      val x = this.getClass().getMethod("handle", e.getClass())
      x.invoke(this, e)
    } catch {
      case e: NoSuchMethodException => log.warn("Aggregate has no handler for {}", e.getClass)
      case e: Throwable => throw e
    }
    if (isNew) changes = changes :+ e
  }

  def loadFromHistory(history: Traversable[DomainEvent], newRevision: Int) {
    for (event <- history) {
      applyChange(event, false)
    }
    revision = newRevision
  }

  protected def loadState(state: IMemento) = ???

  def loadFromMemento(state: IMemento, streamId: GUID, streamRevision: Int) = {
    loadState(state)
    changes = Nil
    revision = streamRevision
    // id = streamId
  }
}
