package controllers

import CQRS._
import cakesolutions.kafka.testkit.KafkaServer
import eventstore.{KafkaEventDispatcher, KafkaEventStore}

import scala.concurrent.{ExecutionContext, Future}

object Environment {

  implicit val ec:ExecutionContext = play.api.libs.concurrent.Execution.Implicits.defaultContext

  val kafka = new KafkaServer()
  kafka.startup()
  val kafkaPort = kafka.kafkaPort



  // default Actor constructor
  //val cmdActor = new InventoryCommandActor(cmds)
  val bdb = new BullShitDatabase
  val viewActor = new InventoryItemDetailView(bdb)

  val repo = new KafkaEventStore(s"localhost:${kafkaPort}", "inventory", None, InventoryItem.registry)
  val bus = new KafkaEventDispatcher(s"localhost:${kafkaPort}", "inventory", Seq(viewActor, new InventoryListView(bdb)))

  val inner = new InventoryCommandHandlers(repo)

  class CmdHandler extends InventoryCommandHandlers(repo) {
    override def receive: PartialFunction[Command, Future[Unit]] = {
      val x = super.receive
      x.andThen {
        x => bus.pollEventStream()
      }
    }
  }
  val cmds = new CmdHandler
  val read = new ReadModelFacade(bdb)

}
