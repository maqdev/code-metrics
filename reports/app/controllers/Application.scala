package controllers

import play.api.mvc._
import anorm._
import play.api.db.DB
import play.api.Play.current
import java.util.Date
import collection.mutable
import anorm.SqlRow
import scala.Some
import java.text.SimpleDateFormat


case class Loc(newLoc: Int, changedLoc: Int) {
  def + (l: Loc) = {
    Loc(newLoc + l.newLoc, changedLoc + l.changedLoc)
  }
}
case class TotalResultRow(dt: String, loc: Loc, authors: Set[String], fileTypes: Set[String])
case class AuthorResultRow(dt: String, loc: Loc, fileTypes: Map[String, Loc], projects: Map[String, Loc])
case class CommitResultRow(projectName: String, hash: String, loc: Loc)

object Helper {

  def aggregate[X,Y](seq: Seq[X], f : (X, Option[Y]) => (Option[Y], Option[Y])) : Seq[Y] ={

    val l = new mutable.MutableList[Y]()

    var prev : Option[Y] = None

    seq.foreach(r => {
      val (a,b) = f(r, prev)
      prev = a

      if (b.isDefined) {
        if (prev.isDefined) {
          l += prev.get
        }
        prev = b
      }
    })

    if (prev.isDefined) {
      l += prev.get
    }

    l
  }
}

object Application extends Controller {

  val formatter = new SimpleDateFormat("yyyy-MM-dd")


  def index = Action {
    val query =
      """
        |select a.*, aa.email
        |from author a
        |join author_alias aa on (a.author_id = aa.author_id)
        |where exists (select author_id from commt c where c.author_id = a.author_id)
        |order by a.name
      """.stripMargin

    DB.withConnection { implicit c =>

      val q = SQL(query)
      val l = Helper.aggregate(q(), (row: SqlRow, a: Option[(Int, String, Seq[String])]) => {
        val authorId = row[Int]("author_id")
        val authorName = row[String]("name")
        val email = row[String]("email")

        if (a.isEmpty || a.get._1 != authorId) {
          (a, Some((authorId, authorName, Seq(email))))
        } else {
          (Some((authorId, authorName, a.get._3 ++ Seq(email))), None)
        }
      })

      Ok(views.html.index(l))
    }

  }

  def authors(authorId: Int, period: String) = Action {
    val query =
      """
        |select date_trunc({period}, c.dt) period, mt.name mt, sum(m.value) mt_val, ft.name ft, p.name project_name
        |from commt c
        |join metric m on (c.commt_id = m.commt_id)
        |join metric_type mt on (m.metric_type_id = mt.metric_type_id)
        |join project p on (p.project_id = c.project_id)
        |join file f on (m.file_id = f.file_id)
        |join file_type ft on (f.file_type_id = ft.file_type_id)
        |left join file_category fc on (fc.file_category_id = f.file_category_id)
        |where (mt.name in ('!= loc', '+ loc'))
        |and c.author_id = {author_id}
        |and not c.exclude
        |and not ft.exclude
        |and not coalesce(fc.exclude, false)
        |group by period, ft.name, project_name, mt
        |order by period
      """.stripMargin

    DB.withConnection { implicit c =>
      val authorName = SQL("select name from author where author_id = {author_id}").on("author_id" -> authorId).apply().head[String]("name")

      val q = SQL(query).on("period" -> period, "author_id" -> authorId)

      val l = Helper.aggregate(q(), (row: SqlRow, a: Option[AuthorResultRow]) => {

        val dt = formatter.format(row[Date]("period"))
        val ft = row[String]("ft")
        val projectName = row[String]("project_name")
        val loc = convLoc(row)

        if (a.isEmpty || a.get.dt != dt) {
          (a, Some(AuthorResultRow(
                dt,
                loc,
                Map(ft -> loc),
                Map(projectName -> loc))
            )
          )
        } else {
          val prev = a.get

          val lf = if (prev.fileTypes.contains(ft)) prev.fileTypes.get(ft).get + loc else loc
          val lp = if (prev.projects.contains(projectName)) prev.projects.get(projectName).get + loc else loc

          (Some(AuthorResultRow(
                dt,
                loc + prev.loc,
                prev.fileTypes ++ Map(ft -> lf),
                prev.projects ++ Map(projectName -> lp))),
            None
          )
        }
      })

      Ok(views.html.author(authorId, authorName, l, period))
    }
  }

  def totals(period: String) = Action {

    val query =
      """
        |select date_trunc({period}, c.dt) period, mt.name mt, sum(m.value) mt_val, ft.name ft, a.name author
        |from commt c
        |join author a on (a.author_id = c.author_id)
        |join metric m on (c.commt_id = m.commt_id)
        |join metric_type mt on (m.metric_type_id = mt.metric_type_id)
        |join project p on (p.project_id = c.project_id)
        |join file f on (m.file_id = f.file_id)
        |join file_type ft on (f.file_type_id = ft.file_type_id)
        |left join file_category fc on (fc.file_category_id = f.file_category_id)
        |where (mt.name in ('!= loc', '+ loc'))
        |and not c.exclude
        |and not ft.exclude
        |and not coalesce(fc.exclude, false)
        |group by period, ft.name, a.name, mt
        |order by period
      """.stripMargin

    DB.withConnection { implicit c =>

      val q = SQL(query).on("period" -> period)

      val l = Helper.aggregate(q(), (row: SqlRow, a: Option[TotalResultRow]) => {

        val dt = formatter.format(row[Date]("period"))
        val ft = row[String]("ft")
        val author = row[String]("author")
        val loc = convLoc(row)

        if (a.isEmpty || a.get.dt != dt) {
          (a, Some(TotalResultRow(dt, loc, Set(author), Set(ft))))
        } else {
          val prev = a.get
          (Some(TotalResultRow(dt, loc + prev.loc, prev.authors+author, prev.fileTypes+ft)), None)
        }
      })

      Ok(views.html.total(l))
    }
  }

  def commits(authorId: Int, period: String, dtString: String) = Action {

    val query =
      """
        |select p.name project_name, c.hash, mt.name as mt, sum(m.value) mt_val
        |from commt c
        |join metric m on (c.commt_id = m.commt_id)
        |join metric_type mt on (m.metric_type_id = mt.metric_type_id)
        |join project p on (p.project_id = c.project_id)
        |join file f on (m.file_id = f.file_id)
        |join file_type ft on (f.file_type_id = ft.file_type_id)
        |left join file_category fc on (fc.file_category_id = f.file_category_id)
        |where (mt.name in ('!= loc', '+ loc'))
        |and not c.exclude
        |and not ft.exclude
        |and not coalesce(fc.exclude, false)
        |and c.author_id = {author_id}
        |and date_trunc({period}, c.dt) = {dt}
        |group by project_name, c.hash, mt
        |order by project_name
      """.stripMargin

    DB.withConnection { implicit c =>

      val authorName = SQL("select name from author where author_id = {author_id}").on("author_id" -> authorId).apply().head[String]("name")

      val dt = formatter.parse(dtString)

      val q = SQL(query).on("period" -> period, "dt" -> dt, "author_id" -> authorId)

      val l = Helper.aggregate(q(), (row: SqlRow, a: Option[CommitResultRow]) => {
        val projectName = row[String]("project_name")
        val hash = row[String]("hash")
        val loc = convLoc(row)

        if (a.isEmpty || a.get.hash != hash) {
          (a, Some(CommitResultRow(projectName, hash, loc)))
        } else {
          val prev = a.get
          (Some(CommitResultRow(projectName, hash, prev.loc + loc)), None)
        }
      })

      Ok(views.html.commits(authorName, dtString, period, l))
    }
  }

  private def convLoc(row: SqlRow) = {
    val mt = row[String]("mt")
    val mtval = row[Long]("mt_val").toInt

    val loc = if (mt == "!= loc") Loc(0, mtval)
    else if (mt == "+ loc") Loc(mtval, 0)
    else Loc(0,0)

    loc
  }
}