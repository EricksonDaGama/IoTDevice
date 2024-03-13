import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class IoTServer {
    private ExecutorService executorService;
    private AuthenticationService authenticationService;

    public IoTServer() {
        executorService = Executors.newCachedThreadPool();
        authenticationService = new AuthenticationService();
    }

    public static void main(String[] args) {
        new IoTServer().startServer();
    }

    public void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(23456)) {
            System.out.println("Servidor iniciado.");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(new ClientHandlerThread(clientSocket, authenticationService));
            }
        } catch (IOException e) {
            System.err.println("Erro ao iniciar o servidor: " + e.getMessage());
        }
    }

    class ClientHandlerThread implements Runnable {
        private Socket clientSocket;
        private AuthenticationService authenticationService;

        ClientHandlerThread(Socket socket, AuthenticationService authService) {
            this.clientSocket = socket;
            this.authenticationService = authService;
        }

        @Override
        public void run() {
            try (ObjectOutputStream outStream = new ObjectOutputStream(clientSocket.getOutputStream());
                 ObjectInputStream inStream = new ObjectInputStream(clientSocket.getInputStream())) {

                String username = (String) inStream.readObject();
                String password = (String) inStream.readObject();

                String response = authenticationService.handleAuthentication(username, password);
                outStream.writeUTF(response);
                outStream.flush();

            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Erro ao tratar cliente: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Erro ao fechar socket: " + e.getMessage());
                }
            }
        }
    }

    public class AuthenticationService {
        private final String USERS_FILE = "src/users.txt";
        private Map<String, String> userCredentials;

        public AuthenticationService() {
            userCredentials = new HashMap<>();
            loadUserCredentials();
        }

        public String handleAuthentication(String username, String password) {
            String storedPassword = userCredentials.get(username);
            if (storedPassword != null) {
                if (storedPassword.equals(password)) {
                    return "OK-USER";
                } else {
                    return "WRONG-PWD";
                }
            } else {
                saveNewUser(username, password);
                return "OK-NEW-USER";
            }
        }

        private void loadUserCredentials() {
            try (BufferedReader reader = new BufferedReader(new FileReader(USERS_FILE))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        userCredentials.put(parts[0], parts[1]);
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro ao ler credenciais: " + e.getMessage());
            }
        }

        private void saveNewUser(String username, String password) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(USERS_FILE, true))) {
                writer.write(username + ":" + password + "\n");
                userCredentials.put(username, password);
            } catch (IOException e) {
                System.err.println("Erro ao salvar novo usuário: " + e.getMessage());
            }
        }
    }


}
