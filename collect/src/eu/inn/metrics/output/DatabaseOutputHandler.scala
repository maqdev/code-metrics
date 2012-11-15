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
import fingerprint.{TextFingerprintArrays, TextFingerprintCalculator, TextFingerprint, FingerprintPart}
import java.util.Date
import util.matching.Regex
import eu.inn.metrics.diff.DiffHandlerType

import norma._
import java.io.Closeable
import java.sql.{Timestamp, DriverManager}
import collection.mutable
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap
import scala.util.control.Breaks._
import net.spy.memcached.MemcachedClient
import java.net.InetSocketAddress

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

class DatabaseOutputHandler(url: String, driver: String, force: Boolean)
  extends Closeable with OutputHandler {

  implicit val connection = {
    val cls = Class.forName(driver)
    DriverManager.getConnection(url)
  }

  def close() {
    connection.close()
  }

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

    val id = SQL("insert into metric_type(name) values({name})").on("name"->metricTypeName).executeInsert().get.toInt
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
  var currentCommitDt : Option[Date] = None

  def commitStarted(c: RepositaryCommit) : Boolean = {
    println("-------------------------------")
    println("" + c.dt + " " + c.commitType + " " + c.name + " " + c.email + " " + c.hash)

    val authorId = getAuthorId(c.name, c.email)

    val q = SQL("select commt_id, processed from commt where hash={hash}").on("hash"->c.hash).apply()
    val (cmt,processed) = if (q.isEmpty) (None,false) else (q.head[Option[Long]]("commt_id"),q.head[Boolean]("processed"))

    if (cmt.isDefined && !force && processed) {
      println("Commit already processed")
      return false
    }
    else {

      if (cmt.isDefined) {
        if (!processed) {
          println("Removing incomplete commit #" + cmt.get)
        }

        // remove existing metrics
        println("Removing existing metrics for commit " + c.hash + " #" + cmt.get + "...")
        SQL("delete from metric where commt_id={commt_id}").on("commt_id"->cmt.get).execute

        println("Removing existing fingerprints for commit " + c.hash + " #" + cmt.get + "...")
        SQL("delete from fingerprint where file_version_id in (select file_version_id from file_version where commt_id = {commt_id})").on("commt_id"->cmt.get).execute

        println("Unlinking existing similar file versions for commit " + c.hash + " #" + cmt.get + "...")
        SQL("update file_version set similar_file_version_id = null, similarity=0 where similar_file_version_id in (select file_version_id from file_version where commt_id = {commt_id})").on("commt_id"->cmt.get).execute

        println("Removing existing file versions for commit " + c.hash + " #" + cmt.get + "...")
        SQL("delete from file_version where commt_id = {commt_id}").on("commt_id"->cmt.get).execute

        println("Removing commit " + c.hash + " #" + cmt.get + "...")
        SQL("delete from commt where commt_id={commt_id}").on("commt_id"->cmt.get).execute
      }

      currentCommitId = SQL("insert into commt(author_id, hash, project_id, commt_type, dt) values ({author_id}, {hash}, {project_id}, {commt_type}, {dt})").on(
        "author_id"->authorId,
        "hash"->c.hash,
        "project_id"->currentProjectId.get,
        "commt_type"->c.commitType.id,
        "dt"->new Timestamp(c.dt.getMillis())
      ).executeInsert()

      currentCommitDt = Some(c.dt.toDate)
      return true
    }
  }

  def commitFinished(c: RepositaryCommit) {
    println("... processed commit " + c.hash)
    SQL("update commt set processed = true where commt_id={commt_id}").on("commt_id"->currentCommitId.get).execute
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

    if (metrics.fingerprint.isDefined) {
      val fp = metrics.fingerprint.get
      val md5 = fp.nonWhitespaceMd5.toArray

      val fileVersionId = SQL("insert into file_version(commt_id, file_id, similarity, nonws_md5, is_new) values({commt_id}, {file_id}, 0, {nonws_md5}, {is_new})").on(
        "commt_id"->currentCommitId.get,
        "file_id"->fileId,
        "nonws_md5"->md5,
        "is_new"->metrics.isNewFile
      ).executeInsert()

      println("Inserted new version for the file, md5nws = " + md5.map(b => "%02x" format b).mkString + " file version is " + fileVersionId)

      val fsql = SQL("insert into fingerprint(file_version_id, type, key, value, line_count) values({file_version_id}, {type}, {key}, {value}, {line_count})")

      for(p<-fp.fingerprintA) {
        fsql.on(
          "file_version_id"->fileVersionId,
          "type"->'A',
          "key"->p.key,
          "value"->p.value,
          "line_count"->p.lineCount
        ).execute
      }

      for(p<-fp.fingerprintB) {
        fsql.on(
          "file_version_id"->fileVersionId,
          "type"->'B',
          "key"->p.key,
          "value"->p.value,
        "line_count"->p.lineCount
        ).execute
      }

      val r = TextFingerprintCalculator.getTextFingerprintArrays(metrics.fingerprint.get)
      putIntoCache(fileVersionId.get, r)
      updateSimilarFiles(fileVersionId.get, metrics)
    }
    println()
  }

  def processingFile(fileName: String, oldFileName: String, newFileName: String) {
    println("Processing file " + fileName + "...")
  }

  def setProgress(current: Int, maximum: Int) {
    val percent = (current * 100.0) / maximum
    println("Completed " + ("%.2f" format percent) + "%")
  }

  private var fileCategoryMap = scala.collection.mutable.Map[String, Int]()
  def getFileCategoryId(name: String) = fileCategoryMap.get(name)

  def fetchTypeList(ftl: FileTypeList) {

    SQL(
      """
        |select fc.file_category_id, fc.name, fc.regex, fc.diff_handler, fc.cloc_language, fc.priority
        |from file_category fc
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
      val clocLanguage = r[Option[String]]("cloc_language")
      val priority = r[Int]("priority")

      ftl.append(FileCategoryRegex(name, new Regex(regex), priority,
        diffHandler match {
          case Some(x) => Some(DiffHandlerType.withName(x))
          case None => None
        },
        clocLanguage
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

  def updateSimilarFiles(fileVersionId: Long, metrics: FileMetrics) = {
    println("Looking for exactly equal files for " + metrics.fileName + " version = " + fileVersionId + "...")

    val upds = SQL("update file_version set similar_file_version_id={similar_file_version_id}, similarity={similarity} where file_version_id={file_version_id}")

    var prevFvId : Option[Long] = None
    SQL(
      """
        |select c.dt, fv.file_version_id, fv.similarity, fv.similar_file_version_id from
        |file_version fvo
        |join file_version fv on (fv.nonws_md5 = fvo.nonws_md5)
        |join commt c on (c.commt_id = fv.commt_id)
        |where fvo.file_version_id = {file_version_id}
        |order by dt;
      """.stripMargin
    ).on("file_version_id"->fileVersionId).apply().foreach{ row =>

      val fvId = row[Long]("file_version_id")
      val similarFvId = row[Option[Long]]("similar_file_version_id")

      if (!prevFvId.isEmpty && prevFvId != similarFvId) {
        println("Updating exact match. Now file " + fvId + " is version of " + prevFvId)
        upds.on(
          "file_version_id"->fvId,
          "similar_file_version_id"->prevFvId.get,
          "similarity"->1.0
        ).execute()
      }
      prevFvId = Some(fvId)
    }

    val fpx = selectFingerprint(fileVersionId)
    var maxSimilarityBefore = 0.0
    var similarFileBeforeId : Option[Long] = None

    println("Looking for similar files for " + metrics.fileName + " version = " + fileVersionId + "...")
    var count : Int = 0
    SQL(
      """
        |select c.dt, fv.file_version_id, fv.similarity
        |from file_version fv
        |join commt c on (c.commt_id = fv.commt_id)
        |where fv.similarity < 1.0 and file_version_id in
        |(select distinct fp.file_version_id from file_version fvo
        |join fingerprint fpo on (fvo.file_version_id = fpo.file_version_id)
        |join fingerprint fp on ((fp.type = fpo.type) and (fp.key = fpo.key) and (fp.value = fpo.value))
        |where fvo.file_version_id = {file_version_id} and fp.file_version_id != {file_version_id})
      """.stripMargin
    ).on("file_version_id"->fileVersionId).apply().foreach{ row =>
      count += 1
      val dt = row[Date]("dt")
      val fvId = row[Long]("file_version_id")
      val similarity = row[Float]("similarity")

      val fpy = selectFingerprint(fvId)

      val newSimilarity = TextFingerprintCalculator.getSimilarity(fpx, fpy)
      if (dt.before(currentCommitDt.get)) {
        if (newSimilarity > maxSimilarityBefore) {
          maxSimilarityBefore = newSimilarity
          similarFileBeforeId = Some(fvId)
        }
      }
      else {
        if (newSimilarity > similarity) {
          println("Previous file is similar to current for "+
            ("%.2f" format newSimilarity*100)+
            "%. Now file " + fvId + " is version of " + fileVersionId)
          upds.on(
            "file_version_id"->fvId,
            "similar_file_version_id"->fileVersionId,
            "similarity"->newSimilarity
          ).execute()
        }
      }
    }

    println("For " + metrics.fileName + " version = " + fileVersionId + " processed " + count + " similar files.")
    if (similarFileBeforeId.isDefined) {
      println("This file is similar to previous file for "+
        ("%.2f" format maxSimilarityBefore*100)+
        "%. Now file " + fileVersionId + " is version of " + similarFileBeforeId.get)
      upds.on(
        "file_version_id"->fileVersionId,
        "similar_file_version_id"->similarFileBeforeId.get,
        "similarity"->maxSimilarityBefore
      ).execute()
    }

    waitForCacheOp()
  }

  def shutdown = shutdownCache()

  final val fingerprintCacheSize = 256000

  lazy val fingerprintCache = {
    val result = new ConcurrentLinkedHashMap.Builder[Long, TextFingerprintArrays]()
    result.maximumWeightedCapacity(fingerprintCacheSize) // 256000 versions hold in cache, each ~ 512bytes, todo: move to config
    result.build()
  }

  def selectFingerprint(fileVersionId: Long) : TextFingerprintArrays = {

    val fp = getFromCache(fileVersionId)
    if (fp.isEmpty) {
      println("Fetching fingerprints for version = " + fileVersionId + "...")

      val a = new mutable.MutableList[FingerprintPart]()
      val b = new mutable.MutableList[FingerprintPart]()

      SQL(
        """
          |select type, key, value, line_count from fingerprint where file_version_id={file_version_id}
        """.stripMargin
      ).on("file_version_id"->fileVersionId).apply().foreach{ row =>

        val typ = row[String]("type")
        val key = row[Int]("key")
        val value = row[Int]("value")
        val lineCount = row[Int]("line_count")

        val l = if (typ == "A") a else b
        l += FingerprintPart(key, value, lineCount)
      }

      val r = TextFingerprintCalculator.getTextFingerprintArrays(TextFingerprint(Seq[Byte](), a, b))
      putIntoCache(fileVersionId, r)
      r
    }
    else
      fp.get
  }

  val useMemcached = true
  lazy val memcached = {
    new MemcachedClient(
      new InetSocketAddress("localhost", 11211));
  }

  def waitForCacheOp() {
    if (useMemcached) {
      memcached.set("just-wait", 10, 0).get()
    }
  }

  def shutdownCache() {
    if (useMemcached) {
      memcached.shutdown()
    }
  }

  def putIntoCache(fileVersionId: Long, fingerprint: TextFingerprintArrays) = {
    if (useMemcached) {
      val exp = (System.currentTimeMillis() / 1000L) + 365*24*60*60;
      memcached.set("ffp" + fileVersionId, exp.toInt, fingerprint)
    }
    else {
      fingerprintCache.put(fileVersionId, fingerprint)
    }
  }

  def getFromCache(fileVersionId: Long) : Option[TextFingerprintArrays] = {
    if (useMemcached) {
      val obj = memcached.get("ffp" + fileVersionId)
      if (obj == null)
        None
      else
        obj match {
          case fp: TextFingerprintArrays => Some(fp)
          case _ => throw new ClassCastException
        }
    }
    else {
      val fp = fingerprintCache.get(fileVersionId)
      if (fp == null)
        None
      else
        Some(fp)
    }
  }
}
