import socket
from main.protobuf import message_pb2
from google.protobuf.internal import decoder

HOST = "localhost"
PORT = 6123


def send_message(sock, message):
    # Write length prefix (varint style - like writeDelimitedTo in Java)
    message_bytes = message.SerializeToString()
    length = len(message_bytes)
    while True:
        to_write = length & 0x7F
        length >>= 7
        if length:
            sock.send(bytes([to_write | 0x80]))
        else:
            sock.send(bytes([to_write]))
            break
    # Then write the actual message
    sock.send(message_bytes)


def receive_message(s, message_class):
    # Step 1: Read varint-encoded message length
    varint_buf = b""
    while True:
        byte = s.recv(1)
        if not byte:
            raise EOFError("Socket closed while reading varint length")
        varint_buf += byte
        try:
            msg_len, _ = decoder._DecodeVarint(varint_buf, 0)
            break
        except decoder._DecodeError:
            continue  # keep reading until varint is complete

    # Step 2: Read exactly msg_len bytes
    msg_data = b""
    while len(msg_data) < msg_len:
        chunk = s.recv(msg_len - len(msg_data))
        if not chunk:
            raise EOFError("Socket closed before full message was received")
        msg_data += chunk

    # Step 3: Parse with the provided message class
    msg = message_class()
    msg.ParseFromString(msg_data)
    return msg


def op_send_message(s, name):
    message = message_pb2.UserMessage()

    message.version = 1
    message.sender = name
    message.title = input("Enter title:")
    message.recipient = input("Enter recipient:")
    message.content = input("Enter content:")

    send_message(s, message)


def send_user_message(s, username, title, recipient, content):
    message = message_pb2.UserMessage()

    message.version = 1
    message.sender = username
    message.title = title
    message.recipient = recipient
    message.content = content

    writeStateChange(s, message_pb2.ClientState.Messaging)
    send_message(s, message)

    response = receive_message(s, message_pb2.UserMessageResponse)

    writeStateChange(s, message_pb2.ClientState.UserLoop)

    return response


def receive_user_messages(s, username, sender):
    messageRequest = message_pb2.UserMessageRequest()
    messageRequest.username = username
    messageRequest.sender = sender

    writeStateChange(s, message_pb2.ClientState.Reading)
    send_message(s, messageRequest)
    list = receive_message(s, message_pb2.UserMessageList)

    writeStateChange(s, message_pb2.ClientState.UserLoop)

    return list


def user_loop(s, name):
    while 1:
        command = input("Enter command: ")

        if command == "message":
            title = input("Enter title:")
            recipient = input("Enter recipient:")
            content = input("Enter content:")

            send_user_message(s, name, title, recipient, content)

        if command == "read":
            sender = input("Inbox or Outbox: ")
            if sender != "Inbox" and sender != "Outbox":
                print("input error")

            messages = receive_user_messages(s, name, sender == "Outbox")
            print(messages)

        if command == "quit":
            writeStateChange(s, message_pb2.ClientState.Quit)
            s.close()
            exit()


def acceptStateChange(s):
    new_state = receive_message(s, message_pb2.StateChange)
    return new_state.state


def writeStateChange(s, state):
    stateChange = message_pb2.StateChange()
    stateChange.state = state
    send_message(s, stateChange)


def sendAuthDetails(s, state, username, password):
    authMsg = message_pb2.AuthMessage()
    authMsg.username = username
    authMsg.password = password
    authMsg.state = state

    send_message(s, authMsg)
    response = receive_message(s, message_pb2.AuthResponse)

    return response, username


def authLoop(s, state):
    while 1:
        username = input("Username: ")
        password = input("Password: ")
        response, username = sendAuthDetails(s, state, username, password)

        if response.response == message_pb2.AuthReturn.AuthSuccess:
            print("Auth Suceeded")
            return username
        if response.response == message_pb2.AuthReturn.AuthFailure:
            print("Auth Failed")
            return None
        print("Auth Failed, Retry")


def authenticate(s):
    state: message_pb2.ClientState
    while 1:
        command = input("Login or Signup: ")
        if not (command == "Login" or command == "Signup"):
            print("Invalid Input.")
            continue

        if command == "Login":
            state = message_pb2.ClientState.Login
        elif command == "Signup":
            state = message_pb2.ClientState.Signup

        writeStateChange(s, state)
        username = authLoop(s, state)
        return username


def user_setup(s):
    data = s.recv(1024)
    print(f"Received from server: {data.decode()}")


if __name__ == "__main__":
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect((HOST, PORT))

        user_setup(s)
        username = authenticate(s)
        if username is None:
            exit()

        state = acceptStateChange(s)
        assert state == message_pb2.ClientState.UserLoop

        user_loop(s, username)
