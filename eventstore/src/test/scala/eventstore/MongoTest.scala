package eventstore

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Assert._
import org.junit.Test
import org.junit.Before
import eventstore.persistence.Mongo
import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.global._

case class DummyEvent(val foo: String) extends CQRS.DomainEvent

class MongoTest extends AssertionsForJUnit {
  @Test def mongoCommitRoundtrip = {
    val streamId = java.util.UUID.randomUUID()
    val revision = 1
    val newGuid = java.util.UUID.randomUUID()
    val sequence = 1
    val events = List(EventMessage(body = DummyEvent("bar")))
    val commit = new Commit(streamId, revision, newGuid, sequence, EventDateTime.now, Map(), events)
    val mc = Mongo().toMongoCommit(commit)

    val mcs1 = mc.toString
    val commit2 = Mongo().fromMongoCommit(mc)
    // java.lang.AssertionError: expected:
    // <Commit(123,1,321,1,1367996016868,null,List(EventMessage(Map(),DummyEvent(bar))))> 
    // but was:
    // <Commit(123,1,321,1,1367996016915,null,List(EventMessage(Map(),DummyEvent(bar))))>

    assertEquals(commit, commit2)

  }

}