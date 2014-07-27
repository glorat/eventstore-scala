package controllers

import akka.actor._
import scala.concurrent.duration._
import play.api._
import play.api.libs.concurrent.Akka
import CQRS.OnDemandEventBus
import eventstore.IPersistStreams
import eventstore.Logging
import eventstore.EventDateTime
import CQRS.PollingEventBus
import CQRS.InventoryItemDetailView
import CQRS.EventStoreRepository
import eventstore.OptimisticEventStore
import CQRS._
import eventstore.InMemoryPersistenceEngine
import eventstore.persistence.MongoPersistenceEngine
import com.mongodb.casbah.MongoClient

class Actors(implicit app: Application) extends Plugin {

  import play.api.Play

  // We need this for Salat to work with Play
  implicit val ctx = new com.novus.salat.Context {
    val name = "Custom_Classloader"
  }
  ctx.registerClassLoader(Play.classloader(Play.current))

  val persistence = new InMemoryPersistenceEngine
  //val persistence = new MongoPersistenceEngine(MongoClient("localhost").getDB("test2"), null)
  //persistence.purge

  val store = new OptimisticEventStore(persistence, Seq())
  val rep = new EventStoreRepository(store)
  private val cmds = new InventoryCommandHandlers(rep)

  // default Actor constructor
  //val cmdActor = new InventoryCommandActor(cmds)
  val viewActor = InventoryItemDetailView

  lazy val bus = Akka.system.actorOf(PollingEventBus.props(persistence), name = "eventbus")
  lazy val cmdActor = Akka.system.actorOf(SyncCommandHandlerActor.props(cmds, bus), name = "commandHandler")

  override def onStart() = {
    //Akka.system.scheduler.schedule(0 seconds, 5 minutes)(println("do something"))
  }

  override def onStop() = {

  }

  override def enabled = true
}
