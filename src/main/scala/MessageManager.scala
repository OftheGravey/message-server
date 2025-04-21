import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement
import javax.crypto.spec.PBEKeySpec
import java.util.Arrays
import scala.collection.mutable.ArrayBuffer
import javax.crypto.SecretKeyFactory
import java.security.SecureRandom
import java.sql.Blob
import javax.sql.rowset.serial.SerialBlob
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.sqlite.SQLiteException
import scala.util.{Try, Success, Failure}
import protocols.{UserMessage, ClientState}

sealed trait InsertMessageError
case object NoMessageError extends InsertMessageError
case object RecipientUnknown extends InsertMessageError
case object BadSender extends InsertMessageError
case object MessageError extends InsertMessageError 


object MessageManager {

     def insertMessage(message: UserMessage): InsertMessageError = {
        val connection: Connection = DatabaseManager.createConnection()

        println("Inserting message...")
        
        var stmt = connection.prepareStatement(
            """
            INSERT INTO messages
                (sender, recipient, title, content)
            VALUES (?, ?, ?, ?);
                """
        )

        stmt.setString(1, message.getSender())
        stmt.setString(2, message.getRecipient())
        stmt.setString(3, message.getTitle())
        stmt.setString(4, message.getContent())

        val insertResult: Try[Int] = Try {
            stmt.executeUpdate()
        }

        insertResult match {
            case Success(value) => 
                println(s"User ${message.getSender()} inserted a message.")
                return NoMessageError
            case Failure(e: SQLiteException) => 
                println(s"User ${message.getSender()} failed to add a message.", e)
                return MessageError
        }
    }

    def readMessages(username: String, sender: Boolean): Array[UserMessage] = {
        val connection: Connection = DatabaseManager.createConnection()
        val statement: Statement = connection.createStatement() 

        var userColumn: String = ""
        if (sender) {
            userColumn = "sender"
        }
        else {
            userColumn = "recipient"
        }

        val result = statement.executeQuery(
            s"""
            SELECT sender, recipient, title, content
            FROM messages
            WHERE $userColumn = '$username'
            LIMIT 3;
            """
        )
        val messageList = new ArrayBuffer[UserMessage]()

        while (result.next()) {
            val message = UserMessage.newBuilder()
                .setSender(result.getString("sender"))
                .setRecipient(result.getString("recipient"))
                .setTitle(result.getString("title"))
                .setContent(result.getString("content"))
                .build()

            messageList += message
        }

        return messageList.toArray
    }
}
