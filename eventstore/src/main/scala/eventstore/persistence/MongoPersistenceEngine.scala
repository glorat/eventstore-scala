package eventstore.persistence

import eventstore._
import com.mongodb.casbah.Imports._
import com.mongodb.WriteConcern
import com.novus.salat._

class MongoPersistenceEngine(store: MongoDB, serializer: IDocumentSerializer)(implicit val ctx : com.novus.salat.Context)
  extends IPersistStreams with eventstore.Logging {
  // Statics
  private val ConcurrencyException = "E1100";

  private val persistedCommits = store("commits");
  private val persistedSnapshots = store("snapshots")
  private val persistedStreamHeads = store("streams")

  /*this.commitSettings = this.store.CreateCollectionSettings<BsonDocument>("Commits");
			this.commitSettings.AssignIdOnInsert = false;
			this.commitSettings.SafeMode = SafeMode.True;

			this.snapshotSettings = this.store.CreateCollectionSettings<BsonDocument>("Snapshots");
			this.snapshotSettings.AssignIdOnInsert = false;
			this.snapshotSettings.SafeMode = SafeMode.False;

			this.streamSettings = this.store.CreateCollectionSettings<BsonDocument>("Streams");
			this.streamSettings.AssignIdOnInsert = false;
			this.streamSettings.SafeMode = SafeMode.False;
			* 
			*/
  def initialize(): Unit =
    {
      //if (Interlocked.Increment(ref this.initialized) > 1)
      //	return;

      log.debug("InitializingStorage");

      this.tryMongo(() =>
        {

          this.persistedCommits.ensureIndex(MongoDBObject("dispatched" -> 1), "dispatchedIndex", false)
          this.persistedCommits.ensureIndex(MongoDBObject("_id.streamId" -> 1, "events.streamRevision" -> 1), "getFromIndex", true)
          this.persistedCommits.ensureIndex(MongoDBObject("commitStamp" -> 1), "commitStampIndex", false)
          this.persistedStreamHeads.ensureIndex(MongoDBObject("unsnapshotted" -> 1), "unsnapshottedIndex", false)
        });
    }

  def transactionCount = {
    this.persistedCommits.size
  }       
  
  // Members declared in eventstore.ICommitEvents   
  def commit(attempt: eventstore.Commit): Unit = {
    log.debug("AttemptingToCommit", (attempt.events.size, attempt.streamId, attempt.commitSequence))
    tryMongo(() =>
      {
        var toser = Mongo().toMongoCommit(attempt)

        try {
          // for concurrency / duplicate commit detection safe mode is required
          persistedCommits.insert(toser, WriteConcern.SAFE);
          this.updateStreamHeadAsync(attempt.streamId, attempt.streamRevision, attempt.events.size);
          log.debug("CommitPersisted {}", attempt.commitId);
        } catch {
          case e: MongoException =>
            if (!e.getMessage.contains(ConcurrencyException))
              throw e;

            val qry = grater[MongoCommitId].asDBObject(MongoCommitId(attempt.streamId, attempt.streamRevision))
            val savedMongoCommit = this.persistedCommits.findOne(qry);
            if (savedMongoCommit.isDefined) {
              val savedCommit = Mongo().fromMongoCommit(savedMongoCommit.get)
              if (savedCommit.commitId == attempt.commitId)
                throw new DuplicateCommitException();
            } else {
              log.debug("ConcurrentWriteDetected");
              throw new ConcurrencyException("Concurrent Write")
            }

        }
      });
  }

  def getFrom(streamId: eventstore.Guid, minRevision: Int, maxRevision: Int): Seq[eventstore.Commit] = {
    log.debug("GettingAllCommits for {} between {} and {}", (streamId, minRevision, maxRevision))

    this.tryMongo(() =>
      {
        val query: DBObject = ("events.streamRevision" $gte minRevision $lte maxRevision) ++ ("_id.streamId" -> streamId)
        val res = this.persistedCommits.find(query).sort(MongoDBObject("events.streamRevision" -> 1))
        res.map(x => Mongo().fromMongoCommit(x)).toSeq
      })
  }
  // Members declared in eventstore.IPersistStreams   
  def getFrom(start: eventstore.EventDateTime): Seq[eventstore.Commit] = {
    this.tryMongo(() =>
      {
        val query: DBObject = ("commitStamp" $gte start)
        val res = this.persistedCommits.find(query).sort(MongoDBObject("commitStamp" -> 1))
        res.map(x => Mongo().fromMongoCommit(x)).toSeq
      })

  }
  def getFromTo(start: eventstore.EventDateTime, end: eventstore.EventDateTime): Seq[eventstore.Commit] = {
    this.tryMongo(() =>
      {
        val query: DBObject = ("commitStamp" $gte start $lt end)
        val res = this.persistedCommits.find(query).sort(MongoDBObject("commitStamp" -> 1))
        res.map(x => Mongo().fromMongoCommit(x)).toSeq
      })
  }

  def getUndispatchedCommits(): Seq[eventstore.Commit] = {
    tryMongo(() => {
      val qry: DBObject = MongoDBObject("dispatched" -> false)
      val srt = MongoDBObject("commitStamp" -> 1)
      val res = persistedCommits.find(qry).sort(srt)
      res.map(x => Mongo().fromMongoCommit(x)).toSeq
    })
  }
  def markCommitAsDispatched(commit: eventstore.Commit): Unit = {
    log.debug("Marking Commit As Dispatched {}", commit.commitId)
    tryMongo(() => {
      val qry = Mongo().toMongoCommit(commit)
      val update = MongoDBObject("dispatched" -> true)
      persistedCommits.update(qry, update, upsert = false, concern = WriteConcern.SAFE)
    })

  }
  def purge: Unit = {
    log.warn("Purging Storage")
    persistedCommits.drop
    persistedSnapshots.drop
    persistedStreamHeads.drop
  }
  // IPersistSnapshots
  def addSnapshot(snapshot: eventstore.Snapshot): Boolean = {
    // if (snapshot == None) return false
    log.debug("Adding snapshot {} {}", snapshot.streamId, snapshot.streamRevision)
    try {
      val mongoSnapshot = Mongo().toMongoSnapshot(snapshot)
      val qry = MongoDBObject("_id" -> mongoSnapshot("_id"))
      val update = $set("payload" -> mongoSnapshot("payload"))
      persistedSnapshots.update(qry, update, upsert = true)
      true
    } catch {
      case _: Exception => false
    }
  }

  def getSnapshot(streamId: eventstore.Guid, maxRevision: Int): Option[eventstore.Snapshot] = {
    None
    // FIXME:
    /*
    tryMongo(() => {
      val qry: MongoDBObject = ???
      val srt = MongoDBObject("_id" -> -1) // Descending
      val res = persistedSnapshots.find(qry).sort(srt).limit(1)
      if (res.size > 0) Some(grater[Snapshot].asObject(res.next)) else None
    })*/
  }
  def getStreamsToSnapshot(maxThreshold: Int): Iterable[eventstore.StreamHead] = {
    log.debug("GettingStreamsToSnapshot")
    tryMongo(() => {
      val qry = "Unsnapshotted" $gte maxThreshold
      val srt = MongoDBObject("Unsnapshotted" -> -1) // Descending
      val res = persistedStreamHeads.find(qry).sort(srt)
      // FIXME: Beware the GUID fudge
      res.map(x => grater[StreamHead].asObject(x)).toSeq
    })
  }

  private def updateStreamHeadAsync(streamId: Guid, streamRevision: Int, eventsCount: Int) {
    // TODO: Queue this on a threadpool?
    tryMongo(() => {
      val upd = $set("headRevision" -> streamRevision) ++ $inc("snapshotRevision" -> 0) ++ $inc("unshapshotted" -> eventsCount)
      persistedStreamHeads.update(
        MongoDBObject("_id" -> streamId), upd, upsert = true)

    })
  }

  protected def tryMongo[T](callback: () => T): T =
    {
      //if (this.disposed)
      //	throw new ObjectDisposedException("Attempt to use storage after it has been disposed.");

      try {
        callback()
      } catch {
        case e: MongoException => {
          log.error("StorageUnavailable" + e.toString())
          throw new /*StorageUnavailable*/ Exception(e.getMessage, e)
        }
      }
    }
}

trait IDocumentSerializer
/*
class MongoPersistenceFactory(val connectionName: String, dbName: String, serializer: IDocumentSerializer) extends IPersistenceFactory {

  def build: IPersistStreams =
    {
      var connectionString = transformConnectionString(this.getConnectionString());
      var database = MongoClient(connectionString).getDB(dbName)
      return new MongoPersistenceEngine(database, this.serializer);
    }

  protected def getConnectionString(): String =
    {
      //ConfigurationManager.ConnectionStrings(this.connectionName).ConnectionString;
      // FIXME:
      "localhost"
    }

  protected def transformConnectionString(connectionString: String): String =
    {
      connectionString
    }
}*/

