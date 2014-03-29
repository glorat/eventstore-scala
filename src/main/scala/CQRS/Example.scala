package CQRS

import eventstore._
import eventstore.persistence.MongoPersistenceEngine
import com.mongodb.casbah.Imports._

case class DeactivateInventoryItem(inventoryItemId: GUID, originalVersion: Int) extends Command
case class CreateInventoryItem(inventoryItemId: GUID, name: String) extends Command
case class RenameInventoryItem(inventoryItemId: GUID, newName: String, originalVersion: Int) extends Command
case class CheckInItemsToInventory(inventoryItemId: GUID, count: Int, originalVersion: Int) extends Command
case class RemoveItemsFromInventory(inventoryItemId: GUID, count: Int, originalVersion: Int) extends Command

case class InventoryItemCreated(id: GUID, name: String) extends DomainEvent
case class InventoryItemRenamed(id: GUID, newName: String) extends DomainEvent
case class InventoryItemDeactivated(id: GUID) extends DomainEvent
case class ItemsCheckedInToInventory(id: GUID, count: Int) extends DomainEvent
case class ItemsRemovedFromInventory(id: GUID, count: Int) extends DomainEvent

case class InventoryItemState(id: GUID, activated: Boolean)

class InventoryItem extends AggregateRoot {
  var activated: Boolean = false
  var id: GUID = java.util.UUID.randomUUID()

  def this(id_ : GUID, name_ : String) = {
    this()
    applyChange(InventoryItemCreated(id_, name_))
  }

  def handle(e: InventoryItemCreated) = {
    id = e.id
    activated = true
  }

  def handle(e: InventoryItemDeactivated) = {
    activated = false
  }

  def changeName(newName: String) =
    {

      //if (string.IsNullOrEmpty(newName)) throw new ArgumentException("newName");
      applyChange(InventoryItemRenamed(id, newName));
    }

  def remove(count: Int) {
    if (count <= 0) throw new Exception("cant remove negative count from inventory");
    applyChange(ItemsRemovedFromInventory(id, count));
  }

  def checkIn(count: Int) {
    if (count <= 0) throw new Exception("must have a count greater than 0 to add to inventory");
    applyChange(ItemsCheckedInToInventory(id, count));
  }

  def deactivate() {
    if (!activated) throw new Exception("already deactivated");
    applyChange(InventoryItemDeactivated(id));
  }

}

class InventoryCommandHandlers(repository: IRepository) {
  def handle(c: CreateInventoryItem) = {
    val item = new InventoryItem(c.inventoryItemId, c.name)
    repository.save(item, -1)
  }

  def handle(c: DeactivateInventoryItem) = {
    val item = repository.getById(c.inventoryItemId, new InventoryItem)
    item.deactivate
    repository.save(item, c.originalVersion)
  }

  def handle(c: RemoveItemsFromInventory) = {
    val item = repository.getById(c.inventoryItemId, new InventoryItem)
    item.remove(c.count)
    repository.save(item, c.originalVersion)
  }
  def handle(c: CheckInItemsToInventory) = {
    val item = repository.getById(c.inventoryItemId, new InventoryItem)
    item.checkIn(c.count)
    repository.save(item, c.originalVersion)
  }
  def handle(c: RenameInventoryItem) = {
    val item = repository.getById(c.inventoryItemId, new InventoryItem)
    item.changeName(c.newName)
    repository.save(item, c.originalVersion)
  }
}

object Example extends App {
  // val bus =
  // val persistence = new InMemoryPersistenceEngine
  val persistence = new MongoPersistenceEngine(MongoClient("localhost").getDB("test2"), null)
  persistence.purge

  val store = new OptimisticEventStore(persistence, Seq())
  val rep = new EventStoreRepository(store)
  val cmds = new InventoryCommandHandlers(rep)

  val id = java.util.UUID.randomUUID()
  println("Creating item with id " + id)
  cmds.handle(CreateInventoryItem(id, "test"))
  val created = rep.getById(id, new InventoryItem)

  cmds.handle(CheckInItemsToInventory(id, 10, created.getRevision))
  val evs = store.advanced.getFrom(0).flatMap(_.events).map(em => em.body.asInstanceOf[DomainEvent])
  evs.foreach(ev => println(ev))
  println("Dupe it")
  cmds.handle(CheckInItemsToInventory(id, 10, created.getRevision))
  val evs2 = store.advanced.getFrom(0).flatMap(_.events).map(em => em.body.asInstanceOf[DomainEvent])
  evs2.foreach(ev => println(ev))
}
