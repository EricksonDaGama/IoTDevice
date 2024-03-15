import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IoTServer {
    private ExecutorService executorService;
    private AuthenticationService authenticationService;
    private DeviceManager deviceManager;

    public IoTServer() {
        executorService = Executors.newCachedThreadPool();
        authenticationService = new AuthenticationService();
        deviceManager = new DeviceManager();
        startSessionCleanupThread();
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

    private void startSessionCleanupThread() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(60000); // 60 segundos
                    deviceManager.cleanupExpiredSessions();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    class ClientHandlerThread implements Runnable {
        private Socket clientSocket;
        private AuthenticationService authenticationService;
        private DeviceManager deviceManager;
        private String username;
        private String devId;

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

                boolean authenticated = false;
                while (!authenticated) {
                    username = (String) inStream.readObject();
                    String password = (String) inStream.readObject();

                    String authResponse = authenticationService.handleAuthentication(username, password);
                    long currentTime = System.currentTimeMillis();
                    outStream.writeUTF(authResponse);
                    outStream.writeLong(currentTime);
                    outStream.flush();

                    if (authResponse.equals("OK-USER") || authResponse.equals("OK-NEW-USER")) {
                        authenticated = true;
                    }
                }

                boolean deviceRegistered = false;
                while (!deviceRegistered) {
                    devId = (String) inStream.readObject();
                    boolean isRegistered = deviceManager.registerDevice(username, devId);

                    if (!isRegistered) {
                        outStream.writeUTF("NOK-DEVID");
                    } else {
                        outStream.writeUTF("OK-DEVID");
                        deviceRegistered = true;
                    }
                    outStream.flush();
                }

            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Erro ao tratar cliente: " + e.getMessage());
            } finally {
                if (username != null && devId != null) {
                    deviceManager.removeActiveSession(username, devId);
                }
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
        private Map<String,String> activeSessions;
        private Map<String, Long> activeSessionsTimestamps;
        private static final long TIMEOUT_THRESHOLD = 60000; // 1 minuto


        public DeviceManager() {
            registeredDevices = new HashMap<>();
            activeSessions = new HashMap<>();
            activeSessionsTimestamps = new ConcurrentHashMap<>();
            loadDeviceMappings();
        }

        public synchronized boolean registerDevice(String userId, String devId) {
            String sessionKey = userId + ":" + devId;
            long currentTime = System.currentTimeMillis();

            // Verifica se o dispositivo para este userId já está registrado e ativo
            if (devId.equals(activeSessions.get(userId))) {
                // Se já estiver ativo, nega o registro para evitar sessão simultânea
                System.out.println("Sessão já ativa para este usuário.");
                return false;
            }

            // Registra o dispositivo e a sessão
            registeredDevices.put(userId, devId);
            activeSessions.put(userId,devId); // Armazena apenas o userId para evitar sessões simultâneas
            activeSessionsTimestamps.put(sessionKey, currentTime);
            saveDeviceMappings(userId,devId);
            System.out.println("Dispositivo registrado com sucesso.");
            return true;
        }

        public synchronized void removeActiveSession(String userId, String devId) {
            activeSessions.remove(userId + ":" + devId);
            activeSessionsTimestamps.remove(userId + ":" + devId);
        }

        public void cleanupExpiredSessions() {
            long currentTime = System.currentTimeMillis();
            activeSessionsTimestamps.entrySet().removeIf(entry -> currentTime - entry.getValue() > TIMEOUT_THRESHOLD);
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

        private void saveDeviceMappings(String userId,String deviceId) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(DEVICES_FILE,true))) {
               writer.write(userId + ":" + deviceId + "\n");
               registeredDevices.put(userId,deviceId);
                writer.flush(); // Força a escrita dos dados no arquivo imediatamente
            } catch (IOException e) {
                System.err.println("Erro ao escrever no arquivo de dispositivos: " + e.getMessage());
            }
        }
    }
}
