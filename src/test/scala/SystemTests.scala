import java.sql.Connection;
import protocols.{UserMessage}

// For more information on writing tests, see
// https://scalameta.org/munit/docs/getting-started.html
class SystemTests extends munit.FunSuite {

  val databaseName = Main.buildDatabaseName("sys-test")
  DatabaseManager.setupDb(s"data/$databaseName.db")

  override def afterAll(): Unit = {
    val connection: Connection = DatabaseManager.createConnection()
    val statement = connection.createStatement()

    statement.executeUpdate("DROP TABLE users;")
    statement.executeUpdate("DROP TABLE messages;")

    connection.close()
  }

  test("basic user creation") {
    val NORMAL_USER_USERNAME = "test-a"
    val NORMAL_USER_PASSWORD = "password"
    val NORMAL_USER_WRONG_PASSWORD = "password_a"
    val UNKNOWN_USER_USERNAME = "test-b"
    val UNKNOWN_USER_PASSWORD = "password_b"

    var result: AccessError = NoAccessError

    result =
      AccessManager.createUser(NORMAL_USER_USERNAME, NORMAL_USER_PASSWORD)
    assert(result == NoAccessError)

    result =
      AccessManager.autheticateUser(NORMAL_USER_USERNAME, NORMAL_USER_PASSWORD)
    assert(result == NoAccessError)

    result = AccessManager.autheticateUser(
      NORMAL_USER_USERNAME,
      NORMAL_USER_WRONG_PASSWORD
    )
    assert(result == InvalidPassword)

    result = AccessManager.autheticateUser(
      UNKNOWN_USER_USERNAME,
      UNKNOWN_USER_PASSWORD
    )
    assert(result == UserUnknown)

    result =
      AccessManager.createUser(NORMAL_USER_USERNAME, NORMAL_USER_PASSWORD)
    assert(result == UserAlreadyExists)
  }

  test("basic message lifecycle") {
    val SENDING_USER = "testa"
    val RECIEVING_USER = "testb"
    val NO_MESSAGES_USER = "testc"
    val messageA = UserMessage
      .newBuilder()
      .setSender(SENDING_USER)
      .setRecipient(RECIEVING_USER)
      .build()

    val insertResult = MessageManager.insertMessage(messageA)
    assert(insertResult == NoMessageError)

    val messageList = MessageManager.readMessages(RECIEVING_USER, false)
    assert(messageList.contains(messageA))
    assert(messageList(0).getRecipient == RECIEVING_USER)
    assert(messageList(0).getSender == SENDING_USER)

    val messageListSender = MessageManager.readMessages(SENDING_USER, true)
    assert(messageListSender.contains(messageA))

    MessageManager.insertMessage(messageA)
    MessageManager.insertMessage(messageA)

    val messageManyList: Array[UserMessage] =
      MessageManager.readMessages(RECIEVING_USER, false)
    assert(messageManyList.length == 3)

    val noMessagesList = MessageManager.readMessages(NO_MESSAGES_USER, false)
    assert(noMessagesList.length == 0)
  }

  test("bad data tests") {
    val BAD_PASSWORD_USER_USERNAME = "test-a"
    val BAD_PASSWORD_USER_PASSWORD = ""
    val BAD_USERNAME_USER_USERNAME = ""
    val BAD_USERNAME_USER_PASSWORD = "password_b"

    var result: AccessError = NoAccessError

    result = AccessManager.createUser(
      BAD_PASSWORD_USER_USERNAME,
      BAD_PASSWORD_USER_PASSWORD
    )
    assert(result == InvalidPassword)

    result = AccessManager.autheticateUser(
      BAD_PASSWORD_USER_USERNAME,
      BAD_PASSWORD_USER_PASSWORD
    )
    assert(result == InvalidPassword)

    result = AccessManager.createUser(
      BAD_USERNAME_USER_USERNAME,
      BAD_USERNAME_USER_PASSWORD
    )
    assert(result == InvalidUsername)

    result = AccessManager.autheticateUser(
      BAD_USERNAME_USER_USERNAME,
      BAD_USERNAME_USER_PASSWORD
    )
    assert(result == InvalidUsername)
  }
}
