# Scala-Python Messaging Client-Server
A socket programming project to send, receive and store messages from clients.

## Description
This repo includes both the server and client code. \
The server is written in Scala. It is responsible for authenticating clients, receiving messages and sending back messages. \
The client is written in Python. It is responsible for accepting CLI inputs from a user. \
Communications between the server and client are defined in Protobuf to maintain consistency between technologies. \
The server uses SQLite to store credentials and user messages. \
Configured to run over localhost. \

## Usage

### Requirements for Server and Client
Compile protobuf protocols via
```bash
compile-proto.sh
```

### Server Requirements
Requires the JVM and Scala to run. Installation guides [here](<https://www.scala-lang.org/download/>) \
Requires `server.env` variables to be set. The tenant variable can be changed to create seperate database

The server can be started via
```bash
sbt run
```

### Client Requirements
Requires Python and UV, or alternatively any package manager that can read `pyproject.toml`. UV can be found [here](https://docs.astral.sh/uv/getting-started/installation/)

The client can be run via
```bash
uv sync # setups packages
uv run src/client.py
```
Or alternatively
```bash
python3 src/client.py
```

## Testing
This project has system tests for the server which can be run by
```bash
sbt test
```

This project has a mock client which can be run via (requires server to be running)
```bash
pytest -q src/test/testClient.py
```
NOTE: For the mock client it is recommended to change the server tenant from prod to the branch slug.

## Future Development
* TLS implementation for messages. They are currently sent and received as plaintext values which can be read in transit.
* Improved client UX. Currently the user experience on the client side is poor, exact word matching for inputs.
* Deleting messages. Messages can not be deleted from either the user-flow or the server side without ad-hoc queries against the database.
* Remove static message limit. Currently it is only possible for the client to read 3 messages from the server due to protocol limitations.
