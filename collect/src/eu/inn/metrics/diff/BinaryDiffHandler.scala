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

import eu.inn.metrics.{MetricType, FileMetrics}
import java.io.File

class BinaryDiffHandler(fileName: String, oldFilePath: String, newFilePath: String, category: Option[String], language: String) extends
  DiffHandlerBase(fileName, oldFilePath, newFilePath, category, language) {

  def run(): FileMetrics = {

    val metrics = scala.collection.mutable.Map[MetricType.Value, Int]()

    if (oldFilePath.isEmpty) {
      metrics += (MetricType.FILES_ADDED -> 1)

      val f = new File(newFilePath)
      metrics += (MetricType.BYTES_ADDED -> f.length.toInt)
    }
    else
    if (newFilePath.isEmpty) {
      metrics += (MetricType.FILES_REMOVED -> 1)

      val f = new File(oldFilePath)
      metrics += (MetricType.BYTES_REMOVED -> f.length.toInt)
    }
    else {
      metrics += (MetricType.FILES_CHANGED -> 1)

      val fold = new File(oldFilePath)
      val fnew = new File(newFilePath)

      metrics += (MetricType.BYTES_DELTA -> (fnew.length - fold.length).toInt)
    }

    FileMetrics(fileName, category, language, metrics, oldFilePath.isEmpty && !newFilePath.isEmpty, None)
  }
}
