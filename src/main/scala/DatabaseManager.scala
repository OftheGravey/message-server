import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement
import javax.crypto.spec.PBEKeySpec
import java.util.Arrays
import javax.crypto.SecretKeyFactory
import java.security.SecureRandom
import java.sql.Blob
import javax.sql.rowset.serial.SerialBlob
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.sqlite.SQLiteException
import scala.util.{Try, Success, Failure}

object DatabaseManager {
    private var dbUrl = "jdbc:sqlite:"

    def createConnection(): Connection = {
        var maybeConnection = Option(DriverManager.getConnection(dbUrl))

        maybeConnection match {
            case Some(conn) => 
                println("Connected to DB")
                return conn
            case None => 
                println("Error connecting to DB")
                throw new Error()
        }
    }

    def setupDb(dbPath: String) {
        dbUrl = s"$dbUrl$dbPath"
        val connection: Connection = DatabaseManager.createConnection()
        var statement = connection.createStatement()

        statement.executeUpdate(
            """
            create table if not exists users (
                username string PRIMARY KEY,
                password_hash blob,
                salt blob
            )
            """
        )
        statement.executeUpdate("PRAGMA foreign_keys = ON;")
        statement.executeUpdate(
            """
            create table if not exists messages (
                sender string,
                recipient string,
                title string,
                content string,
                FOREIGN KEY (sender) REFERENCES users(username),
                FOREIGN KEY (recipient) REFERENCES users(username)
            )
            """
        )

        connection.close()
    }
}