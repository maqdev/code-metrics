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

import sys.process.{ProcessIO, Process}
import scala.{None, Some, Seq}
import java.io.OutputStream
import concurrent.SyncVar

abstract class ProcessCommandBase(workDirectory: String, commandName: String) {
  val eol = sys.props("line.separator")

  protected def run(args: Seq[String], f: String => Unit, os: Option[SyncVar[OutputStream]], extraEnv: (String, String)*) {

    var errorLines: String = ""

    val pio = new ProcessIO(
      stdin => os match {
        case Some(x) => x.put(stdin)
        case None => stdin.close()
      },
      stdout => scala.io.Source.fromInputStream(stdout).getLines().foreach(f),
      stderr => scala.io.Source.fromInputStream(stderr).getLines().foreach(error => errorLines += error + eol)
    )

    val currentDir = if (workDirectory.isEmpty) sys.props("user.dir") else workDirectory
    val cwd = new java.io.File(currentDir)

    val process = Process(commandName +: args, cwd, extraEnv: _*).run(pio)
    val result = process.exitValue()

    if (!errorLines.isEmpty || result != 0)
      throw ProcessCommandException(errorLines, result)
  }
}

