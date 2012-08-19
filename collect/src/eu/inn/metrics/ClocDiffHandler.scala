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

import scala.Console._

class ClocDiffHandler (clocPath : String, oldFilePath : String, newFilePath : String, lang : String, ext : String)
  extends DiffHandlerBase(oldFilePath, newFilePath, ext) {

  override def run() : Int = {
    val cmd = new ClocCommand(clocPath)
    val r =
    
    if (oldFilePath.isEmpty)
      cmd.cloc(newFilePath, lang, ext, 1)
    else
    if (newFilePath.isEmpty)
      cmd.cloc(oldFilePath, lang, ext, -1)
    else
      cmd.diff(oldFilePath, newFilePath, lang, ext)

    super.run()

    println("" + MetricType.LOC_ADDED + ": " + r.codePlus)
    println("" + MetricType.LOC_REMOVED + ": " + r.codeMinus)
    println("" + MetricType.LOC_CHANGED + ": " + r.codeChanged)
    println("" + MetricType.LOC_UNCHANGED + ": " + r.codeUnchanged)

    println("" + MetricType.COMMENT_ADDED + ": " + r.commentsPlus)
    println("" + MetricType.COMMENT_REMOVED + ": " + r.commentsMinus)
    println("" + MetricType.COMMENT_CHANGED + ": " + r.commentsChanged)
    println("" + MetricType.COMMENT_UNCHANGED + ": " + r.commentsUnchanged)

    0
  }
}
