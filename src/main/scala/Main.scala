import java.io._
import java.net._

object Main extends App {
  def buildDatabaseName(tenant: String): String = {
    if (tenant == "prod") {
      return "main-db"
    }
    return s"$tenant-db"
  }

  val serverSocket = new ServerSocket(6123)
  val tenantOption = sys.env.get("TENANT")
  if (tenantOption.isEmpty)
    throw new Exception("Undefined TENANT env var")
  val databaseName = buildDatabaseName(tenantOption.get)

  DatabaseManager.setupDb(s"data/$databaseName.db")

  while (true) {
    val clientSocket = serverSocket.accept()
    println("Client connected!")

    val handler = new Handler(clientSocket)
    handler.start()
  }

  serverSocket.close()
}
