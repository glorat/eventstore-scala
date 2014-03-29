package eventstore

trait IPipelineHook

class OptimisticEventStore(persistence: IPersistStreams, pipelineHooks: Iterable[IPipelineHook] = Iterable())
  extends IStoreEvents with ICommitEvents with Logging {

  def createStream(streamId: Guid) = {
    new OptimisticEventStream(streamId, this)
  }

  def openStream(streamId: Guid, minRevision: Int, maxRevision: Int): IEventStream =
    {
      val max = if (maxRevision <= 0) Int.MaxValue else maxRevision;

      log.debug("OpeningStreamAtRevision {} {} {}", Array(streamId, minRevision, max));
      new OptimisticEventStream(streamId, this, minRevision, max);
    }
  def openStream(snapshot: Snapshot, origMaxRevision: Int): IEventStream =
    {
      if (snapshot == null)
        throw new IllegalArgumentException("snapshot");

      // Logger.Debug(Resources.OpeningStreamWithSnapshot, snapshot.StreamId, snapshot.StreamRevision, maxRevision);
      val maxRevision = if (origMaxRevision <= 0) Int.MaxValue else origMaxRevision;
      new OptimisticEventStream(snapshot, this, maxRevision)
    }

  def getFrom(streamId: Guid, minRevision: Int, maxRevision: Int): Seq[Commit] =
    {
      persistence.getFrom(streamId, minRevision, maxRevision)
      /*.foreach(commit => 
			{
				var filtered = commit;
				this.pipelineHooks.filter(x => (filtered = x.Select(filtered)) == null))
				{
					Logger.Info(Resources.PipelineHookSkippedCommit, hook.GetType(), commit.CommitId);
					break;
				}

				if (filtered == null)
					Logger.Info(Resources.PipelineHookFilteredCommit);
				else
					yield return filtered;
			}*/
    }
  def commit(attempt: Commit): Unit =
    {
      if (!attempt.isValid()) {
        // Logger.Debug(Resources.CommitAttemptFailedIntegrityChecks);
        return ;
      }
      /*
			foreach (var hook in this.pipelineHooks)
			{
				Logger.Debug(Resources.InvokingPreCommitHooks, attempt.CommitId, hook.GetType());
				if (hook.PreCommit(attempt))
					continue;

				Logger.Info(Resources.CommitRejectedByPipelineHook, hook.GetType(), attempt.CommitId);
				return;
			}*/

      // Logger.Info(Resources.CommittingAttempt, attempt.CommitId, attempt.Events.Count);
      this.persistence.commit(attempt);
      /*
			foreach (var hook in this.pipelineHooks)
			{
				Logger.Debug(Resources.InvokingPostCommitPipelineHooks, attempt.CommitId, hook.GetType());
				hook.PostCommit(attempt);
			}*/
    }

  def advanced(): IPersistStreams =
    {
      this.persistence
    }
}

class OptimisticEventStream(val streamId: Guid, persistence: ICommitEvents) extends IEventStream {

  def this(streamId: Guid, persistence: ICommitEvents, minRevision: Int, maxRevision: Int) = {

    this(streamId, persistence)
    var commits = persistence.getFrom(streamId, minRevision, maxRevision);
    this.populateStream(minRevision, maxRevision, commits);

    if (minRevision > 0 && this.committed.size == 0)
      throw new Exception // StreamNotFoundException();
  }

  def this(snapshot: Snapshot, persistence: ICommitEvents, maxRevision: Int) =

    {
      this(snapshot.streamId, persistence)
      var commits = persistence.getFrom(snapshot.streamId, snapshot.streamRevision, maxRevision);
      this.populateStream(snapshot.streamRevision + 1, maxRevision, commits);
      this._streamRevision = snapshot.streamRevision + this.committed.size;
    }

  private var committed = List[EventMessage]()
  private var events = List[EventMessage]()
  // TODO: Need to make these private
  var uncommittedHeaders = Map[String, Object]()
  var committedHeaders = Map[String, Object]()
  var identifiers = Set[Guid]()

  def committedEvents: Iterable[eventstore.EventMessage] = committed
  //def committedHeaders: Map[String,Object] = committedHeaders
  def uncommittedEvents: Iterable[eventstore.EventMessage] = events
  //def uncommittedHeaders: Map[String,Object] = uncommittedHeaders

  private var _commitSequence: Int = 0
  private var _streamRevision: Int = 0
  def commitSequence = _commitSequence
  def streamRevision = _streamRevision

  // TODO: Constructors: minRevision/maxRevision -> Populate
  protected def populateStream(minRevision: Int, maxRevision: Int, commits: Iterable[Commit]): Unit = {
    commits.foreach {
      commit =>
        {
          identifiers = identifiers + commit.commitId
          _commitSequence = commit.commitSequence
          val currentRevision = commit.streamRevision - commit.events.size + 1
          if (currentRevision <= maxRevision) {
            copyToCommitedHeaders(commit)
            copyToEvents(minRevision, maxRevision, currentRevision, commit)
          }
        }
    }
  }
  private def copyToCommitedHeaders(commit: Commit): Unit = {
    //commit.headers.keys.foreach(key => commitedHeaders(key) = commit.headers(key))
    commit.headers.keys.foreach(key => committedHeaders += key -> commit.headers(key))
  }

  private def copyToEvents(minRevision: Int, maxRevision: Int, currentRevision: Int, commit: Commit): Unit = {
    var curr = currentRevision

    // MEMO: Would be nice to make this more functional
    commit.events.foreach(event => {
      if (curr > maxRevision) {
        return
      }
      curr += 1
      if (curr < minRevision) {
        // Log Ignoring too early reivision
      } else {
        committed = committed :+ event
        _streamRevision = curr - 1
      }
    })
  }

  // Interface methods above
  // Specific class methods below
  def add(uncommitedEvent: EventMessage): Unit = {
    // assert null checks
    events = events :+ uncommitedEvent
  }

  def commitChanges(commitId: Guid): Unit = {
    if (identifiers.contains(commitId)) throw new DuplicateCommitException
    if (!hasChanges) return

    try {
      persistChanges(commitId)
    } catch {
      case e: ConcurrencyException => {
        val commits = persistence.getFrom(this.streamId, this.streamRevision + 1, Int.MaxValue)
        this.populateStream(this.streamRevision + 1, Int.MaxValue, commits)

        throw e
      }
    }
  }

  protected def hasChanges(): Boolean = {
    events.size > 0
  }

  protected def persistChanges(commitId: Guid) = {
    var attempt = buildCommitAttempt(commitId)
    // log.debug("Persisting Commit" + commitId +streamId)
    persistence.commit(attempt)
    populateStream(streamRevision + 1, attempt.streamRevision, Seq(attempt))
    clearChanges
  }
  protected def buildCommitAttempt(commitId: Guid): Commit = {
    // FIXME: Seems flaky to rely on clock for sequencing!
    val ts = EventDateTime.now
    new Commit(streamId, streamRevision + events.size, commitId, commitSequence + 1, ts, uncommittedHeaders.toMap, events)
  }
  def clearChanges: Unit = {
    events = events.takeWhile(_ => false)
    uncommittedHeaders = uncommittedHeaders.takeWhile(_ => false)
  }
}