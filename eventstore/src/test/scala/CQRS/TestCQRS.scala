package CQRS

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Assert._
import org.junit.Test
import org.junit.Before
import eventstore._
import eventstore.persistence.Mongo
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.global._

class TestCQRS extends AssertionsForJUnit {
  val id = java.util.UUID.fromString("9d9814f5-f531-4d80-8722-f61dcc1679b8")
  val persistence = new InMemoryPersistenceEngine
  // val persistence = new MongoPersistenceEngine(MongoClient("localhost").getDB("test"), null)
  // persistence.purge

  val store = new OptimisticEventStore(persistence, Seq())
  val rep = new EventStoreRepository(store)
  val cmds = new InventoryCommandHandlers(rep)

  val bus = OnDemandEventBus

  val sendCommand: Command => Unit = (cmd => { cmds.receive(cmd); bus.pollEventStream(store.advanced) })

  @Test def example = {
    import com.novus.salat.global._

    val viewActor = InventoryItemDetailView
    sendCommand(CreateInventoryItem(id, "test"))
    val detail = ReadModelFacade.getInventoryItemDetails(id).get
    assertEquals(InventoryItemDetailsDto(id, "test", 0, 1), detail)
    val created = rep.getById(id, new InventoryItem)
    assertEquals(created.getRevision, detail.version)
    assertEquals(1, detail.version)
    sendCommand(CheckInItemsToInventory(id, 10, detail.version))
    sendCommand(CheckInItemsToInventory(id, 20, detail.version + 1))

    val d2 = ReadModelFacade.getInventoryItemDetails(id)
    assertTrue(d2.isDefined)
    assertEquals(InventoryItemDetailsDto(id, "test", 30, 3), d2.get)
    // println("Current item" + ReadModelFacade.getInventoryItemDetails(id))

    val evs = store.advanced.getFrom(0).flatMap(_.events).map(em => em.body.asInstanceOf[DomainEvent])
    //evs.foreach(ev => println(ev))
    assertEquals(3, evs.size)

  }

  @Test ( expected = classOf[eventstore.ConcurrencyException] )
  def dupeWrite = {
    example
    sendCommand(CheckInItemsToInventory(id, 10, 2))

  }
}