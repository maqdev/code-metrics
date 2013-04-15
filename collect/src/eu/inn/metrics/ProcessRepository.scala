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

import diff.DiffWrapper
import output.OutputHandler
import eu.inn.metrics.shell.{RepositoryOperations}

class ProcessRepository (config: CollectMetricsConfig, outputHandler : OutputHandler) {

  def run() {
    val git = new RepositoryOperations(config)

    outputHandler.gitVersion(git.gitVersion())
    val fileTypeList = new FileTypeList(config.fileCategoryRegexPath, config.clocCmd)

    outputHandler.repositaryUrl(git.originUrl())
    outputHandler.fetchTypeList(fileTypeList)

    try {
      if (!config.onlyInit) {
        val log = git.fetchCommitLog(config)
        val size = log.size
        var i = 0

        outputHandler.setProgress(i, size)
        for (r <- log) {
          if (outputHandler.commitStarted(r)) {
            if (r.commitType == RepositoryCommitType.NORMAL) {

              val metrics = git.fetchCommitMetrics(r,
                (fileName: String, oldFileName: String, newFileName: String) =>
                {
                  outputHandler.processingFile(fileName, oldFileName, newFileName)
                  val dw = new DiffWrapper(config.clocCmd, fileTypeList, config.clocFileSizeLimit)
                  dw.getMetrics(fileName, oldFileName, newFileName, outputHandler.getAllKnownFiles())
                }
              )

              for (m <- metrics)
                outputHandler.fileMetrics(m)
            }
            outputHandler.commitFinished(r)
          }

          i += 1
          outputHandler.setProgress(i, size)
        }
      }
    }
    finally {
      outputHandler.shutdown()
    }
  }
}
