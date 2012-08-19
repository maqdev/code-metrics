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

package eu.inn.metrics

import sys.process._
import collection.mutable
import org.joda.time.format.DateTimeFormat

class GitCommand(workDirectory : String = "") extends ProcessCommandBase(workDirectory, "git") {

  def version() = {
    var version = GitVersion(0,0,0,0)

    val regx = """git version (\d+)\.(\d+)\.(\d+)\.(\d+)(.*)""".r
    var unparsedOutput = ""

    val parse = (s : String) => {
      val o = regx.findFirstMatchIn(s)
      if (!o.isEmpty)
        version = GitVersion(o.get.group(1).toInt, o.get.group(2).toInt, o.get.group(3).toInt, o.get.group(4).toInt, o.get.group(5))
      else
        unparsedOutput += s + eol
    }

    val cmd = List("--version")
    run(cmd, parse )

    if (!unparsedOutput.isEmpty || version.hi == 0)
      throw ProcessCommandException("Couldn't parse " + cmd + " result: " + unparsedOutput, 0)

    version
  }

  def originUrl() = {
    val regx = """origin\s*(.*)://(?:.*@)?(.*?)(\s*)\(fetch\)""".r
    var unparsedOutput = ""

    var result : String = ""
    val parse = (s : String) => {
      val o = regx.findFirstMatchIn(s)
      if (!o.isEmpty)
        result = o.get.group(2)
      else
        unparsedOutput += s + eol
    }

    val cmd = List("remote", "-v")
    run(cmd, parse )

    if (result.isEmpty())
      throw ProcessCommandException("Couldn't parse " + cmd + " result: " + unparsedOutput, 0)

    result
  }

  def log() : Seq[GitCommit] = {

    /*
    executed command:
    git log --pretty=format:"%H; %an; %ae; %ai; %P;"

    expected line of log:
    23873339243c520fbac496aa40326fcde62bab1a; Magomed Abdurakhmanov; maqdev@gmail.com; 2012-08-13 09:25:51 +0200; a2ee1e8b0298acfd81a4ae15f288c77315153343;
    */

    val regx = """(.*); (.*); (.*); (.*); (.*)?;""".r
    var unparsedOutput = ""

    var result = mutable.MutableList[GitCommit]()

    val fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss Z")

    val parse = (s : String) => {
      val o = regx.findFirstMatchIn(s)
      if (!o.isEmpty) {
        val dt = org.joda.time.DateTime.parse(o.get.group(4), fmt)
        val parent_hashes = o.get.group(5).split(" ")

        val commitType = if (parent_hashes.length == 2 && parent_hashes(0) != parent_hashes(1) && !parent_hashes(1).isEmpty)
          GitCommitType.MERGE
        else
          GitCommitType.NORMAL

        result += GitCommit(o.get.group(1), o.get.group(2), o.get.group(3), commitType, dt)
      }
      else {
        unparsedOutput += s + eol
      }
    } : Unit

    val cmd = List("log", """--pretty=format:%H; %an; %ae; %ai; %P;""")
    run(cmd, parse )

    if (!unparsedOutput.isEmpty)
      throw ProcessCommandException("Couldn't parse " + cmd + " result: " + unparsedOutput, 0)

    result.toSeq
  }

  def showCommitDiff(commit : GitCommit) {
    val regxFirstLine = """(.*);""".r
    var unparsedOutput = ""
    var hash = ""

    val parse = (s : String) => {
      if (hash.isEmpty()) {

        val o = regxFirstLine.findFirstMatchIn(s)
        if (!o.isEmpty)
          hash = o.get.group(1)
        else
          unparsedOutput += s + eol
      }
    }

    val cmd = List("show", "--ext-diff", """--pretty=format:%H;""", commit.hash)
    run(cmd, parse )

    if (!unparsedOutput.isEmpty || hash != commit.hash)
      throw ProcessCommandException("Couldn't parse " + cmd + " result: " + unparsedOutput, 0)
  }
}

