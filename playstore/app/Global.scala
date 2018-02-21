import CQRS._
import cakesolutions.kafka.testkit.KafkaServer
import eventstore.{KafkaEventDispatcher, KafkaEventStore}
import play.api._

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

object Global extends GlobalSettings {



  override def onStart(app: Application) {
    Logger.info("Global application has started")
  }

  override def onStop(app: Application) {
    Logger.info("Global application shutdown...")
  }

}
