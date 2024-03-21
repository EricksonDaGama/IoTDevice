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

            if (parts[0].equalsIgnoreCase("CREATE")) {
                if (parts.length != 2) {
                    return "Formato de Comando Inválido";
                }
                return criarDominio(parts[1]);
            }


            if (parts[0].equalsIgnoreCase("ADD")) {
                if (parts.length != 3) {
                    return "Formato de Comando Inválido";
                }

                return adicionarUsuarioAoDominio(parts[1], parts[2]);
            }

            if (parts[0].equalsIgnoreCase("RD")) {
                if (parts.length != 2) {
                    return "Formato de Comando Inválido";
                }
                return registrarDispositivoNoDominio(parts[1]);

            }
            if (parts[0].equalsIgnoreCase("ET")) {
                return handleTemperatureUpdate(parts);
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

        private String handleTemperatureUpdate(String[] partes) {
            if (partes.length != 2) {
                return "NOK"; // Formato de comando inválido
            }
            try {
                float temperatura = Float.parseFloat(partes[1]);
                if (deviceManager.isDeviceRegistered(username, devId)) {
                    return deviceManager.updateDeviceTemperature(username, devId, temperatura) ? "OK" : "NOK";
                }
            } catch (NumberFormatException e) {
                return "NOK"; // Formato de temperatura inválido
            }
            return "NOK"; // Dispositivo não registrado ou outro erro
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




        private String adicionarUsuarioAoDominio(String user1, String dm) {
            synchronized (this) { // Sincronizar o acesso ao arquivo
                try {
                    // Verificar se o domínio existe
                    if (!dominioExiste(dm)) {
                        return "NODM";  // Domínio não existe
                    }

                    // Verificar se o solicitante é o Owner do domínio
                    if (!isOwner(dm, username)) {
                        return "NOPERM";  // Sem permissão
                    }

                    // Verificar se o usuário a ser adicionado existe
                    if (!authenticationService.userExists(user1)) {
                        return "NOUSER";  // Usuário não existe
                    }

                    // Adicionar usuário ao domínio
                    adicionarUsuarioAoArquivo(user1, dm);
                    return "OK";  // Usuário adicionado com sucesso
                } catch (IOException e) {
                    System.err.println("Erro ao acessar o arquivo de domínios: " + e.getMessage());
                    return "Erro ao adicionar usuário";
                }
            }
        }


        private boolean isOwner(String dominio, String usuario) throws IOException {
            try (BufferedReader br = new BufferedReader(new FileReader("src/dominios.txt"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("Domínio: " + dominio)) {
                        // Ler a próxima linha para obter o Owner
                        if ((line = br.readLine()) != null && line.contains("Owner: " + usuario)) {
                            return true;
                        }
                        break;
                    }
                }
            }
            return false;
        }


        //Método para Adicionar Usuário ao Arquivo de Domínios:
        private void adicionarUsuarioAoArquivo(String user1, String dm) throws IOException {
            List<String> fileContent = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader("src/dominios.txt"))) {
                String line;
                boolean found = false;
                while ((line = br.readLine()) != null) {
                    if (line.contains("Domínio: " + dm)) {
                        found = true;
                        fileContent.add(line);
                        fileContent.add(br.readLine()); // Adiciona a linha do Owner
                        String usuarios = br.readLine();
                        if (!usuarios.contains(user1)) {
                            usuarios += ", " + user1;
                        }
                        fileContent.add(usuarios); // Adiciona usuários atualizados
                        continue;
                    }
                    if (found && line.isEmpty()) {
                        found = false;
                    }
                    if (!found) {
                        fileContent.add(line);
                    }
                }
            }

            // Reescrever o arquivo com o conteúdo atualizado
            try (BufferedWriter bw = new BufferedWriter(new FileWriter("src/dominios.txt"))) {
                for (String line : fileContent) {
                    bw.write(line);
                    bw.newLine();
                }
            }
        }


        private String registrarDispositivoNoDominio(String dm) {
            synchronized (this) {
                try {
                    if (!dominioExiste(dm)) {
                        return "NODM";  // O domínio não existe
                    }
                    if (!isUserAllowed(dm, username)) {
                        return "NOPERM";  // O usuário não tem permissão
                    }
                    registrarDispositivoNoArquivo(dm);
                    return "OK";
                } catch (IOException e) {
                    System.err.println("Erro ao acessar o arquivo de domínios: " + e.getMessage());
                    return "Erro ao registrar dispositivo";
                }
            }
        }

        /**
         * erifica se um usuário tem permissão para registrar um dispositivo em um domínio específico.
         * Para isso, ela lê o arquivo dominios.txt e verifica se o
         * usuário está listado como tendo permissão para esse domínio.
         * @param dm
         * @param username
         * @return
         * @throws IOException
         */
        private boolean isUserAllowed(String dm, String username) throws IOException {
            try (BufferedReader br = new BufferedReader(new FileReader("src/dominios.txt"))) {
                String line;
                boolean isCurrentDomain = false;
                while ((line = br.readLine()) != null) {
                    if (line.contains("Domínio: " + dm)) {
                        isCurrentDomain = true;
                    } else if (isCurrentDomain && line.contains("Usuários com permissão:")) {
                        return line.contains(username);
                    } else if (line.startsWith("Domínio:")) {
                        isCurrentDomain = false;
                    }
                }
            }
            return false; // Retorna falso se o domínio não foi encontrado ou o usuário não tem permissão
        }


        private void registrarDispositivoNoArquivo(String dm) throws IOException {
            // A lógica abaixo é simplificada e pode precisar ser adaptada para o seu caso específico
            List<String> lines = new ArrayList<>();
            String dispositivo = username + ":" + devId;

            try (BufferedReader br = new BufferedReader(new FileReader("src/dominios.txt"))) {
                String line;
                boolean foundDomain = false;
                while ((line = br.readLine()) != null) {
                    if (line.contains("Domínio: " + dm)) {
                        foundDomain = true;
                    }
                    if (foundDomain && line.startsWith("Dispositivos registrados:")) {
                        line += " " + dispositivo; // Adiciona o dispositivo na linha atual
                        foundDomain = false; // Evita adicionar o dispositivo em linhas subsequentes
                    }
                    lines.add(line);
                }
            }

            try (BufferedWriter bw = new BufferedWriter(new FileWriter("src/dominios.txt"))) {
                for (String line : lines) {
                    bw.write(line);
                    bw.newLine();
                }
            }
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


        //metodo utilizado quando se adiciona um usuario a um dominio.
        public boolean userExists(String username) {
            return userCredentials.containsKey(username);
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

        public synchronized boolean updateDeviceTemperature(String username, String devId, float temperatura) {
            Map<String, String> devices = loadDevices();
            String dispositivoKey = username + ":" + devId;

            devices.put(dispositivoKey, "Última Temperatura: " + temperatura + "°C");

            return saveDevices(devices);
        }


        private Map<String, String> loadDevices() {
            Map<String, String> devices = new HashMap<>();
            try (BufferedReader reader = new BufferedReader(new FileReader("src/devices.txt"))) {
                String line;
                String currentDevice = null;

                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("Dispositivo:")) {
                        currentDevice = line.substring(line.indexOf(':') + 1).trim();
                    } else if (line.trim().startsWith("Última Temperatura:") && currentDevice != null) {
                        devices.put(currentDevice, line.substring(line.indexOf(':') + 1).trim());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return devices;
        }

        private boolean saveDevices(Map<String, String> devices) {
            // Lista para armazenar todas as linhas do arquivo
            List<String> fileContent = new ArrayList<>();

            try (BufferedReader br = new BufferedReader(new FileReader("src/devices.txt"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    fileContent.add(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }

            // Atualizar a lista com as novas temperaturas dos dispositivos
            for (Map.Entry<String, String> entry : devices.entrySet()) {
                String dispositivoKey = "Dispositivo: " + entry.getKey();
                boolean found = false;

                for (int i = 0; i < fileContent.size(); i++) {
                    if (fileContent.get(i).equals(dispositivoKey)) {
                        // Se encontrou o dispositivo, atualiza a próxima linha com a temperatura
                        if (i + 1 < fileContent.size()) {
                            fileContent.set(i + 1, entry.getValue());
                        } else {
                            fileContent.add(entry.getValue());
                        }
                        found = true;
                        break;
                    }
                }

                // Se o dispositivo não foi encontrado, adicione-o ao final do arquivo
                if (!found) {
                    fileContent.add(dispositivoKey);
                    fileContent.add(entry.getValue());
                }
            }

            // Escrever todas as linhas de volta no arquivo
            try (BufferedWriter bw = new BufferedWriter(new FileWriter("src/devices.txt"))) {
                for (String fileLine : fileContent) {
                    bw.write(fileLine);
                    bw.newLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }

            return true;
        }


        public synchronized boolean isDeviceRegistered(String username, String devId) {
            String dispositivoKey = username + ":" + devId;
            try (BufferedReader reader = new BufferedReader(new FileReader("src/dominios.txt"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("Dispositivos registrados:")) {
                        if (line.contains(dispositivoKey)) {
                            return true;
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

    }
}