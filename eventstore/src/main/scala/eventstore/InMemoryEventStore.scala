package eventstore

import CQRS._

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.classTag

class InMemoryDispatcher(store:InMemoryEventStore, var registrations: Seq[EventStreamReceiver])(implicit val ec:ExecutionContext)
extends Logging{
  private var pos = -1

  def pollEventStream(): Future[Unit] = {


    log.debug("InMemoryDispatcher polling for more events")
    val snap = store.commitedEvents
    if (snap.size > pos) {
      val cms = snap.slice(pos+1, snap.size+1)
      log.debug("InMemoryDispatcher acquired {} more events", cms.size)
      val ret = cms.map(c => handle(c))
      Future.sequence(ret).map(x => ())
    }
    else {
      log.debug("InMemoryDispatcher had no more events")
      Future.successful()
    }
  }

  def handle(ce: CommitedEvent): Future[Unit] = {
    // Publish to registrations
    // These might be done in parallel!
    val all = registrations.map(_.handle(ce))
    Future.sequence(all).map(x => ())
  }
}

class InMemoryEventStore(streamToRevision:Option[GUID=>Int], registry:DomainEvent=>AggregateRoot)(implicit val ec:ExecutionContext) extends IRepository with Logging {
  var commitedEvents: List[CommitedEvent] = List()
  val entityView = new EntityView(registry)

  override def save(aggregate: AggregateRoot, expectedVersion: Int): Future[Unit] = {
    if (streamToRevision.isDefined) {
      val latestVersion = streamToRevision.get(aggregate.id)
      if (expectedVersion < latestVersion) {
        // Someone saved already
        throw new ConcurrencyException(s"Trying to save aggregate from version ${expectedVersion} when ${latestVersion} already in DB")
      }
    }

    val evs = aggregate.getUncommittedChanges
    var i = expectedVersion
    val cevs = evs.map(ev => {
      i += 1
      CommitedEvent(ev, aggregate.id, i)
    })

    commitedEvents ++= cevs

    val foo = cevs.map(e => entityView.handle(e))
    Future.sequence(foo).map(_ => ())
  }

  override def getById[T <: AggregateRoot : ClassManifest](id: GUID, tmpl: T): T = {
    entityView.getById(id, tmpl)
  }
}
