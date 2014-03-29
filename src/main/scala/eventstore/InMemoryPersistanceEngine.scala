package eventstore

class InMemoryPersistenceFactory extends IPersistenceFactory {
  def build: IPersistStreams = new InMemoryPersistenceEngine
}

class InMemoryPersistenceEngine extends IPersistStreams with Logging {

  var commits = List[Commit]()
  var heads = List[StreamHead]()
  var undispatched = List[Commit]()
  var snapshots = List[Snapshot]()
  var stamps = Map[Guid, EventDateTime]()

  def initialize = {
    log.info("Initialized")
  }

  def getFrom(streamId: Guid, minRevision: Int, maxRevision: Int) = {
    commits.synchronized {
      commits.filter(x => x.streamId == streamId && x.streamRevision >= minRevision && (x.streamRevision - x.events.size + 1) <= maxRevision)
    }
  }

  // TODO: Check - All commits are ordered in time aren't they?

  def getFrom(start: EventDateTime) = {
    commits.dropWhile(s => s.commitStamp < start)
  }

  def getFromTo(start: EventDateTime, end: EventDateTime) = {
    val froms = getFrom(start)
    froms.takeWhile(c => c.commitStamp <= end)
  }

  def commit(attempt: Commit) {
    commits.synchronized {
      if (commits.contains(attempt))
        throw new DuplicateCommitException // FIXME: DuplicateCommitException
      if (commits.exists(c => c.streamId == attempt.streamId && c.streamRevision == attempt.streamRevision))
        throw new ConcurrencyException("Concurrent Write")

      stamps = stamps + (attempt.commitId -> attempt.commitStamp)
      commits = commits :+ attempt
      undispatched = undispatched :+ attempt
      val head = heads.find(x => x.streamId == attempt.streamId) //.collectFirst(x => x.streamId = attempt.streamId)
      if (head.isDefined) heads = remove(head.get, heads)
      // Logger
      val snapshotRevision = if (head.isDefined) head.get.snapshotRevision else 0
      heads = heads :+ (StreamHead(attempt.streamId, attempt.streamRevision, snapshotRevision))
    }
  }

  def getUndispatchedCommits = {
    commits.synchronized {
      commits.filter(c => undispatched.contains(c))
    }
  }

  def markCommitAsDispatched(commit: Commit) = {
    commits.synchronized {
      undispatched = remove(commit, undispatched)
    }
  }

  def getStreamsToSnapshot(maxThreshold: Int) = {
    commits.synchronized {
      val x = heads.filter(x => x.headRevision >= x.snapshotRevision + maxThreshold)
      x.map(stream => new StreamHead(stream.streamId, stream.headRevision, stream.snapshotRevision))

    }
  }

  def getSnapshot(streamId: Guid, maxRevision: Int): Option[Snapshot] = {
    log.warn("In memory doesn't support snapshots yet")
    None
    /*
    commits.synchronized {
      snapshots.filter(x => x.streamId == streamId && x.streamRevision <= maxRevision)
      throw new NotImplementedError
    }*/
  }

  def addSnapshot(snapshot: Snapshot): Boolean = {
    log.debug("AddingSnapshot id:{} revision:{}", snapshot.streamId, snapshot.streamRevision);

    commits.synchronized {
      val currentHead = heads.find(h => h.streamId == snapshot.streamId)
      //var currentHead = this.heads.FirstOrDefault(h => h.StreamId == snapshot.StreamId);
      if (currentHead.isDefined) {
        val newhead = currentHead.get
        snapshots :+= snapshot
        heads = remove(newhead, heads)
        heads :+= new StreamHead(newhead.streamId, newhead.headRevision, snapshot.streamRevision)
        true
      } else {
        false
      }
    }
  }

  def purge: Unit = {
    commits.synchronized {
      commits = Nil
      snapshots = Nil
      heads = Nil
    }
  }

  def remove[A](num: A, list: List[A]) = list diff List(num)

}