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

sealed trait AccessError
case object UserAlreadyExists extends AccessError
case object InvalidPassword extends AccessError
case object InvalidUsername extends AccessError
case object UserUnknown extends AccessError
case object NoAccessError extends AccessError

object AccessManager {
    private val RANDOM = new SecureRandom();
    private val ITERATIONS = 10000;
    private val KEY_LENGTH = 256;

    def getNextSalt(): Array[Byte] = {
        var salt = new Array[Byte](16);
        RANDOM.nextBytes(salt);
        return salt;
    } 

    def hashPassword(password: String, salt: Array[Byte]): Array[Byte] = {
        var password_chars = password.toCharArray()
        var spec = new PBEKeySpec(password_chars, salt, ITERATIONS, KEY_LENGTH)

        Arrays.fill(password_chars, Character.MIN_VALUE)
        
        var skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return skf.generateSecret(spec).getEncoded()
    } 

    def validUsername(username: String): Boolean = {
        if (username.length == 0) {
            return false
        }
        return true
    }

    def validPassword(password: String): Boolean = {
        if (password.length == 0) {
            return false
        }
        return true
    }

    def createUser(username: String, password: String): AccessError  = {
        if (!validPassword(password)){
            return InvalidPassword
        }
        if (!validUsername(username)){
            return InvalidUsername
        }
        val connection: Connection = DatabaseManager.createConnection()

        var salt = getNextSalt()
        var password_hash = hashPassword(password, salt)

        var stmt = connection.prepareStatement(
            """
            INSERT INTO users 
                (username, password_hash, salt)
            VALUES (?, ?, ?);
                """
        )

        stmt.setString(1,username)
        stmt.setBytes(2, password_hash)
        stmt.setBytes(3, salt)

        val insertResult: Try[Int] = Try {
            stmt.executeUpdate()
        }

        connection.close()

        insertResult match {
            case Success(value) => 
                println(s"User $username signed up.")
                return NoAccessError
            case Failure(e: SQLiteException) => 
                println(s"User $username already exists.", e)
                return UserAlreadyExists
        }
    }

    def autheticateUser(username: String, password: String): AccessError = {
        if (!validPassword(password)){
            return InvalidPassword
        }
        if (!validUsername(username)){
            return InvalidUsername
        }

        val connection: Connection = DatabaseManager.createConnection()
        val statement = connection.createStatement()

        val result = statement.executeQuery(
            s"""
            SELECT username, password_hash, salt 
            FROM users
            WHERE username = '$username';"""
        )


        if (!result.isBeforeFirst()) {
            println(s"User $username doesn't exist.")
            connection.close()
            return UserUnknown
        }

        val password_hash = result.getBytes("password_hash")
        val salt = result.getBytes("salt")

        val password_hash_attempt = hashPassword(password, salt)

        connection.close()

        if ( password_hash sameElements password_hash_attempt ) {
            return NoAccessError
        }
        println("Invalid password")
        return InvalidPassword
    }
}