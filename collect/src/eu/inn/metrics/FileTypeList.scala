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

import diff.DiffHandlerType
import scala.collection._
import scala.{Seq, List}
import org.apache.commons.io.FilenameUtils
import shell.{ClocFileType, ClocCommand}
import util.matching.Regex
import io.Source

class FileTypeList(fileTypeRegexList: String, clocCmd: String) {

  val defaultFileTypesMap = loadFileMap()
  val sortedFileTypeRegexList = loadFileCategoryRegex().sortBy(f => - f.priority)

  case class FileTypeResult(handlerType : DiffHandlerType.Value, language: String, extension: String, category: String)

  def getFileType(fileName: String): FileTypeResult = {
    val ext = FilenameUtils.getExtension(fileName)

    val default = defaultFileTypesMap.get(ext) match {
      case Some(s) => FileTypeResult(s.diffHandlerType, s.language, ext, s.language)
      case None => FileTypeResult(DiffHandlerType.BINARY, ext, ext, ext)
    }

    for (ft <- sortedFileTypeRegexList)
      if (ft.regex.findFirstIn(fileName).isDefined) {

        val handler = ft.diffHandlerType.getOrElse(default.handlerType)
        val language = ft.language.getOrElse(default.language)
        return FileTypeResult(handler, language, ext, ft.category)
      }

    default
  }

  /*
  * File format:
  * category|*language|*handler_type|regular_expression
  *
  * */
  def parseFileCategoryRegexLine (s: String) = {
    val a = s.split('|').toArray
    priority -= 1
    val category = a(0)
    val language = if (a(1).isEmpty) None else Option(a(1))
    val handler = if (a(2).isEmpty) None else Option(DiffHandlerType.withName(a(2)))
    val regex = a(3)
    FileCategoryRegex(category, new Regex(regex), priority, handler, language)
  }
  var priority = 0

  def loadFileCategoryRegex (): Seq[FileCategoryRegex] = {
    Source.fromFile(fileTypeRegexList).getLines().map(s => parseFileCategoryRegexLine(s)).toSeq
  }


  /* not used
  def deserialize(fileName: String): Seq[ClocFileType] = {
    val f = new FileInputStream(fileName)
    val o = new ObjectInputStream(f)
    try {
      o.readObject().asInstanceOf[Seq[ClocFileType]]
    }
    finally {
      o.close()
      f.close()
    }
  }

  def serialize(fileTypes: Seq[ClocFileType], fileName: String) {
    val f = new FileOutputStream(fileName)
    val o = new ObjectOutputStream(f)
    try {
      o.writeObject(fileTypes)
    }
    finally {
      o.close()
      f.close()
    }
  }
  */

  def loadFileMap() = {

    val cloc = new ClocCommand(clocCmd)
    val types = cloc.getLanguages()
    val map = mutable.Map[String, ClocFileType]()
    for (t <- types)
      for (e <- t.extensions)
        map += (e -> t)
    map
  }
}
