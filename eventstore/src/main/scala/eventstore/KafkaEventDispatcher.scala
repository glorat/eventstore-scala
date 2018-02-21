package eventstore

import CQRS.EventStreamReceiver
import cakesolutions.kafka.{KafkaConsumer, KafkaDeserializer}
import org.apache.kafka.clients.consumer.OffsetResetStrategy

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

import scala.collection.JavaConverters._

class KafkaEventDispatcher(servers:String, topic:String, var registrations: Seq[EventStreamReceiver])(implicit val ec:ExecutionContext) extends eventstore.Logging{
  private val stringSerializer = (msg: String) => msg.getBytes
  private val stringDeserializer = (bytes: Array[Byte]) => new String(bytes)
  private def randomString: String = Random.alphanumeric.take(5).mkString("")

  val consumer = createConsumer

  private def createConsumer = {
    val consumer = KafkaConsumer(consumerConfig)

    import org.apache.kafka.common.TopicPartition
    import java.util
    val partition0 = new TopicPartition(topic, 0)
    consumer.assign(util.Arrays.asList(partition0))
    consumer
  }

  def pollEventStream(): Future[Unit] = {

    log.debug("KafkaEventDispatcher polling for more events")
    val records = consumer.poll(1000)
    val cms = records.asScala.map(record => record.value.asInstanceOf[CommitedEvent]).toSeq

    if (cms.size > 0) {
      log.debug("KafkaEventDispatcher acquired {} more events", cms.size)
      val ret = cms.map(c => handle(c))
      Future.sequence(ret).map(x => ())
    }
    else {
      log.debug("KafkaEventDispatcher had no more events")
      Future.successful()
    }
  }

  def handle(ce: CommitedEvent): Future[Unit] = {
    // Publish to registrations
    // These might be done in parallel!
    val all = registrations.map(_.handle(ce))
    Future.sequence(all).map(x => ())
  }

  def consumerConfig: KafkaConsumer.Conf[String, Object] = {
    KafkaConsumer.Conf(KafkaDeserializer(stringDeserializer),
      KafkaDeserializer(BinarySerializer.deserializer),
      bootstrapServers = servers,
      groupId = randomString,
      enableAutoCommit = false,
      autoOffsetReset = OffsetResetStrategy.EARLIEST)
  }

}
