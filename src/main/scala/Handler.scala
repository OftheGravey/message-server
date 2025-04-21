import java.lang.Thread
import java.net.Socket
import protocols.{UserMessage, UserMessageList, ClientState, StateChange, UserMessageRequest, AuthMessage, AuthReturn, AuthResponse, UserMessageResponseStatus, UserMessageResponse}
import java.nio.charset.StandardCharsets
import java.io._

sealed trait ExecutionStatus
case object ContinueExecution extends ExecutionStatus
case object EndExecution extends ExecutionStatus

sealed trait StateChangeStatus
case object FailedStateChange extends StateChangeStatus
case object SuccessfulStateChange extends StateChangeStatus

class Handler(var clientSocket: Socket) extends Thread {
    val inputStream: InputStream = clientSocket.getInputStream()
    val outputStream: OutputStream = clientSocket.getOutputStream()

    var username: String = ""
    var clientState: ClientState = ClientState.Setup

    override def run() = {
        val introResult = introduceClient()
        println("Authed user")
        if (introResult == EndExecution){
            clientSocket.close()
        }
        val handerResult = handle()
        clientSocket.close()
    }

    def acceptStateChange(expectedStates: Array[ClientState]): StateChangeStatus = {
        val stateChange = StateChange.parseDelimitedFrom(inputStream)
        val desiredStateChange = stateChange.getState()
        println(s"Client state change to ${desiredStateChange}")

        if (expectedStates.isEmpty){
            clientState = desiredStateChange
            return SuccessfulStateChange
        }

        if (expectedStates.contains(desiredStateChange)) {
            clientState = desiredStateChange
            return SuccessfulStateChange
        }
        else {
            // Client asking for out of bounds change
            return FailedStateChange
        }
    }

    def writeStateChange(state: ClientState) {
        val stateChange = StateChange.newBuilder()
            .setState(state)
            .build()
        stateChange.writeDelimitedTo(outputStream)
        outputStream.flush()
        clientState = state
    }

    def authenticate(): AuthReturn = {
        val expectedStates = Array(ClientState.Signup, ClientState.Login)
        while (clientState == ClientState.Setup) {
            val status = acceptStateChange(expectedStates)
            if (status == FailedStateChange) {
                return AuthReturn.AuthFailure
            }
        }

        val authMsg = AuthMessage.parseDelimitedFrom(inputStream)

        // Check for bad client
        assert(authMsg.getState() == clientState)
        val possibleUsername = authMsg.getUsername()
        val password = authMsg.getPassword()

        if (clientState == ClientState.Login) {
            val attemptLogin = AccessManager.autheticateUser(possibleUsername, password)
            attemptLogin match {
                case NoAccessError =>
                    username = possibleUsername
                    return AuthReturn.AuthSuccess
                case InvalidPassword =>
                    return AuthReturn.AuthFailureRetry
                case _ => 
                    return AuthReturn.AuthFailure
            } 
        }
        else if (clientState == ClientState.Signup) {
            val attemptSignup = AccessManager.createUser(possibleUsername, password)
            attemptSignup match {
                case NoAccessError =>
                    username = possibleUsername
                    return AuthReturn.AuthSuccess
                case _ => 
                    return AuthReturn.AuthFailure
            } 
        }
        else {
            return AuthReturn.AuthFailure
        }
    }

    def authenticationLoop(): AuthReturn =  {
        val authResponseBuilder = AuthResponse.newBuilder()
        while (true) {
            val authResult = authenticate()
            val authResponse = AuthResponse.newBuilder()
                .setResponse(authResult)
                .setToken("Placeholder")
                .build()
            authResponse.writeDelimitedTo(outputStream)
            if (authResult == AuthReturn.AuthSuccess || authResult == AuthReturn.AuthFailure) {
                return authResult
            }
        }
        return AuthReturn.AuthFailure
    }

    def introduceClient(): ExecutionStatus = {
        outputStream.write("Welcome!".getBytes(StandardCharsets.UTF_8))

        val loopResult = authenticationLoop()
        if (loopResult == AuthReturn.AuthFailure) {
            return EndExecution
        }

        writeStateChange(ClientState.UserLoop)

        return ContinueExecution
    }

    def receiveClientUserMessage() {
        var responseStatus = UserMessageResponseStatus.MsgFailure
        val messageForm: UserMessage = UserMessage.parseDelimitedFrom(inputStream)
        val sender = messageForm.getSender()

        if (sender == username) {
            val result = MessageManager.insertMessage(messageForm)
            if (result == NoMessageError)
                    responseStatus = UserMessageResponseStatus.MsgSuccess
        }

        val response = UserMessageResponse.newBuilder()
            .setStatus(responseStatus)
            .build()
        outputStream.flush()
        response.writeDelimitedTo(outputStream)
    }

    def sendClientUserMessages() {
        val messageListParams = UserMessageRequest.parseDelimitedFrom(inputStream)
        val requestUsername = messageListParams.getUsername()
        val requestSender = messageListParams.getSender()
        var messageList: Array[UserMessage] = Array()
        if (requestUsername == username) {
            messageList = MessageManager.readMessages(username, requestSender)
        }
        val responseBuilder = UserMessageList.newBuilder()
        for (message <- messageList) {
            responseBuilder.addList(message)
        }
        val response = responseBuilder.build()
        println(messageList)
        println(response)

        response.writeDelimitedTo(outputStream)
    }

    def handle(): ExecutionStatus = {
        val expectedStates = Array(
            ClientState.UserLoop,
            ClientState.Messaging,
            ClientState.Reading,
            ClientState.Quit
        )
        println("Handling...")
        while (true) {
            if (clientState == ClientState.UserLoop) {
                println("Awaiting client state change")
                acceptStateChange(expectedStates)
            }

            if (clientState == ClientState.Messaging) {
                println("Receiving message from client")
                receiveClientUserMessage()
                acceptStateChange(expectedStates)
            }
            else if (clientState == ClientState.Reading) {
                println("Sending message to client")
                sendClientUserMessages()
                acceptStateChange(expectedStates)
            }
            else if (clientState == ClientState.Quit) {
                return EndExecution
            }

        }
        return EndExecution
    }

}
