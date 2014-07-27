package CQRS

import scala.reflect.runtime.universe._
import eventstore.Logging
import com.novus.salat.annotations.raw.Salat
import scala.reflect._


abstract class AggregateRoot extends Logging {

  private var changes = List[DomainEvent]()
  private var revision: Int = 0

  def id: GUID

  private def uncommittedChanges = changes

  def getUncommittedChanges = uncommittedChanges.toIterable
  def getRevision = revision

  def handle: PartialFunction[DomainEvent, Unit]

  // aka raiseEvent
  def applyChange(e: DomainEvent, isNew: Boolean = true) = {
    if (handle.isDefinedAt(e)) {
      handle(e)
    }
    if (isNew) changes = changes :+ e
  }

  private[CQRS] def loadFromHistory(history: Traversable[DomainEvent], newRevision: Int) {
    for (event <- history) {
      applyChange(event, false)
    }
    revision = newRevision
  }

  protected def loadState(state: IMemento) = ???

  private[CQRS] def loadFromMemento(state: IMemento, streamId: GUID, streamRevision: Int) = {
    loadState(state)
    changes = Nil
    revision = streamRevision
    // id = streamId
  }
}
