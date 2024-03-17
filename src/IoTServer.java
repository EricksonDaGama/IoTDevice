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
            System.out.println("Cliente  conexão de cliente recebida:  " + socket.getInetAddress());
        }
        @Override
        public void run() {
            try {
                ObjectOutputStream outStream = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream inStream = new ObjectInputStream(clientSocket.getInputStream());

                //1-recebendo dados do cliente
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
                        System.out.printf("Cliente %s iniciou secção \n",username);
                        authenticated = true;
                    }
                }
                //2-recebendo dados do device-id
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
                //3-Recebendo dados do executável
                String nomeArquivo = inStream.readUTF();
                long tamanhoArquivo = inStream.readLong();

                if (validarTamanhoExecutavel(nomeArquivo, tamanhoArquivo)) {
                    outStream.writeUTF("OK-TESTED");
                } else {
                    outStream.writeUTF("NOK-TESTED");
                    //return;
                }
                outStream.flush();

                //4- recebendo o comando que o cliente quer
                while (!clientSocket.isClosed()) {
                    try {
                        String comando = inStream.readUTF();
                        String resposta = processarComando(comando);
                        outStream.writeUTF(resposta);
                        outStream.flush();
                    } catch (IOException e) {
                        System.err.println("Erro ao ler comando: " + e.getMessage());
                        break;  // Sair do loop em caso de erro
                    }
                }

            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Erro ao tratar cliente: " + e.getMessage());
                // Tratar a exceção conforme necessário
            } finally {
                // A lógica de limpeza e fechamento do socket
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
        private String processarComando(String comando) {
            // Analise o comando e execute a ação correspondente
            // Por exemplo, se o comando for "CREATE <dm>", crie um novo domínio
            // Retorne uma resposta baseada no resultado da ação
            // Exemplo: return "Domínio criado com sucesso";

            String[] parts = comando.split(" ");
            if (parts.length != 2) {
                return "Formato de Comando Inválido";
            }

            if (parts[0].equalsIgnoreCase("CREATE")) {
                return criarDominio(parts[1]);
            }


            if (parts[0].equalsIgnoreCase("ADD")) {
                return "Funcionalidade ADD Não Implementada";
            }

            if (parts[0].equalsIgnoreCase("RD")) {
                return "Funcionalidade ADD Não Implementada";
            }
            if (parts[0].equalsIgnoreCase("ET")) {
                return "Funcionalidade ADD Não Implementada";
            }
            if (parts[0].equalsIgnoreCase("EI")) {
                return "Funcionalidade ADD Não Implementada";
            }

            if (parts[0].equalsIgnoreCase("RT")) {
                return "Funcionalidade ADD Não Implementada";
            }
            if (parts[0].equalsIgnoreCase("RI")) {
                return "Funcionalidade ADD Não Implementada";
            }


            return "Comando Desconhecido";

        }


        private String criarDominio(String nomeDominio) {
            // Implementar lógica para criar um domínio
            synchronized (this) { // Sincronizar o acesso ao arquivo
                try {
                    // Verificar se o domínio já existe
                    if (dominioExiste(nomeDominio)) {
                        return "NOK";  // Domínio já existe
                    }

                    // Adicionar o novo domínio ao arquivo
                    try (FileWriter fw = new FileWriter("src/dominios.txt", true);
                         BufferedWriter bw = new BufferedWriter(fw)) {
                        bw.write("\nDomínio: " + nomeDominio + "\nOwner: " + username + "\nUsuários com permissão: " + username + "\nDispositivos registrados: \n");
                    }
                    return "OK";  // Domínio criado com sucesso
                } catch (IOException e) {
                    System.err.println("Erro ao acessar o arquivo de domínios: " + e.getMessage());
                    return "Erro ao criar o domínio";
                }
            }
        }

        private boolean dominioExiste(String nomeDominio) throws IOException {
            try (BufferedReader br = new BufferedReader(new FileReader("src/dominios.txt"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("Domínio: " + nomeDominio)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean validarTamanhoExecutavel(String nomeArquivo, long tamanhoArquivo) {
            try {
                File file = new File("src/executavel.txt");
                System.out.println("tamanho que o cliente diz "+tamanhoArquivo);
                System.out.println("o nome do arquivo é "+nomeArquivo);

                Scanner scanner = new Scanner(file);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    String[] parts = line.split(":");
                    if (parts[0].equals(nomeArquivo)) {
                        long tamanhoEsperado = Long.parseLong(parts[1]);
                        System.out.println("tamanho esperado "+tamanhoEsperado);
                        return tamanhoArquivo == tamanhoEsperado;
                    }
                }
            } catch (FileNotFoundException | NumberFormatException e) {
                System.err.println("Erro ao validar tamanho do arquivo: " + e.getMessage());
            }
            return false;
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
        private Map<String, String> registeredDevices;
        private Map<String,String> activeSessions;
        private Map<String, Long> activeSessionsTimestamps;
        private static final long TIMEOUT_THRESHOLD = 60000; // 1 minuto
        public DeviceManager() {
            registeredDevices = new HashMap<>();
            activeSessions = new HashMap<>();
            activeSessionsTimestamps = new ConcurrentHashMap<>();
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
    }
}
