package eventstore

class SomeDomainEvent(val value: String) extends CQRS.DomainEvent
case class AggregateMemento(value: String)

object Example extends App with Logging {
  //LogManager.getRootLogger().setLevel(Level.DEBUG);
  //val logger = LogManager.getLogger("Example");

  private val StreamId: Guid = java.util.UUID.randomUUID; // aggregate identifier
  /*
		private static readonly byte[] EncryptionKey = new byte[]
		{
			0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xa, 0xb, 0xc, 0xd, 0xe, 0xf
		};*/
  private var store: IStoreEvents = WireupEventStore
  run

  def run() =
    {
      log.warn("I am starting up")
      OpenOrCreateStream();
      AppendToStream();
      TakeSnapshot();
      LoadFromSnapshotForwardAndAppend();
      //scope.Complete();
    }

  def WireupEventStore(): IStoreEvents =
    {
      /*
			 return Wireup.Init()
				.LogToOutputWindow()
				.UsingInMemoryPersistence()
				.UsingSqlPersistence("EventStore") // Connection string is in app.config
					.WithDialect(new MsSqlDialect())
					.EnlistInAmbientTransaction() // two-phase commit
					.InitializeStorageEngine()
					.TrackPerformanceInstance("example")
					.UsingJsonSerialization()
						.Compress()
						.EncryptWith(EncryptionKey)
				.HookIntoPipelineUsing(new[] { new AuthorizationPipelineHook() })
				.UsingSynchronousDispatchScheduler()
					.DispatchTo(new DelegateMessageDispatcher(DispatchCommit))
				.Build();*/
      new OptimisticEventStore(new InMemoryPersistenceEngine, Seq())

    }
  def DispatchCommit(commit: Commit): Unit =
    {
      // This is where we'd hook into our messaging infrastructure, such as NServiceBus,
      // MassTransit, WCF, or some other communications infrastructure.
      // This can be a class as well--just implement IDispatchCommits.
      try {
        commit.events.foreach(event =>
          println(event.body))
      } catch {
        case _: Throwable => println("Cannot dispatch")
      }
    }

  private def OpenOrCreateStream(): Unit =
    {
      // we can call CreateStream(StreamId) if we know there isn't going to be any data.
      // or we can call OpenStream(StreamId, 0, int.MaxValue) to read all commits,
      // if no commits exist then it creates a new stream for us.
      val stream = store.openStream(StreamId, 0, Int.MaxValue)

      var event = new SomeDomainEvent(value = "Initial event.")

      stream.add(EventMessage(body = event))
      stream.commitChanges(java.util.UUID.randomUUID)

    }
  private def AppendToStream(): Unit =
    {
      var stream = store.openStream(StreamId, Int.MinValue, Int.MaxValue)

      var event = new SomeDomainEvent(value = "Second event.");

      stream.add(EventMessage(body = event))
      stream.commitChanges(java.util.UUID.randomUUID)

    }
  private def TakeSnapshot(): Unit =
    {

      var memento = AggregateMemento(value = "snapshot")
      store.advanced.addSnapshot(new Snapshot(StreamId, 2, memento))

    }
  private def LoadFromSnapshotForwardAndAppend() =
    {
      var latestSnapshot = store.advanced.getSnapshot(StreamId, Int.MaxValue).get
      var stream = store.openStream(latestSnapshot, Int.MaxValue)
      var event = new SomeDomainEvent(value = "Third event (first one after a snapshot).")

      stream.add(EventMessage(body = event))
      stream.commitChanges(java.util.UUID.randomUUID)
    }

}