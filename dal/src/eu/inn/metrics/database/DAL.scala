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

package eu.inn.metrics.database

import java.sql.Timestamp

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

class DAL(url: String, driver: String) {

  val db = Database.forURL(url, driver = driver)

  val Project = new Table[(Int, String, String)]("project") {
    def projectId = column[Int]("project_id", O PrimaryKey, O AutoInc)

    def name = column[String]("name")

    def path = column[String]("path")

    def * = projectId ~ name ~ path
  }

  val Author = new Table[(Int, String)]("author") {
    def authorId = column[Int]("author_id", O PrimaryKey, O AutoInc)

    def name = column[String]("name")

    def * = authorId ~ name
  }

  val AuthorAlias = new Table[(Int, String)]("author_alias") {
    def authorId = column[Int]("author_id")

    def email = column[String]("email", O PrimaryKey)

    def * = authorId ~ email
  }

  val Commit = new Table[(Long, String, Int, Int, Int, Timestamp)]("commt") {
    def commitId = column[Long]("commt_id", O PrimaryKey, O AutoInc)

    def hash = column[String]("hash")

    def projectId = column[Int]("project_id")

    def authorId = column[Int]("author_id")

    def commitType = column[Int]("commt_type")

    def dt = column[Timestamp]("dt")

    def * = commitId ~ hash ~ projectId ~ authorId ~ commitType ~ dt
  }

  val FileType = new Table[(Int, String)]("file_type") {
    def fileTypeId = column[Int]("file_type_id", O PrimaryKey, O AutoInc)

    def name = column[String]("name")

    def * = fileTypeId ~ name
  }

  val FileCategory = new Table[(Int, Option[Int], String, String, Int, Option[Int], Option[String])]("file_category") {
    def fileCategoryId = column[Int]("file_category_id", O PrimaryKey, O AutoInc)

    def projectId = column[Option[Int]]("project_id")

    def name = column[String]("name")

    def regex = column[String]("regex")

    def priority = column[Int]("priority")

    def fileTypeId = column[Option[Int]]("file_type_id")

    def diffHandler = column[Option[String]]("diff_handler")

    def * = fileCategoryId ~ projectId ~ name ~ regex ~ priority ~ fileTypeId ~ diffHandler
  }

  val File = new Table[(Int, Int, Int, Option[Int], String)]("file") {
    def fileId = column[Int]("file_id", O PrimaryKey, O AutoInc)

    def projectId = column[Int]("project_id")

    def fileTypeId = column[Int]("file_type_id")

    def fileCategoryId = column[Option[Int]]("file_category_id")

    def path = column[String]("path")

    def * = fileId ~ projectId ~ fileTypeId ~ fileCategoryId ~ path
  }

  val MetricType = new Table[(Int, String)]("metric_type") {
    def metricTypeId = column[Int]("metric_type_id", O PrimaryKey, O AutoInc)

    def name = column[String]("name")

    def * = metricTypeId ~ name
  }

  val Metric = new Table[(Long, Int, Int, Int)]("metric") {
    def commitId = column[Long]("commt_id")

    def metricTypeId = column[Int]("metric_type_id")

    def fileId = column[Int]("file_id")

    def value = column[Int]("value")

    def * = commitId ~ metricTypeId ~ fileId ~ value
  }
}
