import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;


import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.SignedObject;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
public class IoTServer {
    
    private int port; 
    private String passwordCifra; 
    private String keystore;
    private String keystorePassword;
    private String api;

    private SSLServerSocket ss;

    private AuthenticationService authenticationService;
    private DeviceManager deviceManager;
    public IoTServer(int port, String passwordCifra, String keystore, String keystorePassword, String api) {
        
        this.port = port;
        this.passwordCifra = passwordCifra;
        this.keystore = keystore;
        this.keystorePassword = keystorePassword;
        this.api = api;
        ss = null;

        //Criar ficheiros de texto
        File fileStarter = new File("users.txt");
        try {
            fileStarter.createNewFile();
            fileStarter = new File("dominios.txt");
            fileStarter.createNewFile();
            fileStarter = new File("devices.txt");
            fileStarter.createNewFile();
            fileStarter = new File("executavel.txt");
            fileStarter.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //executorService = Executors.newCachedThreadPool();
        authenticationService = new AuthenticationService();
        deviceManager = new DeviceManager();
        startSessionCleanupThread();
    }
    public static void main(String[] args) {
        
        int port = 12345; 
        String passwordCifra = ""; 
        String keystore = "";
        String keystorePassword = "";
        String api = "";

        if (args.length == 5) {
            
            port = Integer.parseInt(args[0]);
            passwordCifra = args[1];
            keystore = args[2];
            keystorePassword = args[3];
            api = args[4];
        } else if (args.length == 4) {
            passwordCifra = args[0];
            keystore = args[1];
            keystorePassword = args[2];
            api = args[3];
        } else {
            System.out.println("Invalid number of args");
            System.exit(-1);
        }


        new IoTServer(port, passwordCifra, keystore, keystorePassword, api).startServer();
    }
    public void startServer() {

        System.setProperty("javax.net.ssl.keyStore", keystore);
		System.setProperty("javax.net.ssl.keyStorePassword", keystorePassword);
        System.setProperty("javax.net.ssl.keyStoreType", "JKS");

        ServerSocketFactory ssf = SSLServerSocketFactory.getDefault();
        
        try  {
            System.out.println("Servidor iniciado.");
            
            ss = (SSLServerSocket) ssf.createServerSocket(port);

            while (true) {
                Socket clientSocket = ss.accept();
                ServerThread thread = new ServerThread(clientSocket, authenticationService, deviceManager);
                thread.start();
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
    class ServerThread extends Thread {
        private Socket clientSocket;
        private AuthenticationService authenticationService;
        private DeviceManager deviceManager;
        private String userid;
        private String devId;
        ServerThread(Socket socket, AuthenticationService authService, DeviceManager deviceMgr) {
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

                System.out.println("Cheguei ao run");

                //1-recebendo dados do cliente
                boolean authenticated = false;
                while (!authenticated) {
                    userid = (String) inStream.readObject();

                    SecureRandom secureRandom = new SecureRandom();
                    byte[] nonce = new byte[8];
                    secureRandom.nextBytes(nonce);

                    Signature signature = Signature.getInstance("MD5withRSA");

                    outStream.writeObject(nonce);
                    outStream.writeObject(authenticationService.userExists(userid));
                    
                    if (authenticationService.userExists(userid)) {
                        System.out.println("Entrei Existe");
                        SignedObject signedObject = (SignedObject) inStream.readObject();
                        FileInputStream fileInputStream = new FileInputStream( userid + ".cer");
                        CertificateFactory cf = CertificateFactory.getInstance("X509");
                        Certificate certificate = cf.generateCertificate(fileInputStream);
                        PublicKey publicKey = certificate.getPublicKey();

                        if (Arrays.equals((byte[]) signedObject.getObject(), nonce)
							&& signedObject.verify(publicKey, signature)) {
                            System.out.println("Entrei verify");
						    outStream.writeObject(true);

                        } else {
                            System.out.println("Entrei nverify");
                            outStream.writeObject(false);
                            System.exit(-1);
                        }
                        authenticated = true;
                        //outStream.writeObject(authenticationService.userExists(userid));
                    } else {
                        System.out.println("Entrei Nao Existe");
                        byte[] receivedNonce = (byte[]) inStream.readObject();
                        byte[] signatureBytes = (byte[]) inStream.readObject();
                        Certificate certificate = (Certificate) inStream.readObject();
                        PublicKey publicKey = certificate.getPublicKey();
                        signature.initVerify(publicKey);
    
                        if (Arrays.equals(receivedNonce, nonce) && signature.verify(signatureBytes)) {
    
                            authenticationService.saveNewUser(userid,  userid + ".cer");
                            authenticated = true;
                            outStream.writeObject(true);
                        } else {
                            outStream.writeObject(false);
                            System.exit(-1);
                        }
                    }
    
                }
                //2-recebendo dados do device-id
                boolean deviceRegistered = false;
                while (!deviceRegistered) {
                    devId = (String) inStream.readObject();
                    System.out.println("Get device");
                    boolean isRegistered = deviceManager.registerDevice(userid, devId);

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
                }
                outStream.flush();

                //4- recebendo o comando que o cliente quer
                while (!clientSocket.isClosed()) {
                    try {
                        String comando = inStream.readUTF();
                        String resposta = processarComando(comando,inStream,outStream);
                        outStream.writeUTF(resposta);
                        outStream.flush();
                    } catch (IOException e) {
                        System.err.println("Erro ao ler comando: " + e.getMessage());
                        break;  // Sair do loop em caso de erro
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro ao tratar cliente: " + e.getMessage());
                e.printStackTrace();
            } catch (CertificateException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (SignatureException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally {
                // A lógica de limpeza e fechamento do socket
                if (userid != null && devId != null) {
                    deviceManager.removeActiveSession(userid, devId);
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
                if (parts.length != 2) {
                    return "Formato de Comando Inválido";
                }
                return handleImageRequest(parts[1], outStream);
            }
            if(parts[0].equalsIgnoreCase("MYDOMAINS")) {
                return handleMyDomains(this.userid);
            }
            return "Comando Desconhecido";
        }
        private String handleImageRequest(String deviceId, ObjectOutputStream outStream) throws IOException {
            if(!deviceId.contains(":")){
                return "NOID # esse device id não existe";
            }
            String[] userid_idDevice=  deviceId.split(":");
            if (!deviceManager.isDeviceRegistered(userid_idDevice[0], userid_idDevice[1])) {
                return "NOID # esse device id não existe";
            }
            if (!isUserAllowedToReadDevice(deviceId)) {
                return "NOPERM # sem permissões de leitura";
            }
            String imagePath = deviceManager.getDeviceImagePath(deviceId); //getDeviceImagePath
            if (imagePath == null || imagePath.isEmpty()) {
                return "NODATA # esse device id não publicou dados";
            }
            Path imageFile = Paths.get("dadosSensoriasClientes/", imagePath);
            if (!Files.exists(imageFile)) {
                return "NODATA # imagem não encontrada";
            }
            sendFileToClient(imageFile, outStream);
            return "OK"; // A mensagem OK é implícita pelo envio do arquivo
        }
        private String handleTemperatureReadRequest(String domain, ObjectOutputStream outStream) throws IOException {
            if (!dominioExiste(domain)) {
                return "NODM # esse domínio não existe";
            }

            if (!isUserAllowed(domain, userid)) {
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
        private boolean isUserAllowedToReadDevice(String deviceId) throws IOException {
            boolean result=false;
            try (BufferedReader br = new BufferedReader(new FileReader("dominios.txt"))) {
                String line;
                String currentDomain = null;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("Domínio:")) {
                        currentDomain = line.substring(line.indexOf(':') + 1).trim();
                    }
                    if (line.startsWith("Dispositivos registrados:") && currentDomain != null) {
                        if (line.contains(deviceId)) {
                            // Dispositivo encontrado no domínio, verificar permissão do usuário
                            if(isUserAllowed(currentDomain, userid) !=false)
                                result=true;
                        }
                    }
                }
            }
            return result; // Dispositivo não encontrado ou usuário sem permissão
        }
        private List<String> collectTemperatureData(String domain) throws IOException {
            // Implementação para coletar dados de temperatura dos dispositivos do domínio
            // ...
            List<String> data = new ArrayList<>();
            Set<String> devicesInDomain = getDevicesInDomain(domain);
            try (BufferedReader br = new BufferedReader(new FileReader("devices.txt"))) {
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
            try (BufferedReader br = new BufferedReader(new FileReader("dominios.txt"))) {
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
                Path directoryPath = Paths.get("dadosSensoriasClientes/");
                Path filePath = directoryPath.resolve(filename);

                if (!Files.exists(directoryPath)) {
                    Files.createDirectories(directoryPath);
                }
                // Usando try-with-resources para garantir que o stream seja fechado
                try (OutputStream os = Files.newOutputStream(filePath, StandardOpenOption.CREATE)) {
                    os.write(imageBytes);
                }
//                if (deviceManager.isDeviceRegistered(userid, devId)) {
               // Se o device está ou não num domínio, é irrelevante para os dados ficarem guardados no servidor.
//                    return deviceManager.updateDeviceImage(userid, devId, filename) ? "OK" : "NOK";
//                }
                return deviceManager.updateDeviceImage(userid, devId, filename) ? "OK" : "NOK";


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
//                if (deviceManager.isDeviceRegistered(userid, devId)) {
// Se o device está ou não num domínio, é irrelevante para os dados ficarem guardados no servidor.
//                    return deviceManager.updateDeviceTemperature(userid, devId, temperatura) ? "OK" : "NOK";
//                }
//                System.out.println("temperatura do dispositivo "+ userid+":"+devId+" registada");
                return deviceManager.updateDeviceTemperature(userid, devId, temperatura) ? "OK" : "NOK";

            } catch (NumberFormatException e) {
                return "NOK"; // Formato de temperatura inválido
            }
            //return "NOK"; // Dispositivo não registrado ou outro erro
        }
        private String handleMyDomains(String userID) {
            if (userID == null || userID.isEmpty()) {
                return "ERROR: User ID is required for MYDOMAINS command";
            }
            System.out.println("User ID: " + userID);
            List<String> domains = deviceManager.getDeviceDomains(userID);
            if (domains.isEmpty()) {
                return "No domains found for device " + userID;
            }
            return String.join(", ", domains);  // Appending 'OK' to signify the end of data
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
                    try (FileWriter fw = new FileWriter("dominios.txt", true);
                         BufferedWriter bw = new BufferedWriter(fw)) {
                        bw.write("\nDomínio: " + nomeDominio + "\nOwner: " + userid + "\nUsuários com permissão: " + userid + "\nDispositivos registrados: \n");
                    }
                    return "OK";  // Domínio criado com sucesso
                } catch (IOException e) {
                    System.err.println("Erro ao acessar o arquivo de domínios: " + e.getMessage());
                    return "Erro ao criar o domínio";
                }
            }
        }
        private boolean dominioExiste(String nomeDominio) throws IOException {
            try (BufferedReader br = new BufferedReader(new FileReader("dominios.txt"))) {
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
                    if (!isOwner(dm, userid)) {
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
            try (BufferedReader br = new BufferedReader(new FileReader("dominios.txt"))) {
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
            boolean foundDomain = false;
            boolean updatedUsers = false;

            try (BufferedReader br = new BufferedReader(new FileReader("dominios.txt"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("Domínio: " + dm)) {
                        foundDomain = true;
                        fileContent.add(line); // Adiciona linha do domínio
                        continue;
                    }
                    if (foundDomain) {
                        if (line.startsWith("Usuários com permissão:")) {
                            if (!line.contains(user1)) {
                                line += ", " + user1;
                            }
                            updatedUsers = true;
                        } else if (line.startsWith("Dispositivos registrados:") && !updatedUsers) {
                            // Esta condição garante que a linha de dispositivos seja preservada
                            // mesmo se não houver atualização na lista de usuários
                            String previousLine = fileContent.get(fileContent.size() - 1);
                            previousLine += ", " + user1;
                            fileContent.set(fileContent.size() - 1, previousLine);
                        } else if (line.trim().isEmpty()) {
                            foundDomain = false; // Finaliza a edição deste domínio
                        }
                    }
                    fileContent.add(line);
                }
            }

            // Reescrever o arquivo com o conteúdo atualizado
            try (BufferedWriter bw = new BufferedWriter(new FileWriter("dominios.txt"))) {
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
                    if (!isUserAllowed(dm, userid)) {
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
         * @param userid
         * @return
         * @throws IOException
         */
        private boolean isUserAllowed(String dm, String userid) throws IOException {
            try (BufferedReader br = new BufferedReader(new FileReader("dominios.txt"))) {
                String line;
                boolean isCurrentDomain = false;
                while ((line = br.readLine()) != null) {
                    if (line.contains("Domínio: " + dm)) {
                        isCurrentDomain = true;
                    } else if (isCurrentDomain && line.contains("Usuários com permissão:")) {
                        return line.contains(userid);
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
            String dispositivo = userid + ":" + devId;

            try (BufferedReader br = new BufferedReader(new FileReader("dominios.txt"))) {
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
            try (BufferedWriter bw = new BufferedWriter(new FileWriter("dominios.txt"))) {
                for (String line : lines) {
                    bw.write(line);
                    bw.newLine();
                }
            }
        }
        private boolean validarTamanhoExecutavel(String nomeArquivo, long tamanhoArquivo) {
            try {
                File file = new File("executavel.txt");
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
}