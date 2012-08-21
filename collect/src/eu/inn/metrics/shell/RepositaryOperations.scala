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

package eu.inn.metrics.shell

import collection.mutable
import org.joda.time.format.DateTimeFormat
import util.matching._
import java.io.OutputStream
import concurrent.SyncVar
import eu.inn.metrics._
import scala.Some
import eu.inn.metrics.CollectMetricsConfig
import eu.inn.metrics.FileMetrics
import scala.Some

class RepositaryOperations(config: CollectMetricsConfig) extends ProcessCommandBase(config.inputDirectory, "git") {

  def gitVersion() = {
    var version = GitVersion(0, 0, 0, 0)

    val regx: Regex = """git version (\d+)\.(\d+)\.(\d+)\.(\d+)(.*)""".r
    var unparsedOutput = ""

    val parse = (s: String) => {
      val o = regx.findFirstMatchIn(s)
      if (!o.isEmpty)
        version = GitVersion(o.get.group(1).toInt, o.get.group(2).toInt, o.get.group(3).toInt, o.get.group(4).toInt, o.get.group(5))
      else
        unparsedOutput += s + eol
    }

    val cmd = List("--version")
    run(cmd, parse, None)

    if (!unparsedOutput.isEmpty || version.hi == 0)
      throw ProcessCommandException("Couldn't parse " + cmd + " result: " + unparsedOutput, 0)

    version
  }

  def originUrl() = {
    val regx: Regex = """origin\s*(.*)://(?:.*@)?(.*?)(\s*)\(fetch\)""".r
    var unparsedOutput = ""

    var result: String = ""
    val parse = (s: String) => {
      val o = regx.findFirstMatchIn(s)
      if (!o.isEmpty)
        result = o.get.group(2)
      else
        unparsedOutput += s + eol
    }

    val cmd = List("remote", "-v")
    run(cmd, parse, None)

    if (result.isEmpty())
      throw ProcessCommandException("Couldn't parse " + cmd + " result: " + unparsedOutput, 0)

    result
  }

  def fetchCommitLog(): Seq[RepositaryCommit] = {

    /*
    executed command:
    git log --pretty=format:"%H; %an; %ae; %ai; %P;"

    expected line of log:
    23873339243c520fbac496aa40326fcde62bab1a; Magomed Abdurakhmanov; maqdev@gmail.com; 2012-08-13 09:25:51 +0200; a2ee1e8b0298acfd81a4ae15f288c77315153343;
    */

    val regx: Regex = """(.*); (.*); (.*); (.*); (.*)?;""".r
    var unparsedOutput = ""

    var result = mutable.MutableList[RepositaryCommit]()

    val fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss Z")

    val parse = (s: String) => {
      val o = regx.findFirstMatchIn(s)
      if (!o.isEmpty) {
        val dt = org.joda.time.DateTime.parse(o.get.group(4), fmt)
        val parent_hashes = o.get.group(5).split(" ")

        val commitType = if (parent_hashes.length == 2 && parent_hashes(0) != parent_hashes(1) && !parent_hashes(1).isEmpty)
          RepositaryCommitType.MERGE
        else
          RepositaryCommitType.NORMAL

        result += RepositaryCommit(o.get.group(1), o.get.group(2), o.get.group(3), commitType, dt)
      }
      else {
        unparsedOutput += s + eol
      }
    }: Unit

    val cmd = List("log", "--all", """--pretty=format:%H; %an; %ae; %ai; %P;""")
    run(cmd, parse, None)

    if (!unparsedOutput.isEmpty)
      throw ProcessCommandException("Couldn't parse " + cmd + " result: " + unparsedOutput, 0)

    result.toSeq
  }

  def fetchCommitMetrics(commit: RepositaryCommit, getMetrics: (String, String, String) => FileMetrics): Seq[FileMetrics] = {
    val regxFirstLine: Regex = """(.*);""".r
    var unparsedOutput = ""
    var hash = ""

    var result = new mutable.MutableList[FileMetrics]

    var fileName = ""
    var oldFileName = ""
    var newFileName = ""

    val parse = (s: String, os: SyncVar[OutputStream]) => {
      if (hash.isEmpty()) {
        val o = regxFirstLine.findFirstMatchIn(s)
        if (!o.isEmpty)
          hash = o.get.group(1)
        else
          unparsedOutput += s + eol
      }
      else {
        if (s == "--- end ---") {
          result += getMetrics(fileName, oldFileName, newFileName)

          val o = os.get
          o.write(eol.getBytes("UTF-8"))
          o.flush()
        }
        else {
          val idx = s.indexOf(":")
          if (idx > 0) {
            val key = s.substring(0, idx)
            val value = s.substring(idx + 1, s.length)

            key match {
              case "1" => fileName = value
              case "2" => oldFileName = if (value == "/dev/null") "" else value
              case "5" => newFileName = if (value == "/dev/null") "" else value
              case _ => ()
            }
          }
          else
            unparsedOutput += s + eol;
        }
      }
    }: Unit

    val diffToolCmd = config.diffwrapperCmd;

    val cmd = List("show", "--ext-diff", """--pretty=format:%H;""", commit.hash)
    var os = new SyncVar[OutputStream]
    try {
      run(cmd, (s: String) => parse(s, os), Some(os), ("GIT_EXTERNAL_DIFF" -> diffToolCmd))
    }
    finally {
      if (os.isSet)
        os.get.close()
    }

    if (!unparsedOutput.isEmpty || hash != commit.hash)
      throw ProcessCommandException("Couldn't parse " + cmd + " result: " + unparsedOutput, 0)

    result
  }
}

