package eventstore.persistence

import eventstore._
import com.mongodb.casbah.Imports._
import com.novus.salat._
import com.novus.salat.global._
import com.mongodb.DBObject

case class MongoCommitId(streamId: Guid, commitSequence: Int)
case class MongoCommitEvent(streamRevision: Int, payload: EventMessage)
case class MongoCommit(_id: MongoCommitId, commitId: Guid, commitStamp: Long, headers: Map[String, Object] = Map(), events: Seq[MongoCommitEvent], dispatched: Boolean)
case class MongoSnapshotId(streamId: Guid, streamRevision: Int)
case class MongoSnapshot(_id: MongoSnapshotId, payload: Object)

object Mongo {

  // These lines are useful for Mongo debugging type issues
  // Remember to remove import com.novus.salat.global._ if used!
//  implicit val ctx = new Context {
//    val name = "TestContext-Always"
//    override val typeHintStrategy = StringTypeHintStrategy(when = TypeHintFrequency.Always, typeHint = TypeHint)
//  }

  def toMongoCommit(commit: Commit): DBObject = {
    var streamRevision = commit.streamRevision - (commit.events.size - 1);
    var events = commit.events.map(e => {
      val d = MongoCommitEvent(streamRevision = streamRevision, payload = e)
      streamRevision += 1
      d
    })
    val commitStamp = commit.commitStamp // FIXME!
    //val commitStamp = org.joda.time.DateTime.now()
    val msg = MongoCommit(MongoCommitId(commit.streamId, commit.commitSequence), commitId = commit.commitId, commitStamp, commit.headers, events, false)
    grater[MongoCommit].asDBObject(msg)
  }

  def fromMongoCommit(mcomdb: DBObject) = {
    val mcom = grater[MongoCommit].asObject(mcomdb)
    val streamRevision = mcom.events.last.streamRevision

    val events = mcom.events.map(_.payload)
    Commit(mcom._id.streamId, streamRevision, mcom.commitId, mcom._id.commitSequence, mcom.commitStamp, mcom.headers, events.toList)
  }

  def toMongoSnapshot(snapshot: Snapshot): DBObject = {
    val msg = MongoSnapshot(MongoSnapshotId(snapshot.streamId, snapshot.streamRevision), snapshot.payload)
    grater[MongoSnapshot].asDBObject(msg)
  }

  def fromMongoSnapshot(m: DBObject): Snapshot = {
    val msg = grater[MongoSnapshot].asObject(m)
    Snapshot(msg._id.streamId, msg._id.streamRevision, msg.payload)
  }

}