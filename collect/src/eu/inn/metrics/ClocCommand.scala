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

import collection.mutable
import scala.util.control.Breaks._

class ClocCommand(clocPath : String = "cloc", workDirectory : String = "")
  extends ProcessCommandBase(workDirectory, if (clocPath.isEmpty) "cloc" else clocPath) {

  case class ClocResult(lang : String, codePlus : Int = 0, codeMinus : Int = 0, codeChanged : Int = 0, codeUnchanged : Int = 0,
                        commentsPlus : Int = 0, commentsMinus : Int = 0, commentsChanged: Int = 0, commentsUnchanged : Int = 0)

  def internalCloc(cmd : Seq[String], columns : Seq[String], parseCsvLine : mutable.Map[String, String] => ClocResult) : ClocResult = {
    var firstCsvLineProcessed = false
    var unparsedOutput = ""
    var possibleUnparsedOutput = ""
    val columnMap = mutable.Map[String, Int]()
    var result = ClocResult("")
    var maxColumnIndex = 0

    val parse = (s: String) => {
      if (firstCsvLineProcessed) {
        val v = s.split(",")
        if (v.length >= maxColumnIndex) {

          var m = mutable.Map[String,String]()
          for (c <- columns) {
            m += (c -> v(columnMap(c)).trim)
          }

          result = parseCsvLine(m)
        }
        else
          unparsedOutput += s + eol
      }
      else {
        if (!s.isEmpty) {
          val v = s.toLowerCase.split(",")
          if (v.length >= columns.length) {
            firstCsvLineProcessed = true
            breakable {
              for (c <- columns) {
                val index = v.indexWhere( s => s.trim == c)
                if (index >= 0) {
                  columnMap += (c -> index);
                  if (index > maxColumnIndex) {
                    maxColumnIndex = index
                  }
                }
                else {
                  firstCsvLineProcessed = false
                  break;
                }
              }
            }
          }
        }

        if (!firstCsvLineProcessed) {
          possibleUnparsedOutput += s + eol
        }
      }
    } : Unit

    run(cmd, parse)

    if (!unparsedOutput.isEmpty || result.lang.isEmpty)
      throw ProcessCommandException("Couldn't parse " + cmd + " result: " + possibleUnparsedOutput + unparsedOutput, 0)

    result
  }

  def cloc(filePath : String, lang : String, ext : String, multiplier : Int) : ClocResult = {

    val cmd = List("--force-lang=" + lang + "," + ext, "--csv", filePath)

    val parse = (m: mutable.Map[String, String]) => {
      if (multiplier > 0)
        ClocResult(
          m("language"),
          m("code").toInt,
          0, 0, 0,
          m("comment").toInt,
          0, 0, 0
        )
      else
        ClocResult(
          m("language"),
          0,
          m("code").toInt,
          0, 0,
          0,
          m("comment").toInt,
          0, 0
        )
    }

    internalCloc(cmd,
      List("language", "code", "comment"),
      parse
    )
  }

  def diff(oldFilePath: String, newFilePath: String, lang: String, ext: String) = {

    val cmd = List("--force-lang=" + lang + "," + ext, "--csv", "--diff", oldFilePath, newFilePath)

    val parse = (m: mutable.Map[String, String]) => {
      ClocResult(
        m("language"),
        m("+ code").toInt,
        m("- code").toInt,
        m("!= code").toInt,
        m("== code").toInt,
        m("+ comment").toInt,
        m("- comment").toInt,
        m("!= comment").toInt,
        m("== comment").toInt
      )
    }

    internalCloc(cmd,
      List("language", "+ code", "- code", "!= code", "== code", "+ comment", "- comment", "!= comment", "== comment"),
      parse
    )
  }
}

