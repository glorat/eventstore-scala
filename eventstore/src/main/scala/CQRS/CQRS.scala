package CQRS

import scala.reflect.runtime.universe._
import eventstore.{CommitedEvent, Logging}
import com.novus.salat.annotations.raw.Salat

import scala.concurrent.Future
import scala.reflect._

trait Message
@Salat
trait DomainEvent extends Message // with Product?
trait Command extends Message

trait CommandHandler {
  // Could have improved modelling here beyond Unit, since Command
  // failures aren't really Exception-al
  def receive: PartialFunction[Command, Future[Unit]]
}

trait IMemento extends Product

trait IRepository {
  def save(aggregate: AggregateRoot, expectedVersion: Int) : Future[Unit]
  def getById[T <: AggregateRoot: ClassTag](id: GUID, tmpl: T): T
}

trait EventStreamReceiver {
  def handle(ce: CommitedEvent) : Future[Unit]
}
