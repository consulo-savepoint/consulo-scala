package org.jetbrains.jps.incremental.scala.remote

import java.net.{Socket, InetAddress}
import java.io._
import org.jetbrains.jps.incremental.scala._
import com.martiansoftware.nailgun.NGConstants
import com.intellij.util.Base64Converter
import com.intellij.openapi.compiler.CompilerMessageCategory

/**
 * @author Pavel Fatin
 * @author Dmitry Naydanov
 */
trait RemoteResourceOwner {
  protected val address: InetAddress
  protected val port: Int
  
  protected val currentDirectory = System.getProperty("user.dir")
  protected val serverAlias = "compile-server"

  def send(command: String, arguments: Seq[String], client: Client) {
    using(new Socket(address, port)) { socket =>
      using(new DataOutputStream(new BufferedOutputStream(socket.getOutputStream))) { output =>
        createChunks(command, arguments).foreach(_.writeTo(output))
        output.flush()
        using(new DataInputStream(new BufferedInputStream(socket.getInputStream))) { input =>
          handle(input, client)
        }
      }
    }
  }
  
  protected def using[A <: { def close() }, B](resource: A)(block: A => B): B = {
    try {
      block(resource)
    } finally {
      resource.close()
    }
  }

  protected def handle(input: DataInputStream, client: Client) {
    val processor = new ClientEventProcessor(client)

    while (!client.isCanceled) {
      Chunk.readFrom(input) match {
        case Chunk(NGConstants.CHUNKTYPE_EXIT, code) =>
          return
        case Chunk(NGConstants.CHUNKTYPE_STDOUT, data) =>
          try {
            val event = Event.fromBytes(Base64Converter.decode(data))
            processor.process(event)
          } catch {
            case e: Exception =>
              val chars = {
                val s = new String(data)
                if (s.length > 50) s.substring(0, 50) + "..." else s
              }
              client.message(CompilerMessageCategory.ERROR, "Unable to read an event from: " + chars)
              client.trace(e)
          }
        // Main server class redirects all (unexpected) stdout data to stderr.
        // In theory, there should be no such data at all, however, in practice,
        // SBT "leaks" some messages into console (e.g. for "explain type errors" option).
        // Report such output not as errors, but as warings (to continue make process).
        case Chunk(NGConstants.CHUNKTYPE_STDERR, data) =>
          client.message(CompilerMessageCategory.WARNING, fromBytes(data))
        case Chunk(kind, data) =>
          client.message(CompilerMessageCategory.ERROR, "Unexpected server output: " + data)
      }
    }
  }

  protected def createChunks(command: String, args: Seq[String]): Seq[Chunk] = {
    args.map(s => Chunk(NGConstants.CHUNKTYPE_ARGUMENT.toChar, toBytes(s))) :+
      Chunk(NGConstants.CHUNKTYPE_WORKINGDIRECTORY.toChar, toBytes(currentDirectory)) :+
      Chunk(NGConstants.CHUNKTYPE_COMMAND.toChar, toBytes(command))
  }

  private def toBytes(s: String) = s.getBytes

  private def fromBytes(bytes: Array[Byte]) = new String(bytes)
}

case class Chunk(kind: Chunk.Kind, data: Array[Byte]) {
  def writeTo(output: DataOutputStream) {
    output.writeInt(data.length)
    output.writeByte(kind.toByte)
    output.write(data)
  }
}

object Chunk {
  type Kind = Char

  def readFrom(input: DataInputStream): Chunk = {
    val size = input.readInt()
    val kind = input.readByte().toChar
    val data = {
      val buffer = new Array[Byte](size)
      input.readFully(buffer)
      buffer
    }
    Chunk(kind, data)
  }
}