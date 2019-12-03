package lila.insight

import reactivemongo.api.bson._
import scalaz.NonEmptyList

import lila.db.dsl._
import lila.rating.BSONHandlers.perfTypeIdHandler
import lila.rating.PerfType

private final class Storage(val coll: Coll) {

  import Storage._
  import BSONHandlers._
  import Entry.{ BSONFields => F }

  def fetchFirst(userId: String): Fu[Option[Entry]] =
    coll.find(selectUserId(userId)).sort(sortChronological).uno[Entry]

  def fetchLast(userId: String): Fu[Option[Entry]] =
    coll.find(selectUserId(userId)).sort(sortAntiChronological).uno[Entry]

  def count(userId: String): Fu[Int] =
    coll.countSel(selectUserId(userId))

  def insert(p: Entry) = coll.insert(p).void

  def bulkInsert(ps: Seq[Entry]) = coll.insert.many(
    ps.flatMap(BSONHandlers.EntryBSONHandler.writeOpt)
  )

  def update(p: Entry) = coll.update(selectId(p.id), p, upsert = true).void

  def remove(p: Entry) = coll.remove(selectId(p.id)).void

  def removeAll(userId: String) = coll.remove(selectUserId(userId)).void

  def find(id: String) = coll.find(selectId(id)).uno[Entry]

  def ecos(userId: String): Fu[Set[String]] =
    coll.distinctEasy[String, Set](F.eco, selectUserId(userId))

  def nbByPerf(userId: String): Fu[Map[PerfType, Int]] = coll.aggregateList(
    maxDocs = 50
  ) { framework =>
    import framework._
    Match(BSONDocument(F.userId -> userId)) -> List(
      GroupField(F.perf)("nb" -> SumValue(1))
    )
  }.map {
    _.flatMap { doc =>
      for {
        perfType <- doc.getAsOpt[PerfType]("_id")
        nb <- doc.int("nb")
      } yield perfType -> nb
    }.toMap
  }
}

private object Storage {

  import Entry.{ BSONFields => F }

  def selectId(id: String) = BSONDocument(F.id -> id)
  def selectUserId(id: String) = BSONDocument(F.userId -> id)
  val sortChronological = BSONDocument(F.date -> 1)
  val sortAntiChronological = BSONDocument(F.date -> -1)

  def combineDocs(docs: List[BSONDocument]) = docs.foldLeft(BSONDocument()) {
    case (acc, doc) => acc ++ doc
  }
}
