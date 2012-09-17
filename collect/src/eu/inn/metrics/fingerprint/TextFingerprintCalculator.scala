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
 *  2. A. group lines into the fingerprints A using position of line, total group count = precision.
 *  3. A. for each group A calculate crc of line crc's
 *  4. B. group crc's of each line into the fingerprints B using division remainder of crc
 *  5. B. for each group B calculate crc of line crc's
 *
 */

package eu.inn.metrics.fingerprint

import java.io.{BufferedReader}
import java.security.MessageDigest
import collection.mutable
import java.util.zip.CRC32
import scala.util.control.Breaks._

object TextFingerprintCalculator {
  private final val precision = 20 // changing precision will invalidate all existing fingerprints
  private final val calc = new TextFingerprintCalculator(precision)

  def getFingerprint(textReader: BufferedReader) = calc.getFingerprint(textReader)
  def getSimilarity(x: TextFingerprint, y: TextFingerprint) = calc.getSimilarity(x, y)
}

class TextFingerprintCalculator(precision: Int) {

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

    // to calculate position ignored fingerprint
    val crcA = new Array[CRC32](precision)
    val linecountA = new Array[Int](precision)
    val linesPerA = math.max(lines.size / precision, 1)

    // to calculate position ignored fingerprint
    val crcB = new Array[CRC32](precision)
    val linecountB = new Array[Int](precision)

    for (i <- 0 until precision) {
      crcA(i) = new CRC32()
      linecountA(i) = 0
      crcB(i) = new CRC32()
      linecountB(i) = 0
    }

    for (line <- 0 until lines.size) {
      val lineCrc = lines(line)

      val apos = math.min(line / linesPerA, precision-1)
      crcA(apos).update(lineCrc.toByte)
      crcA(apos).update((lineCrc >>> 8).toByte)
      crcA(apos).update((lineCrc >>> 16).toByte)
      crcA(apos).update((lineCrc >>> 24).toByte)
      linecountA(apos) += 1

      val bpos = (lineCrc % precision).toInt
      crcB(bpos).update(lineCrc.toByte)
      crcB(bpos).update((lineCrc >>> 8).toByte)
      crcB(bpos).update((lineCrc >>> 16).toByte)
      crcB(bpos).update((lineCrc >>> 24).toByte)
      linecountB(bpos) += 1
    }

    val partsA = new mutable.MutableList[FingerprintPart]()
    val partsB = new mutable.MutableList[FingerprintPart]()
    for (i <- 0 until precision) {
      if (linecountA(i)>0) {
        partsA += new FingerprintPart(i, crcA(i).getValue().toInt, linecountA(i))
      }
      if (linecountB(i)>0) {
        partsB += new FingerprintPart(i, crcB(i).getValue().toInt, linecountB(i))
      }
    }

    TextFingerprint(if (lines.isEmpty) Seq[Byte]() else md5.digest().toSeq, partsA.toSeq, partsB.toSeq)
  }

  def getSimilarity(x: TextFingerprint, y: TextFingerprint) : Double = {
    if (!x.nonWhitespaceMd5.isEmpty && x.nonWhitespaceMd5 == y.nonWhitespaceMd5) return 1.0

    val similarityA = getSimilarityForParts(x.fingerprintA, y.fingerprintA)
    val similarityB = getSimilarityForParts(x.fingerprintB, y.fingerprintB)

    math.max(math.max(similarityA, similarityB), 0)
  }

  private def getSimilarityForParts(x: Seq[FingerprintPart], y: Seq[FingerprintPart]) = {
    val (xa, xLineCount) = getFingerprintAsArrays(x)
    val (ya, yLineCount) = getFingerprintAsArrays(y)

    val lineWeight = 1.0/(xLineCount+yLineCount)
    var similarity = 1.0

    for (i <- 0 until precision) {
      val xp = xa(i)
      val yp = ya(i)

      if (xp != null && yp != null) {
        if (xp.value == yp.value)
          similarity -= math.abs(xp.lineCount - yp.lineCount) * lineWeight
        else
          similarity -= lineWeight * (xp.lineCount + yp.lineCount)
      }
      else {
        if (xp != null || yp != null) {
          val lc = if (xp != null) xp.lineCount else yp.lineCount
          similarity -= lc * lineWeight
        }
      }
    }
    similarity
  }

  private def getFingerprintAsArrays(parts: Seq[FingerprintPart]) = {
    val a = new Array[FingerprintPart](precision)

    var lineCount = 0
    for (p<-parts) {
      if (p.key >= precision)
        throw new RuntimeException("Position of key is more than requested")

      a(p.key) = p
      lineCount += p.lineCount
    }

    (a, lineCount)
  }
}
