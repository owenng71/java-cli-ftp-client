import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class FTPConnection {
    private static final int BUFFER_SIZE = 4096;

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public FTPReply connect(String host, int port) throws IOException {
        close();

        socket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

        return read();
    }

    public FTPReply login(String user, String pass) throws IOException {
        FTPReply reply = send("USER " + user);
        if (reply.getCode() == 230) {
            return reply;
        }

        if (reply.getCode() != 331) {
            return reply;
        }

        return send("PASS " + pass);
    }

    public FTPReply pwd() throws IOException {
        return send("PWD");
    }

    public FTPReply cd(String dir) throws IOException {
        return send("CWD " + dir);
    }

    public FTPReply delete(String file) throws IOException {
        return send("DELE " + file);
    }

    public FTPReply mkdir(String dir) throws IOException {
        return send("MKD " + dir);
    }

    public FTPReply rmdir(String dir) throws IOException {
        return send("RMD " + dir);
    }

    public FTPReply quit() throws IOException {
        return send("QUIT");
    }

    public DataResult ls(String path) throws IOException {
        FTPDataConnection dataCon = openData();
        Socket dataSock = dataCon.open();
        boolean opened = false;

        try {
            String cmd = (path == null || path.trim().isEmpty()) ? "LIST" : "LIST " + path;
            sendCommand(cmd);
            FTPReply pre = read();

            if (!pre.isPositivePreliminary() && !pre.isPositiveCompletion()) {
                return new DataResult(pre, pre, "");
            }

            opened = true;
            String text = readText(dataSock);
            closeSock(dataSock);
            opened = false;

            FTPReply done = pre.isPositiveCompletion()
                ? pre
                : read();
            return new DataResult(pre, done, text);
        } finally {
            if (opened) {
                closeSock(dataSock);
            }
        }
    }

    public TransferResult get(String remote, File local) throws IOException {
        setBinaryMode();
        FTPDataConnection dataCon = openData();
        Socket dataSock = dataCon.open();
        boolean opened = false;
        BufferedInputStream dataIn = null;
        BufferedOutputStream fileOut = null;

        try {
            sendCommand("RETR " + remote);
            FTPReply pre = read();

            if (!pre.isPositivePreliminary() && !pre.isPositiveCompletion()) {
                return new TransferResult(pre, pre, "Download rejected by server.");
            }

            opened = true;
            dataIn = new BufferedInputStream(dataSock.getInputStream());
            fileOut = new BufferedOutputStream(new FileOutputStream(local));

            long bytes = copy(dataIn, fileOut);
            fileOut.flush();
            closeIo(fileOut);
            fileOut = null;
            closeIo(dataIn);
            dataIn = null;
            closeSock(dataSock);
            opened = false;

            FTPReply done = pre.isPositiveCompletion()
                ? pre
                : read();
            String msg = "Downloaded " + bytes + " bytes to " + local.getPath();
            return new TransferResult(pre, done, msg);
        } finally {
            closeIo(fileOut);
            closeIo(dataIn);
            if (opened) {
                closeSock(dataSock);
            }
        }
    }

    public TransferResult put(File local, String remote) throws IOException {
        setBinaryMode();
        FTPDataConnection dataCon = openData();
        Socket dataSock = dataCon.open();
        boolean opened = false;
        BufferedInputStream fileIn = null;
        BufferedOutputStream dataOut = null;

        try {
            sendCommand("STOR " + remote);
            FTPReply pre = read();

            if (!pre.isPositivePreliminary() && !pre.isPositiveCompletion()) {
                return new TransferResult(pre, pre, "Upload rejected by server.");
            }

            opened = true;
            fileIn = new BufferedInputStream(new FileInputStream(local));
            dataOut = new BufferedOutputStream(dataSock.getOutputStream());

            long bytes = copy(fileIn, dataOut);
            dataOut.flush();
            closeIo(dataOut);
            dataOut = null;
            closeIo(fileIn);
            fileIn = null;
            closeSock(dataSock);
            opened = false;

            FTPReply done = pre.isPositiveCompletion()
                ? pre
                : read();
            String msg = "Uploaded " + bytes + " bytes from " + local.getPath();
            return new TransferResult(pre, done, msg);
        } finally {
            closeIo(dataOut);
            closeIo(fileIn);
            if (opened) {
                closeSock(dataSock);
            }
        }
    }

    public void sendCommand(String command) throws IOException {
        ensureConnected();
        writer.write(command);
        writer.write("\r\n");
        writer.flush();
    }

    public FTPReply send(String command) throws IOException {
        sendCommand(command);
        return read();
    }

    public FTPReply read() throws IOException {
        ensureConnected();

        String firstLine = reader.readLine();
        if (firstLine == null) {
            throw new EOFException("Server closed the control connection.");
        }

        StringBuilder message = new StringBuilder(firstLine);
        int code = parseCode(firstLine);

        if (isMultilineReply(firstLine)) {
            String terminator = firstLine.substring(0, 3) + " ";

            while (true) {
                String nextLine = reader.readLine();
                if (nextLine == null) {
                    throw new EOFException("Server closed the connection during a multiline reply.");
                }

                message.append(System.lineSeparator()).append(nextLine);
                if (nextLine.startsWith(terminator)) {
                    break;
                }
            }
        }

        return new FTPReply(code, message.toString());
    }

    public void close() throws IOException {
        IOException err = null;

        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                err = e;
            } finally {
                reader = null;
            }
        }

        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                if (err == null) {
                    err = e;
                }
            } finally {
                writer = null;
            }
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                if (err == null) {
                    err = e;
                }
            } finally {
                socket = null;
            }
        }

        if (err != null) {
            throw err;
        }
    }

    private FTPDataConnection openData() throws IOException {
        FTPReply pasv = send("PASV");
        if (!pasv.isPositiveCompletion()) {
            throw new IOException("PASV failed: " + pasv.getMessage());
        }
        String host2 = socket.getInetAddress().getHostAddress();
        return FTPDataConnection.fromPasvReply(pasv.getMessage(), host2);
    }

    private void setBinaryMode() throws IOException {
        FTPReply type = send("TYPE I");
        if (!type.isPositiveCompletion()) {
            throw new IOException("Failed to switch to binary mode: " + type.getMessage());
        }
    }

    private String readText(Socket dataSock) throws IOException {
        BufferedInputStream input = null;
        try {
            input = new BufferedInputStream(dataSock.getInputStream());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[BUFFER_SIZE];
            int n;
            while ((n = input.read(buffer)) != -1) {
                output.write(buffer, 0, n);
            }
            return output.toString("UTF-8").trim();
        } finally {
            closeIo(input);
        }
    }

    private long copy(InputStream input, java.io.OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int n;
        long total = 0;

        while ((n = input.read(buffer)) != -1) {
            output.write(buffer, 0, n);
            total += n;
        }

        return total;
    }

    private void ensureConnected() throws IOException {
        if (socket == null || reader == null || writer == null || socket.isClosed()) {
            throw new IOException("Not connected to an FTP server.");
        }
    }

    private int parseCode(String line) throws IOException {
        if (line.length() < 3) {
            throw new IOException("Invalid FTP reply: " + line);
        }

        try {
            return Integer.parseInt(line.substring(0, 3));
        } catch (NumberFormatException e) {
            throw new IOException("Invalid FTP reply code: " + line, e);
        }
    }

    private boolean isMultilineReply(String line) {
        return line.length() > 3 && line.charAt(3) == '-';
    }

    private void closeSock(Socket sock) {
        if (sock != null) {
            try {
                sock.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void closeIo(java.io.Closeable io) {
        if (io != null) {
            try {
                io.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static class FTPReply {
        private final int code;
        private final String message;

        public FTPReply(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public boolean isPositiveCompletion() {
            return code >= 200 && code < 300;
        }

        public boolean isPositivePreliminary() {
            return code >= 100 && code < 200;
        }
    }

    public static class DataResult {
        private final FTPReply pre;
        private final FTPReply done;
        private final String text;

        public DataResult(FTPReply pre, FTPReply done, String text) {
            this.pre = pre;
            this.done = done;
            this.text = text;
        }

        public FTPReply getPre() {
            return pre;
        }

        public FTPReply getDone() {
            return done;
        }

        public String getText() {
            return text;
        }
    }

    public static class TransferResult {
        private final FTPReply pre;
        private final FTPReply done;
        private final String msg;

        public TransferResult(FTPReply pre, FTPReply done, String msg) {
            this.pre = pre;
            this.done = done;
            this.msg = msg;
        }

        public FTPReply getPre() {
            return pre;
        }

        public FTPReply getDone() {
            return done;
        }

        public String getMsg() {
            return msg;
        }
    }
}
