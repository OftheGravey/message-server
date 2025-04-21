import os
import sys

sys.path.append(os.getcwd())
sys.path.append(os.getcwd() + "/src")
from src.client import (
    user_setup,
    receive_user_messages,
    sendAuthDetails,
    writeStateChange,
    acceptStateChange,
    send_user_message,
)
import pytest
import socket
from main.protobuf import message_pb2

HOST = "localhost"
PORT = 6123


def setup_socket():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.connect((HOST, PORT))
    return s


def login_or_signin(username, password):
    s = setup_socket()
    try:
        user_setup(s)

        writeStateChange(s, message_pb2.ClientState.Signup)
        response, username = sendAuthDetails(
            s, message_pb2.ClientState.Signup, username, password
        )
        assert response.response == message_pb2.AuthReturn.AuthSuccess
    except:
        s.close()
        s = setup_socket()
        user_setup(s)

        writeStateChange(s, message_pb2.ClientState.Login)
        response, username = sendAuthDetails(
            s, message_pb2.ClientState.Login, username, password
        )
        assert response.response == message_pb2.AuthReturn.AuthSuccess
    return s


def test_create_auth():
    USER_A_USERNAME = "test-a"
    USER_A_PASSWORD = "test"

    # create user
    s = setup_socket()
    user_setup(s)

    writeStateChange(s, message_pb2.ClientState.Signup)
    response, username = sendAuthDetails(
        s, message_pb2.ClientState.Signup, USER_A_USERNAME, USER_A_PASSWORD
    )
    s.close()

    # relog user
    s = setup_socket()
    user_setup(s)

    writeStateChange(s, message_pb2.ClientState.Login)
    response, username = sendAuthDetails(
        s, message_pb2.ClientState.Login, USER_A_USERNAME, USER_A_PASSWORD
    )
    assert response.response == message_pb2.AuthReturn.AuthSuccess

    s.close()


def test_message_creation():
    USER_B_USERNAME = "test-b"
    USER_B_PASSWORD = "test"
    USER_C_USERNAME = "test-c"
    USER_C_PASSWORD = "test-c"

    # user b
    s = login_or_signin(USER_B_USERNAME, USER_B_PASSWORD)
    state = acceptStateChange(s)
    assert state == message_pb2.ClientState.UserLoop

    USER_B_MSGS_SENT_TO_C = 3
    for i in range(USER_B_MSGS_SENT_TO_C):
        response = send_user_message(
            s, USER_B_USERNAME, "TestA", USER_C_USERNAME, "TestA"
        )
        assert response.status == message_pb2.UserMessageResponseStatus.MsgSuccess

    response = receive_user_messages(s, USER_B_USERNAME, sender=True)
    assert len(response.list) == USER_B_MSGS_SENT_TO_C
    response = receive_user_messages(s, USER_B_USERNAME, sender=False)
    assert len(response.list) == 0

    # Trying to send as different user
    response = send_user_message(s, USER_C_USERNAME, "TestA", USER_B_USERNAME, "TestA")
    assert response.status == message_pb2.UserMessageResponseStatus.MsgFailure

    # Trying to send as different user
    response = receive_user_messages(s, USER_C_USERNAME, sender=False)
    assert len(response.list) == 0

    s.close()

    # user c
    s = login_or_signin(USER_C_USERNAME, USER_C_PASSWORD)
    state = acceptStateChange(s)
    assert state == message_pb2.ClientState.UserLoop

    response = receive_user_messages(s, USER_C_USERNAME, False)
    assert len(response.list) == USER_B_MSGS_SENT_TO_C

    for msg in response.list:
        assert msg.sender == USER_B_USERNAME
