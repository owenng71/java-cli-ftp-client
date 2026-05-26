
# Java FTP Client

This project is a command-line FTP client written in Java for a networking assignment. It connects to real public FTP servers and demonstrates understanding of socket programming and the FTP protocol, including control connections, passive data connections, and reply-code driven communication.

## Features

- Connect to a real FTP server on port `21`
- Anonymous login using `ftp.gnu.org`
- Custom login using `ftp.dlptest.com`
- `pwd` to print the current remote directory
- `cd <dir>` to change the remote directory
- `ls` using passive mode (`PASV + LIST`)
- `get <remote> [local]` to download a file
- `put <local> [remote]` to upload a file
- `delete <file>` to delete a remote file
- `mkdir <dir>` to create a remote directory
- `rmdir <dir>` to remove a remote directory
- `raw <FTP_COMMAND>` for direct FTP commands
- `quit` to disconnect cleanly

## Project Architecture

### Main.java
- Entry point of the application
- Creates the FTP client and starts the CLI

### FTPClient.java
- Handles command-line interaction
- Reads and validates user commands
- Delegates FTP work to `FTPConnection`

### FTPConnection.java
- Core FTP protocol engine
- Maintains the control socket
- Sends commands and processes server replies
- Coordinates login, navigation, uploads, and downloads

### FTPDataConnection.java
- Handles passive-mode data connections
- Parses `227 PASV` replies
- Creates temporary sockets for file transfer and listings

### FTPLoginProfile.java
- Stores predefined login profiles

## FTP Design Concept

The key concept of this project is:

**One long-lived control connection + temporary passive data connections**

The control socket remains active throughout the session and handles commands:

- `USER`
- `PASS`
- `PWD`
- `CWD`
- `QUIT`

A passive data socket is opened only when transferring actual data:

- `LIST`
- `RETR`
- `STOR`

Passive transfer sequence:

1. Send `PASV`
2. Receive `227`
3. Parse host and port
4. Open data connection
5. Execute transfer command

## Supported Commands

| Command | Description |
|-----------|-------------|
| `help` | Show command list |
| `pwd` | Show current directory |
| `cd <dir>` | Change remote directory |
| `ls` | List files |
| `get <remote> [local]` | Download file |
| `put <local> [remote]` | Upload file |
| `delete <file>` | Delete file |
| `mkdir <dir>` | Create directory |
| `rmdir <dir>` | Remove directory |
| `raw <FTP_COMMAND>` | Send manual FTP command |
| `quit` | Exit program |

## Build and Run

Compile:

```bat
javac --release 8 -d build *.java
```

Run:

```bat
java -cp build Main
```

Or:

```bat
run.bat
```

## Notes

- `ftp.gnu.org` is mainly used for anonymous login and download tests.
- `ftp.dlptest.com` is useful for authenticated operations and uploads.
- Public FTP servers can be unstable and may reject requests even when client code is correct.
- The `build` directory contains generated `.class` files and can be recreated.

## Integrity Declaration

I confirm that the implementation and submitted source code are my own work and were developed for this assignment. I did not copy code from classmates or online repositories.

Documentation and formatting assistance may have been used during preparation of the project materials, but the program logic and implementation remain my own work.
