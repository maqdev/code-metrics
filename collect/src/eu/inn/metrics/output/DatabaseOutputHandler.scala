/*
 *	Copyright (c) 2012 Innova Co SARL. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *      * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Carbon Foundation X nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL Carbon Foundation X BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *
 */

/*
 * Author(s):
 *  Magomed Abdurakhmanov (maga@inn.eu)
 */

package eu.inn.metrics.output

import eu.inn.metrics.{FileCategoryRegex, FileTypeList, RepositaryCommit, FileMetrics}
import eu.inn.metrics.shell.GitVersion
import java.sql.Timestamp
import util.matching.Regex
import eu.inn.metrics.diff.DiffHandlerType

// Import the session management, including the implicit threadLocalSession
import org.scalaquery.session._
//import org.scalaquery.session.Database.threadLocalSession

// Import the query language
import org.scalaquery.ql._

// Import the standard SQL types
import org.scalaquery.ql.TypeMapper._

// Use H2Driver which implements ExtendedProfile and thus requires ExtendedTables
import org.scalaquery.ql.extended.H2Driver.Implicit._
import org.scalaquery.ql.extended.{ExtendedTable => Table}

class DatabaseOutputHandler(url: String, driver: String) extends OutputHandler {

  val db = Database.forURL(url, driver)

  val Project = new Table[(Int, String, String)]("project") {
    def projectId  = column[Int]("project_id", O PrimaryKey, O AutoInc)
    def name = column[String]("name")
    def path = column[String]("path")
    def * = projectId ~ name ~ path
  }

  val Author = new Table[(Int, String)]("author") {
    def authorId  = column[Int]("author_id", O PrimaryKey, O AutoInc)
    def name = column[String]("name")
    def * = authorId ~ name
  }

  val AuthorAlias = new Table[(Int, String)]("author_alias") {
    def authorId  = column[Int]("author_id")
    def email = column[String]("email", O PrimaryKey)
    def * = authorId ~ email
  }

  val Commit = new Table[(Long, String, Int, Int, Int, Timestamp)]("commt") {
    def commitId  = column[Long]("commt_id", O PrimaryKey, O AutoInc)
    def hash = column[String]("hash")
    def projectId = column[Int]("project_id")
    def authorId = column[Int]("author_id")
    def commitType = column[Int]("commt_type")
    def dt = column[Timestamp]("dt")
    def * = commitId ~ hash ~ projectId ~ authorId ~ commitType ~ dt
  }

  val FileType = new Table[(Int, String)]("file_type") {
    def fileTypeId  = column[Int]("file_type_id", O PrimaryKey, O AutoInc)
    def name = column[String]("name")
    def * = fileTypeId ~ name
  }

  val FileCategory = new Table[(Int, Int, String, String, Int, Option[Int], Option[String])]("file_category") {
    def fileCategoryId  = column[Int]("file_category_id", O PrimaryKey, O AutoInc)
    def projectId = column[Int]("project_id")
    def name = column[String]("name")
    def regex = column[String]("regex")
    def priority = column[Int]("priority")
    def fileTypeId = column[Option[Int]]("file_type_id")
    def diffHandler = column[Option[String]]("diff_handler")
    def * = fileCategoryId ~ projectId ~ name ~ regex ~ priority ~ fileTypeId ~ diffHandler
  }

  val File = new Table[(Int, Int, Int, Option[Int], String)]("file") {
    def fileId  = column[Int]("file_id", O PrimaryKey, O AutoInc)
    def projectId = column[Int]("project_id")
    def fileTypeId = column[Int]("file_type_id")
    def fileCategoryId = column[Option[Int]]("file_category_id")
    def path = column[String]("path")
    def * = fileId ~ projectId ~ fileTypeId ~ fileCategoryId ~ path
  }

  val MetricType = new Table[(Int, String)]("metric_type") {
    def metricTypeId  = column[Int]("metric_type_id", O PrimaryKey, O AutoInc)
    def name = column[String]("name")
    def * = metricTypeId ~ name
  }

  val Metric = new Table[(Long, Int, Int, Int)]("metric") {
    def commitId  = column[Long]("commt_id")
    def metricTypeId = column[Int]("metric_type_id")
    def fileId = column[Int]("file_id")
    def value = column[Int]("value")
    def * = commitId ~ metricTypeId ~ fileId ~ value
  }

  var progress = -1
  var currentProjectId : Option[Int] = None
  var authorAliasMap = {
    val m = scala.collection.mutable.Map[String, Int]()
    db withSession { implicit session : Session =>
      val q = for {aa <- AuthorAlias} yield aa.*
      q.foreach {
        case (authorId, email) => m += (email -> authorId)
      }
    }
    m
  }
  private def getAuthorId(name: String, email: String) : Int = {
    val r = authorAliasMap.get(email)
    if (r.isDefined)
      return r.get

    db withSession { implicit session : Session =>
      val ins = (Author.name).insert(name)
      val authorId = lastSequenceId

      val insaa = (AuthorAlias.authorId ~ AuthorAlias.email).insert(authorId, email)
      authorAliasMap += (email -> authorId)
      return authorId
    }
  }

  var fileTypeMap = {
    val m = scala.collection.mutable.Map[String, Int]()
    db withSession { implicit session : Session =>
      val q = for {ft <- FileType} yield ft.*
      q.foreach {
        case (fileTypeId, name) => m += (name -> fileTypeId)
      }
    }
    m
  }
  private def getFileTypeId(fileTypeName: String) : Int = {
    val r = fileTypeMap.get(fileTypeName)
    if (r.isDefined)
      return r.get

    db withSession { implicit session : Session =>
      val ins = (FileType.name).insert(fileTypeName)
      val id = lastSequenceId
      fileTypeMap += (fileTypeName -> id)
      return id
    }
  }

  var metricTypeMap = {
    val m = scala.collection.mutable.Map[String, Int]()
    db withSession { implicit session : Session =>
      val q = for {ft <- MetricType} yield ft.*
      q.foreach {
        case (metricTypeId, name) => m += (name -> metricTypeId)
      }
    }
    m
  }
  private def getMetricTypeId(metricTypeName: String) : Int = {
    val r = metricTypeMap.get(metricTypeName)
    if (r.isDefined)
      return r.get

    db withSession { implicit session : Session =>
      val ins = (MetricType.name).insert(metricTypeName)
      val id = lastSequenceId
      metricTypeMap += (metricTypeName -> id)
      return id
    }
  }

  var fileMap = {
    val m = scala.collection.mutable.Map[String, (Int, Option[Int])]()
    db withSession { implicit session : Session =>
      val q = for {ft <- File} yield ft.path ~ ft.fileId ~ ft.fileCategoryId
      q.foreach {
        case (path, fileId, fileCategoryId) => m += (path -> (fileId, fileCategoryId))
      }
    }
    m
  }
  private def getFileId(fileTypeId: Int, fileCategoryId: Option[Int], path: String) : Int = {
    val r = fileMap.get(path)

    db withSession { implicit session : Session =>
      if (r.isDefined) {
        if (r.get._2 != fileCategoryId) {
          println("Updating category for file: " + path + " to " + fileCategoryId)
          val upd = File.where(_.fileId === r.get._1.bind).map(_.fileCategoryId).update(fileCategoryId)
        }
        return r.get._1
      }

      val ins = (File.projectId ~ File.fileTypeId ~ File.fileCategoryId ~ File.path).insert(
        currentProjectId.get, fileTypeId, fileCategoryId, path
      )
      val id = lastSequenceId
      fileMap += (path -> (id, fileCategoryId))
      return id
    }
  }

  def lastSequenceId(implicit session : Session) = Query(SimpleFunction.nullary[Int]("LASTVAL")).first()

  def repositaryUrl(url: String) {
    println("Found repositary: " + url)

    db withSession { implicit session : Session =>
      val qproj = for {p <- Project if p.path === url.bind} yield p.projectId
      currentProjectId = qproj.firstOption()

      if (currentProjectId.isEmpty) {
        val ins = (Project.name ~ Project.path).insert(url, url)
        currentProjectId = Some(lastSequenceId)
      }

      println("id of project is: " + currentProjectId.get)
    }
  }

  var currentCommitId : Option[Long] = None
  def commit(c: RepositaryCommit) : Boolean = {
    println("-------------------------------")
    println("" + c.dt + " " + c.commitType + " " + c.name + " " + c.email + " " + c.hash)

    val authorId = getAuthorId(c.name, c.email)

    db withSession { implicit session : Session =>
      val q = for { cm <- Commit if cm.hash === c.hash.bind } yield cm.commitId

      if (q.firstOption().isDefined) {
        println("Commit already processed")
        return false
      }
      else {
        val ins = (Commit.authorId ~ Commit.hash ~ Commit.projectId ~ Commit.commitType ~ Commit.dt).insert(
          authorId, c.hash, currentProjectId.get, c.commitType.id, new Timestamp(c.dt.getMillis())
        )
        currentCommitId = Some(lastSequenceId)
        return true
      }
    }
  }

  def gitVersion(version: GitVersion) {
    println(version)
  }

  def fileMetrics(metrics: FileMetrics) {

    println("Updating db for: " + metrics.fileName)
    val fileTypeId = getFileTypeId(metrics.language)
    val fileCategoryId = getFileCategoryId(metrics.category)
    if (fileCategoryId.isEmpty) println("Category " + metrics.category + " is not found in database, leaving it empty")
    val fileId = getFileId(fileTypeId, fileCategoryId, metrics.fileName)
    for ((key, value) <- metrics.metrics) {
      val metricTypeId = getMetricTypeId(key.toString)

      db withSession { implicit session : Session =>
        val ins = (Metric.commitId ~ Metric.fileId ~ Metric.metricTypeId ~ Metric.value).insert(
          currentCommitId.get, fileId, metricTypeId, value
        )
      }
    }
    println()
  }

  def processingFile(fileName: String, oldFileName: String, newFileName: String) {
    println("Processing file " + fileName + "...")
  }

  def setProgress(current: Int, maximum: Int) {
    val percent = (current * 100) / maximum
    if (percent != progress) {
      println("Completed " + percent + "%")
    }
    progress = percent
  }

  private var fileCategoryMap = scala.collection.mutable.Map[String, Int]()
  def getFileCategoryId(name: String) = fileCategoryMap.get(name)

  def fetchTypeList(ftl: FileTypeList) {
    db withSession { implicit session : Session =>

      val q = for {
        Join (fc, ft) <- FileCategory leftJoin FileType on (_.fileTypeId === _.fileTypeId)// if fc.projectId === currentProjectId.bind
        _ <- Query orderBy fc.priority
      } yield fc.fileCategoryId ~ fc.name ~ fc.regex ~ fc.diffHandler ~ ft.name ~ fc.priority

      q.foreach {
        case(fileCategoryId,name,regex,diffHandler,fileTypeName,priority)=>
          ftl.append(FileCategoryRegex(name, new Regex(regex), priority,
            diffHandler match {
              case Some(x) => Some(DiffHandlerType.withName(x))
              case None => None
            },
            if (fileTypeName.isEmpty) None else Some(fileTypeName)) //how is this will be handled
          )
          fileCategoryMap += (name -> fileCategoryId)
      }
    }
  }
}
