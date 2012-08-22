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

import output.{DatabaseOutputHandler, StdOutputHandler}
import scala.collection.Seq
import shell.ProcessCommandException

object CollectMetrics {

  def main(args: Array[String]) {
    try {
      val parser = new scopt.immutable.OptionParser[CollectMetricsConfig]("collect", "0.0") {
        def options = Seq(
          opt("i", "init-only", "only perform initial step") {
            (v: String, c: CollectMetricsConfig) => c.copy(onlyInit = v.toBoolean)
          } ,

          opt("d", "diffwrapper-cmd", "diffwrapper path") {
            (v: String, c: CollectMetricsConfig) => c.copy(diffwrapperCmd = v)
          } ,

          opt("c", "cloc-cmd", "cloc path") {
            (v: String, c: CollectMetricsConfig) => c.copy(clocCmd = v)
          } ,

          opt("g", "categories", "path to file with list of category regex") {
            (v: String, c: CollectMetricsConfig) => c.copy(fileCategoryRegexPath = v)
          } ,

          opt("b", "db", "output to db. Specify as 'url|driver' (jdbc url and driver's are used" ) {
            (v: String, c: CollectMetricsConfig) => c.copy(outputDb = v)
          } ,

          arg("<repositary_path>", "local path to git repositary directory") { (v: String, c: CollectMetricsConfig)
            => c.copy(inputDirectory = v) }

        )
      }

      // parser.parse returns Option[C]
      parser.parse(args, CollectMetricsConfig("")) map {
        config =>

          if (config.inputDirectory.isEmpty) {
            println(parser.toString())
          }
          else {
            val o = if(config.outputDb.isEmpty) new StdOutputHandler() else {
              val v = config.outputDb.split('|')
              if (v.length != 2) {
                throw new RuntimeException("db parameter is incorrect")
              }
              new DatabaseOutputHandler(v(0), v(1))
            }

            val r = new ProcessRepositary(config, o)
            r.run()
          }
      } getOrElse {
      }
    }
    catch {
      case e: ProcessCommandException =>
        if (e.resultCode != 0) Console println("Exception result code: " + e.resultCode);
        e.printStackTrace()
      case e: Throwable => e.printStackTrace()
    }
  }
}
