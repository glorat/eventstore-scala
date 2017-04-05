package CQRS

import eventstore.IPersistStreams
import eventstore.CommitedEvent
import akka.actor.Actor
import eventstore.Logging
import eventstore.EventDateTime
import akka.actor.Props
import akka.actor.ActorRef
import akka.pattern.ask

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._

class CommandHandlerActor(handler: CommandHandler) extends Actor {
  def receive = {
    case c: Command => {
      try {
        handler.receive(c)
        sender() ! akka.actor.Status.Success
      } catch {
        case e: Exception =>
          sender() ! akka.actor.Status.Failure(e)
          throw e
      }
    }
  }
}

object CommandHandlerActor {
  def props(handler:CommandHandler) : Props = Props(new CommandHandlerActor(handler))

}

class SyncCommandHandlerActor(handler: CommandHandler, readside:ActorRef) extends Actor with Logging {
  def receive = {
    case c: Command => {
      try {
        handler.receive(c)
        readside.ask("Ping")(10 seconds)
        log.debug("Return success to sender")
        sender() ! c
      } catch {
        case e: Exception =>
          log.debug("Return failure to sender")
          sender() ! e
          throw e
      }
    }
  }
}

object SyncCommandHandlerActor {
  def props(handler:CommandHandler, readside:ActorRef) : Props = Props(new SyncCommandHandlerActor(handler, readside))

}

trait EventStreamReceiver {
  var lastEvent :EventDateTime = EventDateTime.zero
  def handle(ce: CommitedEvent) : Future[Unit]
}

object EventBus extends Logging {
  var observers: Seq[EventStreamReceiver] = Seq()
}

class OnDemandEventBus(var registrations: Seq[EventStreamReceiver])(implicit val ec:ExecutionContext) extends Logging {
  var time = EventDateTime.zero

  def pollEventStream(s: IPersistStreams): Future[Unit] = {
    val cms = s.getFrom(time + 1) // Need to move it a bit forward to be exclusive!

    if (cms.size > 0) {
      val ret = cms.flatMap(_.getEvents).map(c => handle(c))
      log.warn("Demand bus acquired {} more events", cms.size)
      time = cms.last.commitStamp
      Future.sequence(ret).map(x => ())
    }
    else {
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

class PollingEventBus(ondemand: OnDemandEventBus ,s: IPersistStreams) extends Actor with Logging {
  var time = EventDateTime.zero
  import scala.concurrent.duration._

  // This line seems to magically provide the implicit EvaluationContext
  import context.dispatcher
  //context.system.scheduler.schedule(0 seconds, 5 seconds)(self ! s)

  def receive = {
    case foo: Any => {
      pollEventStream(s)
      sender ! "OK" // Read side updated
    }
    case _ => throw new Exception("Gah")
  }

  def pollEventStream(s: IPersistStreams) = ondemand.pollEventStream(s)
}
object PollingEventBus {
  def props(ondemand:OnDemandEventBus, s: IPersistStreams): Props = {
    Props(new PollingEventBus(ondemand,s))
  }
}
