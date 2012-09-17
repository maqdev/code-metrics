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

package eu.inn.metrics.diff

import eu.inn.metrics._
import eu.inn.metrics.FileMetrics
import fingerprint.{FingerprintPart, TextFingerprintCalculator, TextFingerprint}
import shell.{ClocFileWasIgnoredException, ProcessCommandException, ClocCommand}
import java.io.{BufferedReader, FileReader, File}

class ClocDiffHandler(clocPath: String, fileName: String, oldFilePath: String, newFilePath: String, category: Option[String], language: String, extension: String, clocFileSizeLimit: Option[Int])
  extends BinaryDiffHandler(fileName, oldFilePath, newFilePath, category, language) {

  override def run(): FileMetrics = {

    val binaryResult = super.run()
    val result = if (newFilePath.isEmpty) binaryResult else binaryResult.copy(fingerprint = getFingerprints(newFilePath))

    val sizeOld = if (oldFilePath.isEmpty) 0 else (new File(oldFilePath)).length;
    val sizeNew = if (newFilePath.isEmpty) 0 else (new File(newFilePath)).length;

    if (clocFileSizeLimit.isDefined && (sizeOld > clocFileSizeLimit.get || sizeNew > clocFileSizeLimit.get)) {

      val max = math.max(sizeOld, sizeNew)
      val maxInt = if (max > Int.MaxValue) Int.MaxValue else max.toInt
      result.metrics += (MetricType.SIZE_LIMITED -> maxInt)
      return result
    }

    val cmd = new ClocCommand(clocPath)

    try {
      val r =
        if (oldFilePath.isEmpty)
          cmd.cloc(newFilePath, language, extension, 1)
        else
        if (newFilePath.isEmpty)
          cmd.cloc(oldFilePath, language, extension, -1)
        else
          cmd.diff(oldFilePath, newFilePath, language, extension)

      result.metrics += (MetricType.LOC_ADDED -> r.codePlus)
      result.metrics += (MetricType.LOC_REMOVED -> r.codeMinus)
      result.metrics += (MetricType.LOC_CHANGED -> r.codeChanged)
      result.metrics += (MetricType.LOC_UNCHANGED -> r.codeUnchanged)

      result.metrics += (MetricType.COMMENT_ADDED -> r.commentsPlus)
      result.metrics += (MetricType.COMMENT_REMOVED -> r.commentsMinus)
      result.metrics += (MetricType.COMMENT_CHANGED -> r.commentsChanged)
      result.metrics += (MetricType.COMMENT_UNCHANGED -> r.commentsUnchanged)
    }
    catch {
      case e: ClocFileWasIgnoredException =>
        result.metrics += (MetricType.FAILED -> 1)

      case e: ProcessCommandException =>
        result.metrics += (MetricType.FAILED -> 1)
    }

    result
  }

  private def getFingerprints(fileName: String): Option[TextFingerprint] = {

    val file = new File(fileName)
    if (!file.exists() || !file.canRead()) {
      return None
    }

    try {
      val fileReader = new FileReader(file)
      try {
        val buf = new BufferedReader(fileReader)
        try {
          Some(TextFingerprintCalculator.getFingerprint(buf))
        }
        finally {
          buf.close()
        }
      }
      finally {
        fileReader.close()
      }
    }
    catch {
      case e : Throwable =>
        println("Couldn't get fingertips. ")
        e.printStackTrace()

        None
    }
  }
}
