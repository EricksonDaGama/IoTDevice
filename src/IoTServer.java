import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
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
                        String resposta = processarComando(comando,inStream,outStream);

                        System.out.println("agora vou enviar a resposta ao cliente sobre a imagem a resposta sera"+ resposta);
                        outStream.writeUTF(resposta);
                        outStream.flush();
                        System.out.println("enviei a resposta ");
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
        private String processarComando(String comando,ObjectInputStream inStream,ObjectOutputStream outStream) throws IOException {
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
                return handleImageUpload(parts,inStream);

            }

            if (parts[0].equalsIgnoreCase("RT")) {
                if (parts.length != 2) {
                    return "Formato de Comando Inválido";
                }
                return handleTemperatureReadRequest(parts[1], outStream);
            }
            if (parts[0].equalsIgnoreCase("RI")) {
                return handleImageRequest(parts,outStream);
            }
            return "Comando Desconhecido";
        }
        private String handleImageRequest(String[] parts, ObjectOutputStream outStream) throws IOException {
            if (parts.length != 2) {
                return "Formato de Comando Inválido";
            }
            String dispositivo = parts[1];
            String[] userName_idDevice=  dispositivo.split(":");
            if(!deviceManager.isDeviceRegistered(userName_idDevice[0],userName_idDevice[1])) {
                return "NOID # esse device id não existe";

            }
            Set<String> dominios=deviceManager.dominiosOfDevice(dispositivo);
            System.out.println("216");
            boolean userPermissao=false;
            for(String d : dominios){
                if (isUserAllowed(d,username)) {
                    userPermissao = true;
                }
            }
            if(!userPermissao) {
                return "NOPERM # sem permissões de leitura";
            }
            Map<String,DeviceData> devices=deviceManager.loadDevices();
            if(!devices.containsKey(dispositivo)) {
                return "NODATA # esse device id não publicou dados";
            }

            if(devices.get(dispositivo).imagem.length()==0) {
                return "NODATA # esse device id não publicou dados";

            }
            String fileName=devices.get(dispositivo).imagem;
            return sendImageToclient(fileName,outStream);

        }
        private String handleTemperatureReadRequest(String domain, ObjectOutputStream outStream) throws IOException {
            if (!dominioExiste(domain)) {
                return "NODM # esse domínio não existe";
            }

            if (!isUserAllowed(domain, username)) {
                return "NOPERM # sem permissões de leitura";
            }

            List<String> temperatureData = collectTemperatureData(domain);
            if (temperatureData.isEmpty()) {
                return "NODATA";
            }

            Path tempFile = createTempFileWithData(temperatureData);
            sendFileToClient(tempFile, outStream);
            Files.deleteIfExists(tempFile); // Limpar o arquivo temporário
            return "OK"; // A mensagem OK é implícita pelo envio do arquivo
        }

        private List<String> collectTemperatureData(String domain) throws IOException {
            // Implementação para coletar dados de temperatura dos dispositivos do domínio
            // ...
            List<String> data = new ArrayList<>();
            Set<String> devicesInDomain = getDevicesInDomain(domain);
            try (BufferedReader br = new BufferedReader(new FileReader("src/devices.txt"))) {
                String line;
                String currentDevice = null;
                while ((line = br.readLine()) != null) {
                    if (line.trim().startsWith("Dispositivo:")) {
                        currentDevice = line.substring(line.indexOf(':') + 1).trim();
                        System.out.println("dispositivo pegado no device.txt "+currentDevice);
                    } else if (line.trim().startsWith("Última Temperatura:") && currentDevice != null) {
                        System.out.println("o dispositivo está no dominio "+devicesInDomain.contains(currentDevice));
                        if (devicesInDomain.contains(currentDevice)) {
                            data.add(currentDevice + " - " + line.trim());
                        }
                    }
                }
            }
            return data;
        }

        private Set<String> getDevicesInDomain(String domain) throws IOException {
            Set<String> devices = new HashSet<>();
            try (BufferedReader br = new BufferedReader(new FileReader("src/dominios.txt"))) {
                String line;
                boolean isCurrentDomain = false;
                while ((line = br.readLine()) != null) {
                    if (line.trim().startsWith("Domínio: " + domain)) {
                        isCurrentDomain = true;
                    } else if (isCurrentDomain && line.trim().startsWith("Dispositivos registrados:")) {
                        String deviceLine = line.substring(line.indexOf(':') + 1).trim();
                        // Divide por vírgula seguida de espaço ou apenas espaço
                        String[] deviceList = deviceLine.split(",\\s*|\\s+");
                        for (String device : deviceList) {
                            if (!device.isEmpty()) {
                                devices.add(device.trim());
                            }
                        }
                        break;
                    } else if (line.trim().isEmpty()) {
                        isCurrentDomain = false;
                    }
                }
            }
            return devices;
        }
        private Path createTempFileWithData(List<String> data) throws IOException {
            Path tempFile = Files.createTempFile("temperature_data", ".txt");
            Files.write(tempFile, data, StandardOpenOption.WRITE);
            return tempFile;
        }

        private void sendFileToClient(Path file, ObjectOutputStream outStream) throws IOException {
            byte[] fileContent = Files.readAllBytes(file);
            outStream.writeUTF("OK");
            outStream.writeLong(fileContent.length);
            outStream.write(fileContent);
            outStream.flush();
        }
        private String  sendImageToclient(String filename,ObjectOutputStream outStream ) throws IOException {
            outStream.writeUTF("OK");
            String path = "src/dadosSensoriasClientes/"+filename; //filename=Erickson1_03-22-24";
            File file = new File(path);
            if (!file.exists()) {
                System.out.println("Arquivo não encontrado: " + filename);
                return "NODATA # esse device id não publicou dados";
            }
            long fileSize = file.length();
            // Enviando o tamanho do arquivo
            outStream.writeLong(fileSize);

            // Enviando o arquivo
            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outStream.write(buffer, 0, bytesRead);
                }
            }
            outStream.flush();
            return "OK"; // A mensagem OK é implícita pelo envio do arquivo
        }

        private String handleImageUpload(String[] partes, ObjectInputStream inStream) {
            if (partes.length != 2) {
                return "NOK"; // Formato de comando inválido
            }

            try {
                String filename = partes[1];
                long fileSize = inStream.readLong();
                byte[] imageBytes = new byte[(int) fileSize];
                int readBytes = 0;
                while (readBytes < fileSize) {
                    int result = inStream.read(imageBytes, readBytes, imageBytes.length - readBytes);
                    if (result == -1) {
                        break; // EOF
                    }
                    readBytes += result;
                }

                Path directoryPath = Paths.get("src/dadosSensoriasClientes/");
                Path filePath = directoryPath.resolve(filename);

                if (!Files.exists(directoryPath)) {
                    Files.createDirectories(directoryPath);
                }

                // Usando try-with-resources para garantir que o stream seja fechado
                try (OutputStream os = Files.newOutputStream(filePath, StandardOpenOption.CREATE)) {
                    os.write(imageBytes);
                }

                if (deviceManager.isDeviceRegistered(username, devId)) {
                    return deviceManager.updateDeviceImage(username, devId, filename) ? "OK" : "NOK";
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return "NOK"; // Falha no upload ou outro erro
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
            Map<String, DeviceData> devices = loadDevices();
            String dispositivoKey = username + ":" + devId;

            DeviceData data = devices.getOrDefault(dispositivoKey, new DeviceData(null, ""));
            data.temperatura = temperatura; // Atualiza a temperatura
            devices.put(dispositivoKey, data);

            return saveDevices(devices);
        }

        public synchronized boolean updateDeviceImage(String username, String devId, String filename) {
            Map<String, DeviceData> devices = loadDevices();
            String dispositivoKey = username + ":" + devId;

            DeviceData data = devices.getOrDefault(dispositivoKey, new DeviceData(null, ""));
            data.imagem = filename;
            devices.put(dispositivoKey, data);
            return saveDevices(devices);
        }

        private Map<String, DeviceData> loadDevices() {
            Map<String, DeviceData> devices = new HashMap<>();
            try (BufferedReader reader = new BufferedReader(new FileReader("src/devices.txt"))) {
                String line;
                String currentDevice = null;
                DeviceData currentData = null;

                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("Dispositivo:")) {
                        if (currentDevice != null && currentData != null) {
                            devices.put(currentDevice, currentData);
                        }
                        currentDevice = line.substring(line.indexOf(':') + 1).trim();
                        currentData = new DeviceData(null, ""); // Reset para novo dispositivo
                    } else if (line.trim().startsWith("Última Temperatura:")) {
                        if (currentData != null) {
                            String tempStr = line.substring(line.indexOf(':') + 1).trim();
                            if (!tempStr.isEmpty() && !tempStr.equals("°C")) { // Adicionada verificação extra
                                tempStr = tempStr.replace("°C", "").trim(); // Remove "°C" se existir
                                currentData.temperatura = Float.parseFloat(tempStr);
                            }
                        }
                    } else if (line.trim().startsWith("Última Imagem:")) {
                        if (currentData != null) {
                            currentData.imagem = line.substring(line.indexOf(':') + 1).trim();
                        }
                    }
                }
                if (currentDevice != null && currentData != null) {
                    devices.put(currentDevice, currentData); // Adiciona o último dispositivo lido
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return devices;
        }

        private boolean saveDevices(Map<String, DeviceData> devices) {
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

            for (Map.Entry<String, DeviceData> entry : devices.entrySet()) {
                String dispositivoKey = "Dispositivo: " + entry.getKey();
                int deviceIndex = -1;

                // Procura o dispositivo no arquivo
                for (int i = 0; i < fileContent.size(); i++) {
                    if (fileContent.get(i).equals(dispositivoKey)) {
                        deviceIndex = i;
                        break;
                    }
                }

                // Se encontrou, atualiza os dados; se não, adiciona
                if (deviceIndex != -1) {
                    updateDeviceData(fileContent, deviceIndex, entry.getValue());
                } else {
                    addNewDeviceData(fileContent, dispositivoKey, entry.getValue());
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

        private void updateDeviceData(List<String> fileContent, int deviceIndex, DeviceData data) {
            String temperaturaStr = (data.temperatura != null) ? "Última Temperatura: " + data.temperatura + "°C" : "Última Temperatura: ";
            fileContent.set(deviceIndex + 1, temperaturaStr);
            fileContent.set(deviceIndex + 2, "Última Imagem: " + data.imagem);
        }

        private void addNewDeviceData(List<String> fileContent, String dispositivoKey, DeviceData data) {
            String temperaturaStr = (data.temperatura != null) ? "Última Temperatura: " + data.temperatura + "°C" : "Última Temperatura: ";
            fileContent.add(dispositivoKey);
            fileContent.add(temperaturaStr);
            fileContent.add("Última Imagem: " + data.imagem);
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
        public synchronized Set<String> dominiosOfDevice(String dispositive) {
            Set<String> dominios=new HashSet<>();
            try (BufferedReader reader = new BufferedReader(new FileReader("src/dominios.txt"))) {
                String line;
                String dominio="";
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("Domínio:")) {
                        dominio=line.split(" ")[1];
                    }
                    if (line.trim().startsWith("Dispositivos registrados:")) {
                        if (line.contains(dispositive)) {
                            dominios.add(dominio);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return dominios;
        }
    }
    public class DeviceData {
        public Float temperatura;
        public String imagem;

        public DeviceData(Float temperatura, String imagem) {
            this.temperatura = temperatura;
            this.imagem = imagem;
        }
    }
}