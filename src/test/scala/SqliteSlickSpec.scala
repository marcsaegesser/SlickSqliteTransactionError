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
    "add and modify records in a for comprehension transaction" in { fixture =>
      val data = List.fill(50)((Random.nextString(10), Random.nextInt(), Random.nextString(10)))
      val updates = data.map { case (c1, c2, c3) => (c1, c2*2, c3.take(5)) }

      ( for {
        _  <- fixture.create
        _  <- Future.sequence(data.map((fixture.addRecord _).tupled))
        _  <- Future.sequence(updates.map((fixture.updateC2C3a _).tupled))
        rs <- fixture.getAll()
      } yield rs
      ) map { rs =>
        rs shouldEqual updates
      }
    }

    "add and modify records in a transaction" in { fixture =>
      val data = List.fill(50)((Random.nextString(10), Random.nextInt(), Random.nextString(10)))
      val updates = data.map { case (c1, c2, c3) => (c1, c2*2, c3.take(5)) }

      ( for {
        _  <- fixture.create
        _  <- Future.sequence(data.map((fixture.addRecord _).tupled))
        _  <- Future.sequence(updates.map((fixture.updateC2C3b _).tupled))
        rs <- fixture.getAll()
      } yield rs
      ) map { rs =>
        rs shouldEqual updates
      }
    }
  }
}

class TestData(dbFile: Path) {
  val driver = slick.driver.SQLiteDriver
  val url = "jdbc:sqlite:" + dbFile.toAbsolutePath.toString
  val db = Database.forURL(url, driver = "org.sqlite.JDBC", executor = AsyncExecutor(s"$url-worker", 1, 1000))

  import driver.api._

  def create() =
    db.run(testTable.schema.create)

  def addRecord(c1: String, c2: Int, c3: String): Future[Int] =
    db.run(testTable += ((c1, c2, c3)))

  def getRecord(c1: String): Future[Option[(String, Int, String)]] =
    db.run(testTable.filter(_.c1 === c1).result.headOption)

  def getAll(): Future[Seq[(String, Int, String)]] =
    db.run(testTable.result)

  def updateC3(c1: String, c3: String): Future[Int] =
    db.run(testTable.filter(_.c1 === c1).map(_.c3).update(c3))

  def updateC2C3a(c1: String, c2: Int, c3: String): Future[Int] =
    db.run(
      (for {
        _ <- testTable.filter(_.c1 === c1).map(_.c2).update(c2)
        n <- testTable.filter(_.c1 === c1).map(_.c3).update(c3)
      } yield n).transactionally
    )

  def updateC2C3b(c1: String, c2: Int, c3: String) =
    db.run(
      DBIO.sequence(Seq(
        testTable.filter(_.c1 === c1).map(_.c2).update(c2),
        testTable.filter(_.c1 === c1).map(_.c3).update(c3)
      )).transactionally
    )



  class TestTable(tag: slick.lifted.Tag) extends Table[(String, Int, String)](tag, "TestData") {
    def c1 = column[String]("c1", O.PrimaryKey)
    def c2 = column[Int]("c2")
    def c3 = column[String]("c3")

    def * = (c1, c2, c3)
  }

  val testTable = TableQuery[TestTable]

  def close() = db.close()
}
