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
 * Author(s): Magomed Abdurakhmanov (maqdev@gmail.com)
 */

import sys.process.{ProcessLogger, Process}
import tools.util.PathResolver.Environment

case class GitException(Message : String, resultCode : Int) extends Exception

case class Config(foo: Int = -1, bar: String = "", xyz: Boolean = false,
                  libname: String = "", libfile: String = "", maxlibname: String = "",
                  maxcount: Int = -1, whatnot: String = "")



object CollectMetrics {

  def git(args : Seq[String], f : String => Unit) : Unit = {

    var errorLines : String = ""
    val pl = ProcessLogger(f, error => errorLines += error + sys.props("line.separator"))

    val result = Process("git", args).!(pl)

    if (!errorLines.isEmpty || result != 0)
      throw GitException(errorLines, result)
  }

  def main(args : Array[String]){

    println("Hello World")


    val parser = new scopt.immutable.OptionParser[Config]("scopt", "2.x") { def options = Seq(
      intOpt("f", "foo", "foo is an integer property") { (v: Int, c: Config) => c.copy(foo = v) },
      opt("o", "output", "output") { (v: String, c: Config) => c.copy(bar = v) },
      booleanOpt("xyz", "xyz is a boolean property") { (v: Boolean, c: Config) => c.copy(xyz = v) },
      keyValueOpt("l", "lib", "<libname>", "<filename>", "load library <libname>")
      { (key: String, value: String, c: Config) => c.copy(libname = key, libfile = value) },
      keyIntValueOpt(None, "max", "<libname>", "<max>", "maximum count for <libname>")
      { (key: String, value: Int, c: Config) => c.copy(maxlibname = key, maxcount = value) },
      arg("<file>", "some argument") { (v: String, c: Config) => c.copy(whatnot = v) }
    ) }
    // parser.parse returns Option[C]
    parser.parse(args, Config()) map { config =>
      // do stuff
    } getOrElse {
      // arguments are bad, usage message will have been displayed
    }

    try
    {
      git(List("--version"), (s : String) => println(s))



    }
    catch
    {
      case e => println("Failed with exception:" + e)
    }
  }
}
