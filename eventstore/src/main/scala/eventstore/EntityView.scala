package eventstore

import CQRS.{AggregateRoot, _}

import scala.concurrent.Future
import scala.reflect.ClassTag

class EntityView(registry : DomainEvent=>AggregateRoot) extends EventStreamReceiver{
  case class Memento(initial:DomainEvent, state:Object, revision:Int)

  var entities : Map[GUID, Memento] = Map()


  override def handle(ce: CommitedEvent): Future[Unit] = {
    val id = ce.streamId
    // Ensure we have something in the cache
    val memento : Memento = if (entities.contains(id)) {
      entities(id)
    }
    else {
      val e = registry(ce.event)
      val state = e.getState
      val m = Memento(ce.event, state, ce.streamRevision)
      entities = entities +(id -> m)
      m
    }

    val entity = registry(memento.initial)
    entity.loadFromMemento(memento.state, id, memento.revision)
    // Apply state change
    entity.loadFromHistory(Seq(ce.event), ce.streamRevision)
    // Resave the snapshot
    val old = entities(id)
    val updated = old.copy(revision = ce.streamRevision, state = entity.getState)
    entities += (id -> updated)
    Future.successful()
  }

  def getById[T <: AggregateRoot : ClassTag](id: GUID, tmpl: T): T = {
    //entities.getOrElse(id, tmpl) asInstanceOf[T]
    if (entities.contains(id)) {
      val m = entities(id)
      tmpl.loadFromMemento(m.state, id, m.revision)
    }
    tmpl
  }
}
