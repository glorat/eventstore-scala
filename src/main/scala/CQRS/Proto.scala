package CQRS

import eventstore.IPersistStreams
import eventstore.CommitedEvent
import akka.actor.Actor
import eventstore.Logging
import eventstore.EventDateTime

object Proto {

}


trait EventStreamReceiver {
  var lastEvent = EventDateTime.zero
  def handle(ce:CommitedEvent)
}

object EventBus extends Logging {
  var observers : Seq[EventStreamReceiver] = Seq()
}

object OnDemandEventBus extends Logging {
  var time = EventDateTime.zero
  var registrations : Seq[EventStreamReceiver] = Seq(InventoryItemDetailView,InventoryListView)

  def pollEventStream(s: IPersistStreams): Unit = {
    val cms = s.getFrom(time)
    if (cms.size > 0) {
      val ret = cms.flatMap(_.getEvents).foreach(c => handle(c))
      time = cms.last.commitStamp
    }
  }

  def handle(ce: CommitedEvent): Unit = {
    // Publish to registrations
    // These could be done in parallel!
    registrations.foreach(_.handle(ce))
  }
}

object PollingEventBus extends Actor with Logging {
  var time = EventDateTime.zero

  def receive = {
    case s: IPersistStreams => pollEventStream(s)
    case _ => throw new Exception("Gah")
  }
  
  def pollEventStream(s:IPersistStreams) = OnDemandEventBus.pollEventStream(s)

}
