package CQRS

import scala.reflect.runtime.universe._
import eventstore.Logging
import com.novus.salat.annotations.raw.Salat

import scala.concurrent.Future
import scala.reflect._

trait Message
@Salat
trait DomainEvent extends Message /* with Product // But Salat barfs on that */
trait Command extends Message

trait IEventStore {
  def saveEvents(aggregateId: GUID, events: Traversable[DomainEvent], expectedVersion: Int) : Future[Unit]
  def getEventsForAggregate(aggregateId: GUID): List[DomainEvent]
}
trait IEventPublisher

trait CommandHandler {
  // Could have improved modelling here beyond Unit, since Command
  // failures aren't really Exception-al
  def receive: PartialFunction[Command, Future[Unit]]
}

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

  private def getAggregate[T <: AggregateRoot: ClassTag](tmpl: T, snapshot: Option[eventstore.Snapshot], stream: eventstore.IEventStream): T = {
    log.debug("Trying to create a {}", tmpl.getClass().getName())
    val aggregate = snapshot match {
      case None => {
        tmpl
      }
      case Some(s: eventstore.Snapshot) => {
        val aggr = tmpl
        val mem = s.payload.asInstanceOf[IMemento]
        aggr.loadFromMemento(mem, s.streamId, s.streamRevision)
        aggr
      }
    }
    aggregate
  }

  def getById[T <: AggregateRoot: ClassTag](id: GUID, tmpl: T): T = {
    if (log.isDebugEnabled()) {
      val atype = classTag[T].runtimeClass
      log.debug("getById {}", atype.getName)
    }
    val versionToLoad = Int.MaxValue
    val snapshot = store.advanced.getSnapshot(id, versionToLoad)
    val stream = openStream(id, versionToLoad, snapshot)

    val aggregate: T = getAggregate(tmpl, snapshot, stream)

    val evs = stream.committedEvents.map(ev => {
      val evb = ev.body.asInstanceOf[DomainEvent]
      evb
    })

    aggregate.loadFromHistory(evs, stream.streamRevision)
    aggregate
  }
}

trait IRepository {
  def save(aggregate: AggregateRoot, expectedVersion: Int) : Future[Unit]
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
