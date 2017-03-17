package org.fubar

import java.nio.file._
import java.util.UUID
import org.scalatest._
import org.scalatest.fixture.AsyncWordSpec
import scala.util._
import scala.concurrent._
import slick.jdbc.JdbcBackend.Database
import slick.util.AsyncExecutor
import com.typesafe.scalalogging.slf4j._
import scala.concurrent.ExecutionContext.Implicits.global


class SqliteSlickSpec extends AsyncWordSpec with Matchers with StrictLogging {
  type FixtureParam = TestData

  def withFixture(test: OneArgAsyncTest) = {
    val path = Paths.get(".", "testData", UUID.randomUUID.toString)

    val fixture = new TestData(path)
    complete {
      super.withFixture(test.toNoArgAsyncTest(fixture))
    } lastly {
      fixture.close()
      path.toFile.delete
      ()
    }
  }


  "TestData" should {
    "add several records" in { fixture =>
      val data = List.fill(900)((Random.nextString(10), Random.nextInt(), Random.nextString(10)))

      ( for {
        _  <- fixture.create
        _  <- Future.sequence(data.map((fixture.addRecord _).tupled))
        rs <- fixture.getAll()
      } yield rs
      ) map { rs =>
        rs map (r => (r.c1, r.c2, r.c3)) shouldEqual data
      }
    }

    "add and modify several records" in { fixture =>
      val data = List.fill(500)((Random.nextString(10), Random.nextInt(), Random.nextString(10)))
      val updates = data.map { case (c1, c2, c3) => (c1, c3.take(5)) }

      ( for {
        _  <- fixture.create
        _  <- Future.sequence(data.map((fixture.addRecord _).tupled))
        _  <- Future.sequence(updates.map((fixture.updateC3 _).tupled))
        rs <- fixture.getAll()
      } yield rs
      ) map { rs =>
        rs map (r => (r.c1, r.c2, r.c3)) shouldEqual (data map { d => (d._1, d._2, d._3.take(5)) })
      }
    }

    "add and modify records in a for comprehension transaction" in { fixture =>
      val data = List.fill(500)((Random.nextString(10), Random.nextInt(), Random.nextString(10)))
      val updates = data.map { case (c1, c2, c3) => (c1, c2*2, c3.take(5)) }

      ( for {
        _  <- fixture.create
        _  <- Future.sequence(data.map((fixture.addRecord _).tupled))
        _  <- Future.sequence(updates.map((fixture.updateC2C3 _).tupled))
        rs <- fixture.getAll()
      } yield rs
      ) map { rs =>
        rs map (r => (r.c1, r.c2, r.c3)) shouldEqual updates
      }
    }

    "add and modify records in a transaction" in { fixture =>
      val data = List.fill(500)((Random.nextString(10), Random.nextInt(), Random.nextString(10)))
      val updates = data.map { case (c1, c2, c3) => (c1, c2*2, c3.take(5)) }

      ( for {
        _  <- fixture.create
        _  <- Future.sequence(data.map((fixture.addRecord _).tupled))
        _  <- Future.sequence(updates.map((fixture.updateC2C3X _).tupled))
        rs <- fixture.getAll()
      } yield rs
      ) map { rs =>
        rs map (r => (r.c1, r.c2, r.c3)) shouldEqual updates
      }
    }
  }
}

class TestData(dbFile: Path) {
  val driver = slick.driver.SQLiteDriver
  val url = "jdbc:sqlite:" + dbFile.toAbsolutePath.toString
  val db = Database.forURL(url, driver = "org.sqlite.JDBC", executor = AsyncExecutor(s"$url-worker", 1, 1000))

  import TestData._
  import driver.api._

  def create() =
    db.run(testTable.schema.create)

  def addRecord(c1: String, c2: Int, c3: String): Future[Int] =
    db.run(testTable += Record(0, c1, c2, c3))

  def getRecord(id: Int): Future[Option[Record]] =
    db.run(testTable.filter(_.id === id).result.headOption)

  def getRecord(c1: String): Future[Option[Record]] =
    db.run(testTable.filter(_.c1 === c1).result.headOption)

  def getAll(): Future[Seq[Record]] =
    db.run(testTable.result)

  def updateC1(id: Int, c1: String): Future[Int] =
    db.run(testTable.filter(_.id === id).map(_.c1).update(c1))

  def updateC3(c1: String, c3: String): Future[Int] =
    db.run(testTable.filter(_.c1 === c1).map(_.c3).update(c3))

  def updateC2C3(c1: String, c2: Int, c3: String): Future[Int] =
    db.run(
      (for {
        _ <- testTable.filter(_.c1 === c1).map(_.c2).update(c2)
        n <- testTable.filter(_.c1 === c1).map(_.c3).update(c3)
      } yield n).transactionally
    )

  def updateC2C3X(c1: String, c2: Int, c3: String) =
    db.run(
      DBIO.sequence(Seq(
        testTable.filter(_.c1 === c1).map(_.c2).update(c2),
        testTable.filter(_.c1 === c1).map(_.c3).update(c3)
      )).transactionally
    )



  class TestTable(tag: slick.lifted.Tag) extends Table[Record](tag, "TestData") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def c1 = column[String]("c1")
    def c2 = column[Int]("c2")
    def c3 = column[String]("c3")

    def * = (id, c1, c2, c3) <> (Record.tupled, Record.unapply)

    def idxC1 = index("idx_testdata_c1", (c1))
  }

  val testTable = TableQuery[TestTable]

  def close() = db.close()
}


object TestData {
  case class Record(id: Int, c1: String, c2: Int, c3: String)
}
