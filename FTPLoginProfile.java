public class FTPLoginProfile {
    private static final int ftp_port = 21;

    private final String host;
    private final String username;
    private final String password;
    private final String modeLabel;

    public FTPLoginProfile(String host, String username, String password, String modeLabel) {
        this.host = host;
        this.username = username;
        this.password = password;
        this.modeLabel = modeLabel;
    }

    public static FTPLoginProfile anonymous() {
        return new FTPLoginProfile("ftp.gnu.org", "anonymous", "guest@", "Anonymous");
    }

    public static FTPLoginProfile custom() {
        return new FTPLoginProfile("ftp.dlptest.com", "dlpuser", "rNrKYTX9g7z3RgJRmxWuGHbeu", "Custom");
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return ftp_port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getModeLabel() {
        return modeLabel;
    }
}
