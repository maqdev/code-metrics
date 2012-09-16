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
 *
 *  Generates stable fingerprints for text, so we can perform fast lookup on similar texts
 *  and calculate similarity without full diff
 *
 *  Algorithm:
 *
 *  1. calculate crc for each line of text (empty lines are ignored, crc is calculated on non-whitespace characters)
 *  2. group crc's of each line are grouped into c-grams using division remainder of crc
 *
 *  On output we have list of
 *    (c-gram value, n-gram position, line-count)
 */

package eu.inn.metrics.fingerprint

import java.io.{BufferedReader}
import java.security.MessageDigest
import collection.mutable
import java.util.zip.CRC32
import scala.util.control.Breaks._

class TextFingerprintCalculator(ngramCount: Int, cgramCount: Int) {

  def getFingerprint(textReader: BufferedReader) = {

    val md5 = MessageDigest.getInstance("MD5")
    val crc = new CRC32()

    var lines = new mutable.MutableList[Long]()

    breakable{ while(textReader.ready()) {
      val sOriginal = textReader.readLine()
      if (sOriginal != null) {
        val s = sOriginal.filter( c => !c.isWhitespace )
        if (!s.isEmpty) {
          val bytes = s.getBytes("UTF-8")
          md5.update(bytes)
          crc.reset()
          crc.update(bytes)
          lines += crc.getValue()
        }
      }
      else
        break
    }}

    val linesPerNgram = math.max(lines.size/ngramCount,1)
    val cgrams = new Array[CRC32](cgramCount)
    val cgramLineCount = new Array[Int](cgramCount)

    for (i <- 0 until cgramCount) {
      cgrams(i) = new CRC32()
    }

    val parts = new mutable.MutableList[FingerprintPart]()
    var line = 0
    for (ngram <- 0 until ngramCount) {
      for (i <- 0 until cgramCount) {
        cgrams(i).reset()
        cgramLineCount(i) = 0
      }

      var count = math.min(linesPerNgram, lines.size - line)
      while (count > 0) {
        val crc = lines(line)
        val cgramPos = (crc % cgramCount).toInt
        cgrams(cgramPos).update(crc.toByte)
        cgrams(cgramPos).update((crc >>> 8).toByte)
        cgrams(cgramPos).update((crc >>> 16).toByte)
        cgrams(cgramPos).update((crc >>> 24).toByte)
        cgramLineCount(cgramPos) += 1
        line += 1
        count -= 1
      }

      for (i <- 0 until cgramCount) {
        if (cgramLineCount(i)>0) {
          parts += new FingerprintPart(ngram, i, cgrams(i).getValue(), cgramLineCount(i))
        }
      }
    }

    TextFingerprint(if (lines.isEmpty) Seq[Byte]() else md5.digest().toSeq, parts.toSeq)
  }

  def getSimilarity(a: TextFingerprint, b: TextFingerprint) : Double = {
    if (a.nonWhitespaceMd5 == b.nonWhitespaceMd5) return 1.0

    val (aa, aLineCount) = getFingerprintAsArrays(a.fingerprint)
    val (ba, bLineCount) = getFingerprintAsArrays(b.fingerprint)

    val lineWeight = 1.0/(aLineCount+bLineCount)
    var similarity = 1.0
    for (i <- 0 until ngramCount) {
      for (j <- 0 until cgramCount) {

        val ap = aa(i)(j)
        val bp = ba(i)(j)

        if (ap != null && bp != null) {
          if (ap.cgramValue == bp.cgramValue)
            similarity -= math.abs(ap.lineCount - bp.lineCount) * lineWeight
          else
            similarity -= lineWeight * (ap.lineCount + bp.lineCount)
        }
        else {
          if (ap != null || bp != null) {
            val lc = if (ap != null) ap.lineCount else bp.lineCount
            similarity -= lc * lineWeight
          }
        }
      }
    }

    math.max(similarity, 0)
  }

  private def getFingerprintAsArrays(parts: Seq[FingerprintPart]) = {
    val a = new Array[Array[FingerprintPart]](ngramCount)
    for (i <- 0 until ngramCount) {
      a(i) = new Array[FingerprintPart](cgramCount)
    }

    var lineCount = 0
    for (p<-parts) {
      if (p.pos >= ngramCount)
        throw new RuntimeException("Position of n-gram is more than requested")
      if (p.cgramPos >= cgramCount)
        throw new RuntimeException("Position of c-gram is more than requested")

      a(p.pos)(p.cgramPos) = p
      lineCount += p.lineCount
    }

    (a, lineCount)
  }
}
