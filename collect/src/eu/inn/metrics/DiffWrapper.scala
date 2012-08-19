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

import org.apache.commons.io.FilenameUtils

object DiffWrapper {
  def main(args: Array[String]) {
    if (args.length < 8) {
      println("Usage : cloc_path file_types_path file_name old_file_path old_hash old_mode new_file_path new_hash new_mode")
    }

    try {

      val clocPath = args(0)
      val fileTypesFileName = args(1)
      val fileName = args(2)
      val oldFilePath = if (args(3) == "/dev/null") "" else args(3)
      val newFilePath = if (args(6) == "/dev/null") "" else args(6)

      val fileTypes = if (fileTypesFileName.isEmpty) FileTypeList.defaultFileTypes else FileTypeList.deserialize(fileTypesFileName)
      val fileTypesMap = FileTypeList.createFileMap(fileTypes)

      val ext = FilenameUtils.getExtension(fileName)

      val (handlerType, lang) = fileTypesMap.get(ext) match {
        case Some(s) => (s.diffHandlerType, s.name)
        case None => (DiffHandlerType.BINARY, ext)
      }

      val h = handlerType match {
        case DiffHandlerType.CLOC => new ClocDiffHandler(clocPath, oldFilePath, newFilePath, lang, ext)
        case DiffHandlerType.BINARY => new BinaryDiffHandler(oldFilePath, newFilePath, ext)
      }

      Console println("filename: " + fileName)
      h.run()
    }
    catch {
      case e => e.printStackTrace
    }
  }
}
