// shlurd:  a limited understanding of small worlds
// Copyright 2017-2018 John V. Sichi
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.lingeringsocket.phlebotinum

import com.lingeringsocket.shlurd.cli._

import com.esotericsoftware.kryo.io._

import java.io._
import java.util.zip._

class PhlebSerializer extends ShlurdCliSerializer
{
  import ShlurdCliSerializer._

  def saveSnapshot(
    snapshot : PhlebSnapshot, file : File)
  {
    file.getParentFile.mkdirs
    val zos = new ZipOutputStream(new FileOutputStream(file))
      saveEntry(zos, KRYO_ENTRY)(outputStream => {
        val output = new Output(outputStream)
        kryo.writeObject(output, snapshot)
        output.flush
      })
    try {
    } finally {
      zos.close
    }
  }

  def loadSnapshot(file : File) : PhlebSnapshot =
  {
    // this MUST be preloaded
    ShlurdPrimordialWordnet.frozenCosmos

    val zis = new ZipInputStream(new FileInputStream(file))
    try {
      val nextEntry = zis.getNextEntry
      assert(nextEntry.getName == KRYO_ENTRY)
      val input = new Input(zis)
      kryo.readObject(input, classOf[PhlebSnapshot])
    } finally {
      zis.close
    }
  }
}
