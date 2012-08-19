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

import scala.collection.mutable.Map
import java.io._

object FileTypeList {

  def defaultFileTypes : Seq[FileType] = List(
    FileType("PHP", List("php","php3","php4", "php5"), DiffHandlerType.CLOC),
    FileType("SQL", List("psql","SQL","sql"), DiffHandlerType.CLOC)

  )

  def deserialize(fileName : String) : Seq[FileType] = {
    val f = new FileInputStream(fileName)
    val o = new ObjectInputStream(f)
    try {
      o.readObject().asInstanceOf[Seq[FileType]]
    }
    finally {
      o.close()
      f.close()
    }
  }

  def serialize(fileTypes : Seq[FileType], fileName : String) {
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

  def createFileMap(types: Seq[FileType]) = {
    val map = Map[String, FileType]()

    for (t <- types)
      for (e <- t.extensions)
        map += (e -> t)

    map
  }
}
