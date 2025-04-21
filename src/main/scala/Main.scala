import java.io._
import java.net._

object Main extends App {
  val serverSocket = new ServerSocket(6123)

  DatabaseManager.setupDb("data/sample.db")

  while (true) {
    val clientSocket = serverSocket.accept()
    println("Client connected!")

    val handler = new Handler(clientSocket)
    handler.start()
  }

  serverSocket.close()
}
