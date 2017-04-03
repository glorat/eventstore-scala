package eventstore

import org.scalatest.FlatSpec
import org.scalatest.junit.AssertionsForJUnit

class when_building_a_stream extends FlatSpec with on_the_event_stream  {
  val MinRevision = 2;
  val MaxRevision = 7;
  val EachCommitHas: Int = 2; // Events

  "this" should "succeed" in {
    assert(true == true)
  }

  val Committed: IndexedSeq[Commit] =
    IndexedSeq(
      BuildCommitStub(2, 1, EachCommitHas), // 1-2
      BuildCommitStub(4, 2, EachCommitHas), // 3-4
      BuildCommitStub(6, 3, EachCommitHas), // 5-6
      BuildCommitStub(8, 3, EachCommitHas) // 7-8
      )

  // persistence.setup(x => x.GetFrom(streamId, MinRevision, MaxRevision)).Returns(Committed);
  /*
		Because of = () =>
			stream = new OptimisticEventStream(streamId, persistence.Object, MinRevision, MaxRevision);

		It should_have_the_correct_stream_identifier = () =>
			stream.StreamId.ShouldEqual(streamId);

		It should_have_the_correct_head_stream_revision = () =>
			stream.StreamRevision.ShouldEqual(MaxRevision);

		It should_have_the_correct_head_commit_sequence = () =>
			stream.CommitSequence.ShouldEqual(Committed.Last().CommitSequence);

		It should_not_include_events_below_the_minimum_revision_indicated = () =>
			stream.CommittedEvents.First().ShouldEqual(Committed.First().Events.Last());

		It should_not_include_events_above_the_maximum_revision_indicated = () =>
			stream.CommittedEvents.Last().ShouldEqual(Committed.Last().Events.First());

		It should_have_all_of_the_committed_events_up_to_the_stream_revision_specified = () =>
			stream.CommittedEvents.Count.ShouldEqual(MaxRevision - MinRevision + 1);

		It should_contain_the_headers_from_the_underlying_commits = () =>
			stream.CommittedHeaders.Count.ShouldEqual(2);
	}
object on_the_event_stream {
*/
}

trait on_the_event_stream {
  case class DummyEvent() extends CQRS.DomainEvent

  protected val DefaultStreamRevision = 1
  protected val DefaultCommitSequence = 1
  val streamId: Guid = java.util.UUID.randomUUID
  var stream: OptimisticEventStream = null
  var persistence: ICommitEvents = null // TODO: Mock
  def context: Unit = {
    //persistence = new Mock<ICommitEvents>();
    persistence = new InMemoryPersistenceEngine()
    stream = new OptimisticEventStream(streamId, persistence);
    // SystemTime.Resolver = () => new DateTime(2012, 1, 1, 13, 0, 0);
  }

  protected def BuildCommitStub(revision: Int, sequence: Int, eventCount: Int): Commit = {
    var events: List[EventMessage] = List[EventMessage]()
    // This line is not good scala
    (1 until eventCount).foreach(_ => events = events :+ new EventMessage(Map(), DummyEvent()))

    val newGuid = java.util.UUID.randomUUID()
    new Commit(streamId, revision, newGuid, sequence, EventDateTime.now, null, events);
  }
}
