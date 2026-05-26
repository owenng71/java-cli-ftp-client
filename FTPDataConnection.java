import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class FTPDataConnection {
    private static final int TIMEOUT = 10000;
    private final String host;
    private final String host2;
    private final int port;

    public FTPDataConnection(String host, int port) {
        this(host, null, port);
    }

    public FTPDataConnection(String host, String host2, int port) {
        this.host = host;
        this.host2 = host2;
        this.port = port;
    }

    public Socket open() throws IOException {
        IOException err = null;

        try {
            return openTo(host);
        } catch (IOException e) {
            err = e;
        }

        if (host2 != null && !host2.equals(host)) {
            return openTo(host2);
        }

        throw err;
    }

    public static FTPDataConnection fromPasvReply(String pasv, String host2) throws IOException {
        int start = pasv.indexOf('(');
        int end = pasv.indexOf(')');
        if (start < 0 || end <= start) {
            throw new IOException("Invalid PASV reply: " + pasv);
        }

        String[] parts = pasv.substring(start + 1, end).split(",");
        if (parts.length != 6) {
            throw new IOException("Invalid PASV reply: " + pasv);
        }

        try {
            String host = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
            int port = Integer.parseInt(parts[4]) * 256 + Integer.parseInt(parts[5]);
            return new FTPDataConnection(host, host2, port);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid PASV numbers: " + pasv, e);
        }
    }

    private Socket openTo(String host) throws IOException {
        Socket sock = new Socket();
        sock.connect(new InetSocketAddress(host, port), TIMEOUT);
        return sock;
    }
}
