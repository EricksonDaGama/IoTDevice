import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class IoTServer {
    private ExecutorService executorService;
    private AuthenticationService authenticationService;
    private DeviceManager deviceManager;

    public IoTServer() {
        executorService = Executors.newCachedThreadPool();
        authenticationService = new AuthenticationService();
        deviceManager = new DeviceManager();
    }

    public static void main(String[] args) {
        new IoTServer().startServer();
    }

    public void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(23456)) {
            System.out.println("Servidor iniciado.");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                executorService.submit(new ClientHandlerThread(clientSocket, authenticationService, deviceManager));
            }
        } catch (IOException e) {
            System.err.println("Erro ao iniciar o servidor: " + e.getMessage());
        }
    }

    class ClientHandlerThread implements Runnable {
        private Socket clientSocket;
        private AuthenticationService authenticationService;
        private DeviceManager deviceManager;
        private String username;  // Variável para armazenar o nome de usuário

        ClientHandlerThread(Socket socket, AuthenticationService authService, DeviceManager deviceMgr) {
            this.clientSocket = socket;
            this.authenticationService = authService;
            this.deviceManager = deviceMgr;
        }

        @Override
        public void run() {
            try {
                ObjectOutputStream outStream = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream inStream = new ObjectInputStream(clientSocket.getInputStream());

                username = (String) inStream.readObject();
                String password = (String) inStream.readObject();

                String authResponse = authenticationService.handleAuthentication(username, password);
                outStream.writeUTF(authResponse);
                outStream.flush();

                if (!authResponse.equals("OK-USER") && !authResponse.equals("OK-NEW-USER")) {
                    return;
                }

                String devId = (String) inStream.readObject();
                if (deviceManager.isDeviceActive(username, devId)) {
                    outStream.writeUTF("NOK-DEVID");
                } else {
                    deviceManager.registerDevice(username, devId);
                    outStream.writeUTF("OK-DEVID");
                }
                outStream.flush();

            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Erro ao tratar cliente: " + e.getMessage());
            } finally {
                deviceManager.removeActiveSession(username);
                try {
                    if (clientSocket != null) {
                        clientSocket.close();
                    }
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
                System.err.println("Erro ao ler o arquivo de credenciais: " + e.getMessage());
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

    public class DeviceManager {
        private final String DEVICES_FILE = "src/devices.txt";
        private Map<String, String> registeredDevices;
        private Set<String> activeSessions;

        public DeviceManager() {
            registeredDevices = new HashMap<>();
            activeSessions = new HashSet<>();
            loadDeviceMappings();
        }

        public synchronized boolean isDeviceActive(String userId, String devId) {
            return registeredDevices.getOrDefault(userId, "").equals(devId) && activeSessions.contains(userId);
        }

        public synchronized void registerDevice(String userId, String devId) {
            registeredDevices.put(userId, devId);
            activeSessions.add(userId);
            saveDeviceMappings();
        }

        public synchronized void removeActiveSession(String userId) {
            activeSessions.remove(userId);
        }

        private void loadDeviceMappings() {
            try (BufferedReader reader = new BufferedReader(new FileReader(DEVICES_FILE))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        registeredDevices.put(parts[0], parts[1]);
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro ao ler o arquivo de dispositivos: " + e.getMessage());
            }
        }

        private void saveDeviceMappings() {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(DEVICES_FILE))) {
                for (Map.Entry<String, String> entry : registeredDevices.entrySet()) {
                    writer.write(entry.getKey() + ":" + entry.getValue() + "\n");
                }
            } catch (IOException e) {
                System.err.println("Erro ao escrever no arquivo de dispositivos: " + e.getMessage());
            }
        }
    }
}
