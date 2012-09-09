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

import eu.inn.metrics._
import eu.inn.metrics.shell.GitVersion
import java.sql.{DriverManager, Timestamp}
import util.matching.Regex
import eu.inn.metrics.diff.DiffHandlerType

import norma._
import java.io.Closeable

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

class DatabaseOutputHandler(url: String, driver: String)
  extends Closeable with OutputHandler {

  implicit val connection = {
    val cls = Class.forName(driver)
    DriverManager.getConnection(url)
  }

  def close() {
    connection.close()
  }

  var progress = -1
  var currentProjectId : Option[Int] = None
  lazy val authorAliasMap = {
    val m = scala.collection.mutable.Map[String, Int]()

    SQL(
      """
        |select author_id, email from author_alias
      """.stripMargin
    ).apply().foreach{ row =>
      m += (row[String]("email") -> row[Int]("author_id"))
    }
    m
  }

  private def getAuthorId(name: String, email: String) : Int = {
    val r = authorAliasMap.get(email)
    if (r.isDefined)
      return r.get

    val authorId = SQL("insert into author(name) values({name})").on("name" -> name).executeInsert().get.toInt
    SQL("insert into author_alias(author_id, email) values({author_id}, {email})").on("author_id" -> authorId, "email" -> email).execute()
    authorAliasMap += (email->authorId)
    authorId
  }

  lazy val fileTypeMap = {
    val m = scala.collection.mutable.Map[String, Int]()
    SQL("select file_type_id, name from file_type").apply().foreach {
      row => m += (row[String]("name") -> row[Int]("file_type_id"))
    }
    m
  }

  private def getFileTypeId(fileTypeName: String) : Int = {
    val r = fileTypeMap.get(fileTypeName)
    if (r.isDefined)
      return r.get

    val id = SQL("insert into file_type(name) values({name})").on("name" -> fileTypeName).executeInsert().get.toInt
    fileTypeMap += (fileTypeName -> id)
    return id
  }

  lazy val metricTypeMap = {
    val m = scala.collection.mutable.Map[String, Int]()

    SQL("select metric_type_id, name from metric_type").apply().foreach { r =>
      m += (r[String]("name") -> r[Int]("metric_type_id"))
    }
    m
  }

  private def getMetricTypeId(metricTypeName: String) : Int = {
    val r = metricTypeMap.get(metricTypeName)
    if (r.isDefined)
      return r.get

    val id = SQL("insert into metric_type(name) values({name}").on("name"->metricTypeName).executeInsert().get.toInt
    metricTypeMap += (metricTypeName -> id)
    return id
  }

  lazy val fileMap = {
    val m = scala.collection.mutable.Map[String, (Int, Option[Int])]()

    SQL("select file_id, file_category_id, path from file where project_id={project_id}").on("project_id" -> currentProjectId).apply().foreach { r =>
      m += (r[String]("path") -> (r[Int]("file_id"), r[Option[Int]]("file_category_id")))
    }
    m
  }

  def fixFileCategory(path: String, r: (Int, Option[Int]), fileCategoryId: Option[Int], fileCategoryName: Option[String]){
    if (r._2 != fileCategoryId) {
      println("Updating category for file: " + path + " to " + fileCategoryName)
      SQL("update file set file_category_id={file_category_id} where file_id={file_id}").on(
        "file_category_id"->fileCategoryId,
        "file_id"->r._1
      ).executeUpdate()
    }
  }

  private def getFileId(fileTypeId: Int, fileCategoryId: Option[Int], fileCategoryName: Option[String], path: String) : Int = {
    val r = fileMap.get(path)
    if (r.isDefined) {
      fixFileCategory(path, r.get, fileCategoryId, fileCategoryName)
      return r.get._1
    }

    val id = SQL("insert into file(project_id, file_type_id, file_category_id, path) values({project_id},{file_type_id},{file_category_id},{path})").on(
      "project_id" -> currentProjectId.get,
      "file_type_id" -> fileTypeId,
      "file_category_id" -> fileCategoryId,
      "path" -> path
    ).executeInsert().get.toInt

    fileMap += (path -> (id, fileCategoryId))
    return id
  }

  //def lastSequenceId = SQL("select LASTVAL as lv").apply().head[Long]("lv")

  def repositaryUrl(url: String) {
    println("Found repositary: " + url)

    val q = SQL("select project_id from project where path={path}").on("path" -> url).apply()
    currentProjectId = if (q.isEmpty) None else q.head[Option[Int]]("project_id")

    if (currentProjectId.isEmpty) {
      currentProjectId = Some(SQL("insert into project(name,path) values({name}, {path})").on(
        "path"->url,
        "name"->url
      ).executeInsert().get.toInt)

      println("id of project is: " + currentProjectId.get)
    }
  }

  var currentCommitId : Option[Long] = None
  def commit(c: RepositaryCommit) : Boolean = {
    println("-------------------------------")
    println("" + c.dt + " " + c.commitType + " " + c.name + " " + c.email + " " + c.hash)

    val authorId = getAuthorId(c.name, c.email)

    val q = SQL("select commt_id from commt where hash={hash}").on("hash"->c.hash).apply()
    val cmt = if (q.isEmpty) None else q.head[Option[Long]]("commt_id")

    if (cmt.isDefined) {
      println("Commit already processed")
      return false
    }
    else {
      currentCommitId = SQL("insert into commt(author_id, hash, project_id, commt_type, dt) values ({author_id}, {hash}, {project_id}, {commt_type}, {dt})").on(
        "author_id"->authorId,
        "hash"->c.hash,
        "project_id"->currentProjectId.get,
        "commt_type"->c.commitType.id,
        "dt"->new Timestamp(c.dt.getMillis())
      ).executeInsert()
      return true
    }
  }

  def gitVersion(version: GitVersion) {
    println(version)
  }

  def fileMetrics(metrics: FileMetrics) {

    println("Updating db for: " + metrics.fileName)
    val fileTypeId = getFileTypeId(metrics.language)

    val (fileCategoryId : Option[Int], fileCategoryName: Option[String]) =
      if (metrics.category.isDefined) {
        val fcid = getFileCategoryId(metrics.category.get)
        if (fcid.isEmpty)
          println("Category " + metrics.category.get + " is not found in database, leaving it empty")
        (fcid, metrics.category)
      }
    else
      (None, None)

    val fileId = getFileId(fileTypeId, fileCategoryId, fileCategoryName, metrics.fileName)
    for ((key, value) <- metrics.metrics) {
      val metricTypeId = getMetricTypeId(key.toString)

      SQL("insert into metric(commt_id, file_id, metric_type_id, value) values({commt_id}, {file_id}, {metric_type_id}, {value})").on(
        "commt_id"->currentCommitId.get,
        "file_id"->fileId,
        "metric_type_id"->metricTypeId,
        "value"->value
      ).execute()
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

    SQL(
      """
        |select fc.file_category_id, fc.name, fc.regex, fc.diff_handler, ft.name ft_name, fc.priority
        |from file_category fc
        |left join file_type ft on (fc.file_type_id = ft.file_type_id)
        |where coalesce(fc.project_id, {project_id}) = {project_id}
        |order by fc.priority
      """.stripMargin
    ).on(
      "project_id"->currentProjectId.get
    ).apply().foreach { r =>
      val fileCategoryId = r[Int]("file_category_id")
      val name = r[String]("name")
      val regex = r[String]("regex")
      val diffHandler = r[Option[String]]("diff_handler")
      val fileTypeName = r[Option[String]]("ft_name")
      val priority = r[Int]("priority")

      ftl.append(FileCategoryRegex(name, new Regex(regex), priority,
        diffHandler match {
          case Some(x) => Some(DiffHandlerType.withName(x))
          case None => None
        },
        fileTypeName //how is this will be handled
      ))
      fileCategoryMap += (name -> fileCategoryId)
    }

    println("Updating categories for the files...")
    for ((key,value) <- fileMap) {
      val ft = ftl.getFileType(key)
      if (ft.category.isDefined) {
        val fileCategoryId = getFileCategoryId(ft.category.get)
        fixFileCategory(key, value, fileCategoryId, ft.category)
      }
    }
  }
}
