package src.server;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.IOException;
import java.net.Socket;

public class IoTServer {
    public static final ManagerSever SERVER_MANAGER = ManagerSever
        .getInstance();
    public static final AuthenticationService SERVER_AUTH = AuthenticationService.getInstance();

    private static final int ARG_NUM = 5;
    private static final int DEFAULT_PORT = 12345;

    public static void main(String[] args) {
        int portArg = DEFAULT_PORT;
        String usersCypherPwdArg = null;
        String keystorePathArg = null;
        String keystorePwdArg = null;
        String apiKeyArg = null;

        if (args.length == ARG_NUM) {
            portArg = Integer.parseInt(args[0]);
            usersCypherPwdArg = args[1];
            keystorePathArg = args[2];
            keystorePwdArg = args[3];
            apiKeyArg = args[4];
            System.out.println("IoTServer runs with port: " + portArg);
        } else if (args.length == ARG_NUM - 1) {
            usersCypherPwdArg = args[0];
            keystorePathArg = args[1];
            keystorePwdArg = args[2];
            apiKeyArg = args[3];
            System.out.println("IoTServer runs with default port: 12345");
        } else {
            System.err.println("Usage: IoTServer <port> <password-cifra> <keystore> <password-keystore> <2FA-APIKey>\n Please try again.");
            System.exit(-1);
        }

        AuthenticationService.setApiKey(apiKeyArg);

        System.setProperty("javax.net.ssl.keyStore", keystorePathArg);
        System.setProperty("javax.net.ssl.keyStorePassword", keystorePwdArg);
        System.setProperty("javax.net.ssl.keyStoreType", "JCEKS");

        //TODO Add users' file password cypher to server manager

        IoTServer server = new IoTServer(portArg,
                keystorePathArg, keystorePwdArg, apiKeyArg);
        server.startServer();
    }

    private int port;
    private String keystorePath;
    private String keystorePwd;
    private String apiKey;
    private SSLServerSocket socket;

    public IoTServer(int port, String keystorePath, String keystorePwd,
            String apiKey) {
        this.port = port;
        this.keystorePath = keystorePath;
        this.keystorePwd = keystorePwd;
        this.apiKey = apiKey;
        this.socket = null;
    }

    public void startServer() {
        ServerSocketFactory ssFactory = SSLServerSocketFactory.getDefault();

        try {
            socket = (SSLServerSocket) ssFactory.createServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

        while (true) {
            try{
                Socket connection = socket.accept();
                ClientHandlerThread thread = new ClientHandlerThread(connection, keystorePath,
                        keystorePwd, apiKey);
                thread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
