package src.iotserver;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import src.iohelper.FileHelper;
import src.iohelper.Utils;
import src.iotclient.MessageCode;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;

public class IoTServer {
    public static final ServerManager SERVER_MANAGER = ServerManager
        .getInstance();
    public static final ServerAuth SERVER_AUTH = ServerAuth.getInstance();

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

        ServerAuth.setApiKey(apiKeyArg);

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
                ServerThread thread = new ServerThread(connection, keystorePath,
                        keystorePwd, apiKey);
                thread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class ServerThread extends Thread {
        private static final String IMAGE_DIR_PATH = "./output/server/img/";

        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private ServerManager manager;
        private String userID;
        private String deviceID;
        private boolean isRunning;

        public ServerThread(Socket socket, String keystorePath, String keystorePwd,
                String apiKey) {
            this.socket = socket;
            this.userID = null;
            this.deviceID = null;
            this.isRunning = true;
        }

        public void run() {
            System.out.println("Accepted connection!");

            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
                manager = ServerManager.getInstance();

                while (isRunning) {
                    MessageCode opcode = (MessageCode) in.readObject();
                    switch (opcode) {
                        case AU:
                            authUser();
                            break;
                        case AD:
                            authDevice();
                            break;
                        case TD:
                            attestClient();
                            break;
                        case CREATE:
                            createDomain();
                            break;
                        case ADD:
                            addUserToDomain();
                            break;
                        case RD:
                            registerDeviceInDomain();
                            break;
                        case ET:
                            registerTemperature();
                            break;
                        case EI:
                            registerImage();
                            break;
                        case RT:
                            getTemperatures();
                            break;
                        case RI:
                            getImage();
                            break;
                        case STOP:
                            stopThread();
                            break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (CertificateException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (SignatureException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        private void stopThread() {
            System.out.println("Client quits. killing thread");
            manager.disconnectDevice(this.userID, this.deviceID);
            isRunning = false;
        }

        private void authUser() throws ClassNotFoundException, IOException,
                InvalidKeyException, CertificateException, NoSuchAlgorithmException,
                SignatureException {
            System.out.println("Starting user auth.");
            ServerAuth sa = IoTServer.SERVER_AUTH;
            userID = (String) in.readObject();

            long nonce = sa.generateNonce();
            out.writeLong(nonce);

            if (sa.isUserRegistered(userID)) {
                out.writeObject(MessageCode.OK_USER);
                authRegisteredUser(nonce);
            } else {
                out.writeObject(MessageCode.OK_NEW_USER);
                authUnregisteredUser(nonce);
            }

            int twoFACode = sa.generate2FACode();
            int emailResponseCode = sa.send2FAEmail(userID, twoFACode);
            // Handle bad email response code
            while (emailResponseCode != 200) {
                twoFACode = sa.generate2FACode();
                emailResponseCode = sa.send2FAEmail(userID, twoFACode);
            }

            int receivedTwoFACode = in.readInt();

            if (twoFACode == receivedTwoFACode) {
                out.writeObject(MessageCode.OK);
            } else {
                out.writeObject(MessageCode.NOK);
            }
        }

        private void authDevice() throws IOException, ClassNotFoundException {
            String deviceID = (String) in.readObject();
            MessageCode res = manager.authenticateDevice(userID, deviceID).responseCode();
            if (res == MessageCode.OK_DEVID) {
                this.deviceID = deviceID;
            }
            out.writeObject(res);
        }

        private void attestClient() throws ClassNotFoundException, IOException,
                NoSuchAlgorithmException {
            long nonce = ServerAuth.generateNonce();
            out.writeLong(nonce);
            out.flush();

            byte[] receivedHash = (byte[]) in.readObject();
            if (ServerAuth.verifyAttestationHash(receivedHash, nonce)) {
                out.writeObject(MessageCode.OK_TESTED);
            } else {
                manager.disconnectDevice(userID, deviceID);
                out.writeObject(MessageCode.NOK_TESTED);
            }
        }

        private void createDomain() throws IOException, ClassNotFoundException {
            String domain = (String) in.readObject();
            MessageCode res = manager.createDomain(userID, domain).responseCode();
            out.writeObject(res);
        }

        private void addUserToDomain() throws IOException, ClassNotFoundException {
            String newUser = (String) in.readObject();
            String domain = (String) in.readObject();
            MessageCode res = manager.addUserToDomain(userID, newUser, domain).responseCode();
            out.writeObject(res);
        }

        private void registerDeviceInDomain() throws IOException, ClassNotFoundException {
            String domain = (String) in.readObject();
            MessageCode res = manager.registerDeviceInDomain(domain, this.userID, this.deviceID).responseCode();
            out.writeObject(res);
        }

        private void registerTemperature() throws IOException, ClassNotFoundException {
            String tempStr = (String) in.readObject();
            float temperature;
            try {
                temperature = Float.parseFloat(tempStr);
            } catch (NumberFormatException e) {
                out.writeObject(new ServerResponse(MessageCode.NOK));
                out.flush();
                return;
            }

            MessageCode res = manager
                    .registerTemperature(temperature, this.userID, this.deviceID)
                    .responseCode();
            out.writeObject(res);
            out.flush();
        }

        private void registerImage() throws IOException, ClassNotFoundException {
            String filename = (String) in.readObject();
            long fileSize = (long) in.readObject();
            String fullImgPath = IMAGE_DIR_PATH + filename;

            FileHelper.receiveFile(fileSize, fullImgPath, in);

            MessageCode res = manager
                    .registerImage(filename, this.userID, this.deviceID)
                    .responseCode();
            out.writeObject(res);
        }

        private void getTemperatures() throws IOException, ClassNotFoundException {
            String domain = (String) in.readObject();
            ServerManager manager = ServerManager.getInstance();


            ServerResponse sr= manager.getTemperatures(this.userID, domain,out);
            MessageCode rCode = sr.responseCode();
            out.writeObject(rCode);


            List<String> temperatureData = collectTemperatureData(domain);

            Path tempFile = createTempFileWithData(temperatureData);
            sendFileToClient(tempFile, out);
            //FileHelper.sendFile(tempFile.toString(), out);
            Files.deleteIfExists(tempFile); // Limpar o arquivo temporário

        }

        private void sendFileToClient(Path file, ObjectOutputStream outStream) throws IOException {
            byte[] fileContent = Files.readAllBytes(file);
            outStream.writeUTF("OK");
            outStream.writeLong(fileContent.length);
            outStream.write(fileContent);
            outStream.flush();
        }



        private List<String> collectTemperatureData(String domain) throws IOException {
            List<String> data = new ArrayList<>();
            //Set<String> devicesInDomain = getDevicesInDomain(domain);
            try (BufferedReader br = new BufferedReader(new FileReader("output/server/device.txt"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(":");
                    if (parts.length >= 4) {
                        String deviceId = parts[1].trim();
                        String temperature = parts[2].trim();
                        DomainStorage domStorage = new DomainStorage("output/server/domain.txt");
                        if (domStorage.isUserRegisteredInDomain(parts[0], domain)) {
                            data.add("Device ID: " + deviceId + " - Temperature: " + temperature);
                        }
                    }
                }
            }
            return data;
        }

        private Path createTempFileWithData(List<String> data) throws IOException {
            Path tempFile = Files.createTempFile("temperature_data", ".txt");
            Files.write(tempFile, data, StandardOpenOption.WRITE);
            return tempFile;
        }

        private void getImage() throws IOException, ClassNotFoundException {
            String targetUser = (String) in.readObject();
            String targetDev = (String) in.readObject();
            ServerResponse sr = manager.getImage(this.userID, targetUser, targetDev);
            MessageCode rCode = sr.responseCode();
            // Send code to client
            out.writeObject(rCode);
            // Send file (if aplicable)
            if (rCode == MessageCode.OK) {
                FileHelper.sendFile(sr.filePath(), out);
            }
        }

        private void authUnregisteredUser(long nonce) throws IOException,
                ClassNotFoundException, InvalidKeyException, CertificateException,
                NoSuchAlgorithmException, SignatureException {
            ServerAuth sa = IoTServer.SERVER_AUTH;

            long receivedUnsignedNonce = in.readLong();
            byte[] signedNonce = (byte[]) in.readObject();
            Certificate cert = (Certificate) in.readObject();

            if (sa.verifySignedNonce(signedNonce, cert, nonce) &&
                    receivedUnsignedNonce == nonce) {
                sa.registerUser(userID, Utils.certPathFromUser(userID));
                sa.saveCertificateInFile(userID, cert);
                out.writeObject(MessageCode.OK);
            } else {
                out.writeObject(MessageCode.WRONG_NONCE);
            }
        }

        private void authRegisteredUser(long nonce) throws ClassNotFoundException,
                IOException, InvalidKeyException, CertificateException,
                NoSuchAlgorithmException, SignatureException {
            ServerAuth sa = IoTServer.SERVER_AUTH;

            byte[] signedNonce = (byte[]) in.readObject();
            if (sa.verifySignedNonce(signedNonce, userID, nonce)) {
                out.writeObject(MessageCode.OK);
            } else {
                out.writeObject(MessageCode.WRONG_NONCE);
            }
        }
    }
}
