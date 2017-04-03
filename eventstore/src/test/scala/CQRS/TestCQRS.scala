package CQRS

import org.scalatest.junit.AssertionsForJUnit
import eventstore._
import eventstore.persistence.Mongo
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.global._
import org.scalatest.FlatSpec

class TestCQRS extends FlatSpec {

  class MyFixture() {
    val id : CQRS.GUID = java.util.UUID.fromString("9d9814f5-f531-4d80-8722-f61dcc1679b8")
    val persistence = new InMemoryPersistenceEngine
    // val persistence = new MongoPersistenceEngine(MongoClient("localhost").getDB("test"), null)
    // persistence.purge

    val store = new OptimisticEventStore(persistence, Seq())
    val rep = new EventStoreRepository(store)
    val cmds = new InventoryCommandHandlers(rep)

    val bdb = new BullShitDatabase()
    val read = new ReadModelFacade(bdb)
    val bus = new OnDemandEventBus(Seq(new InventoryItemDetailView(bdb), new InventoryListView(bdb)))

    val sendCommand: Command => Unit = (cmd => { cmds.receive(cmd); bus.pollEventStream(store.advanced) })

  }

  def example(f : MyFixture) : Unit = {
    import com.novus.salat.global._

    import f._


    sendCommand(CreateInventoryItem(id, "test"))
    val detail = read.getInventoryItemDetails(id).get
    assert(InventoryItemDetailsDto(id, "test", 0, 1) == detail)
    val created = rep.getById(id, new InventoryItem)
    assert(created.getRevision == detail.version)
    assert(1 == detail.version)
    sendCommand(CheckInItemsToInventory(id, 10, detail.version))
    sendCommand(CheckInItemsToInventory(id, 20, detail.version + 1))

    val d2 = read.getInventoryItemDetails(id)
    assert(true == d2.isDefined)
    assert(InventoryItemDetailsDto(id, "test", 30, 3) == d2.get)
    // println("Current item" + ReadModelFacade.getInventoryItemDetails(id))

    val evs = store.advanced.getFrom(0).flatMap(_.events).map(em => em.body.asInstanceOf[DomainEvent])
    //evs.foreach(ev => println(ev))
    assert(3 == evs.size)
  }

  "Inventory example" should "do the obvious" in {
    example(new MyFixture)
  }



  it should "not allow duplicate or concurrent writes" in {
    val f = new MyFixture
    example(f)
    assertThrows[eventstore.ConcurrencyException] {
      f.sendCommand(CheckInItemsToInventory(f.id, 10, 2))

    }

  }
}
