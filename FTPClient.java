import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class FTPClient {
    public void run() {
        Scanner scan = new Scanner(System.in);
        FTPConnection con = new FTPConnection();

        try {
            FTPLoginProfile profile = chooseProfile(scan);
            connect(con, profile);
            shell(scan, con);
        } catch (IOException e) {
            System.out.println("FTP error: " + e.getMessage());
        } finally {
            closeSafe(con);
        }
    }

    private FTPLoginProfile chooseProfile(Scanner scan) {
        while (true) {
            System.out.println("Choose login mode:");
            System.out.println("1. Anonymous");
            System.out.println("2. Custom");
            System.out.print("Enter choice: ");

            String choice = scan.nextLine().trim();
            if ("1".equals(choice)) {
                return FTPLoginProfile.anonymous();
            }

            if ("2".equals(choice)) {
                return FTPLoginProfile.custom();
            }

            System.out.println("Invalid choice. Please enter 1 or 2.");
        }
    }

    private void connect(FTPConnection con, FTPLoginProfile profile) throws IOException {
        System.out.println("Selected mode: " + profile.getModeLabel());
        System.out.println("Connecting to " + profile.getHost() + ":" + profile.getPort() + "...");
        FTPConnection.FTPReply welcome = con.connect(profile.getHost(), profile.getPort());
        printReply(welcome);

        FTPConnection.FTPReply login = con.login(profile.getUsername(), profile.getPassword());
        printReply(login);

        if (!login.isPositiveCompletion()) {
            throw new IOException("Login failed. The server rejected the provided credentials.");
        }

        System.out.println("Login successful.");
        System.out.println("Type \"help\" to see available commands.");
    }

    private void shell(Scanner scan, FTPConnection con) {
        while (true) {
            System.out.print("ftp> ");
            if (!scan.hasNextLine()) {
                break;
            }

            String input = scan.nextLine().trim();
            if (input.isEmpty()) {
                continue;
            }

            try {
                if ("help".equalsIgnoreCase(input)) {
                    printHelp();
                    continue;
                }

                if ("pwd".equalsIgnoreCase(input)) {
                    printReply(con.pwd());
                    continue;
                }

                if ("quit".equalsIgnoreCase(input)) {
                    printReply(con.quit());
                    break;
                }

                if ("ls".equalsIgnoreCase(input)) {
                    printData(con.ls(null));
                    continue;
                }

                if (hasCmd(input, "cd")) {
                    String dir = needArg(input, "cd");
                    printReply(con.cd(dir));
                    continue;
                }

                if (hasCmd(input, "delete")) {
                    String file = needArg(input, "delete");
                    printReply(con.delete(file));
                    continue;
                }

                if (hasCmd(input, "mkdir")) {
                    String dir = needArg(input, "mkdir");
                    printReply(con.mkdir(dir));
                    continue;
                }

                if (hasCmd(input, "rmdir")) {
                    String dir = needArg(input, "rmdir");
                    printReply(con.rmdir(dir));
                    continue;
                }

                if (hasCmd(input, "raw")) {
                    String cmd = needArg(input, "raw");
                    printReply(con.send(cmd));
                    continue;
                }

                if (hasCmd(input, "get")) {
                    get(con, input);
                    continue;
                }

                if (hasCmd(input, "put")) {
                    put(con, input);
                    continue;
                }

                System.out.println("Unknown command. Use \"help\" to see available commands.");
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            } catch (IOException e) {
                System.out.println("FTP error: " + e.getMessage());
            }
        }
    }

    private void get(FTPConnection con, String input) throws IOException {
        String[] parts = splitArgs(input, 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Usage: get <remote-file> [local-file]");
        }

        String remote = parts[1];
        String local = parts.length >= 3 ? parts[2] : nameOf(remote);
        FTPConnection.TransferResult result = con.get(remote, new File(local));
        printTransfer(result);
    }

    private void put(FTPConnection con, String input) throws IOException {
        String[] parts = splitArgs(input, 3);
        if (parts.length < 2) {
            throw new IllegalArgumentException("Usage: put <local-file> [remote-file]");
        }

        File local = new File(parts[1]);
        if (!local.isFile()) {
            throw new IllegalArgumentException("Local file not found: " + local.getPath());
        }

        String remote = parts.length >= 3 ? parts[2] : local.getName();
        FTPConnection.TransferResult result = con.put(local, remote);
        printTransfer(result);
    }

    private String needArg(String input, String cmd) {
        String[] parts = splitArgs(input, 2);
        if (parts.length < 2 || parts[1].trim().isEmpty()) {
            throw new IllegalArgumentException("Usage: " + cmd + " <argument>");
        }
        return parts[1].trim();
    }

    private String[] splitArgs(String input, int limit) {
        return input.trim().split("\\s+", limit);
    }

    private boolean hasCmd(String input, String cmd) {
        return input.equalsIgnoreCase(cmd)
            || input.regionMatches(true, 0, cmd + " ", 0, cmd.length() + 1);
    }

    private String nameOf(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < path.length() - 1) {
            return path.substring(lastSlash + 1);
        }
        return path;
    }

    private void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  pwd                         - print the current remote directory");
        System.out.println("  cd <dir>                    - change the current remote directory");
        System.out.println("  ls                          - list files in the current remote directory");
        System.out.println("  get <remote> [local]        - download a remote file");
        System.out.println("  put <local> [remote]        - upload a local file");
        System.out.println("  delete <file>               - delete a remote file");
        System.out.println("  mkdir <dir>                 - create a remote directory");
        System.out.println("  rmdir <dir>                 - remove a remote directory");
        System.out.println("  raw <FTP_COMMAND>           - send a raw FTP command");
        System.out.println("  quit                        - disconnect and exit");
    }

    private void printReply(FTPConnection.FTPReply reply) {
        System.out.println(reply.getMessage());
    }

    private void printData(FTPConnection.DataResult result) {
        printReply(result.getPre());
        String text = result.getText();
        if (text != null && !text.isEmpty()) {
            System.out.println(text);
        }
        printReply(result.getDone());
    }

    private void printTransfer(FTPConnection.TransferResult result) {
        printReply(result.getPre());
        System.out.println(result.getMsg());
        printReply(result.getDone());
    }

    private void closeSafe(FTPConnection con) {
        try {
            con.close();
        } catch (IOException e) {
            System.out.println("Cleanup error: " + e.getMessage());
        }
    }
}
