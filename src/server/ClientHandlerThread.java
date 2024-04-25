package src.server;

import src.others.CodeMessage;

import java.io.*;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;

public class ClientHandlerThread extends Thread {
    private static final String IMAGE_DIR_PATH = "./output/server/img/";

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private ManagerSever manager;
    private String userID;
    private String deviceID;
    private boolean isRunning;

    public ClientHandlerThread(Socket socket, String keystorePath, String keystorePwd,
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
            manager = ManagerSever.getInstance();

            while (isRunning) {
                CodeMessage opcode = (CodeMessage) in.readObject();
                switch (opcode) {
                    case AU:
                        authUser();
                        break;
                    case AD:
                       authDevice();
                        //---------------------------------------
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
        AuthenticationService sa = IoTServer.SERVER_AUTH;
        userID = (String) in.readObject();

        long nonce = sa.generateNonce();
        out.writeLong(nonce);

        if (sa.isUserRegistered(userID)) {
            out.writeObject(CodeMessage.OK_USER);
            authRegisteredUser(nonce); //certificado
        } else {
            out.writeObject(CodeMessage.OK_NEW_USER);
            authUnregisteredUser(nonce); //certificado
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
            out.writeObject(CodeMessage.OK);
        } else {
            out.writeObject(CodeMessage.NOK);
        }
    }

    private void authDevice() throws IOException, ClassNotFoundException {
        String deviceID = (String) in.readObject();
        CodeMessage res = manager.authenticateDevice(userID, deviceID).responseCode();
        if (res == CodeMessage.OK_DEVID) {
            this.deviceID = deviceID;
        }
        out.writeObject(res);
    }

    private void attestClient() throws ClassNotFoundException, IOException,
            NoSuchAlgorithmException {
        long nonce = AuthenticationService.generateNonce();
        out.writeLong(nonce);
        out.flush();

        byte[] receivedHash = (byte[]) in.readObject();
        if (AuthenticationService.verifyAttestationHash(receivedHash, nonce)) {
            out.writeObject(CodeMessage.OK_TESTED);
        } else {
            manager.disconnectDevice(userID, deviceID);
            out.writeObject(CodeMessage.NOK_TESTED);
        }
    }

    private void createDomain() throws IOException, ClassNotFoundException {
        String domain = (String) in.readObject();
        CodeMessage res = manager.createDomain(userID, domain).responseCode();
        out.writeObject(res);
    }

    private void addUserToDomain() throws IOException, ClassNotFoundException {
        String newUser = (String) in.readObject();
        String domain = (String) in.readObject();
        CodeMessage res = manager.addUserToDomain(userID, newUser, domain).responseCode();
        out.writeObject(res);
    }

    private void registerDeviceInDomain() throws IOException, ClassNotFoundException {
        String domain = (String) in.readObject();
        CodeMessage res = manager.registerDeviceInDomain(domain, this.userID, this.deviceID).responseCode();
        out.writeObject(res);
    }

    private void registerTemperature() throws IOException, ClassNotFoundException {
        String tempStr = (String) in.readObject();
        float temperature;
        try {
            temperature = Float.parseFloat(tempStr);
        } catch (NumberFormatException e) {
            out.writeObject(new ServerResponse(CodeMessage.NOK));
            out.flush();
            return;
        }

        CodeMessage res = manager
                .registerTemperature(temperature, this.userID, this.deviceID)
                .responseCode();
        out.writeObject(res);
        out.flush();
    }

    private void registerImage() throws IOException, ClassNotFoundException {
        String filename = (String) in.readObject();
        long fileSize = (long) in.readObject();
        String fullImgPath = IMAGE_DIR_PATH + filename;

        receiveFile(fileSize, fullImgPath, in);

        CodeMessage res = manager
                .registerImage(filename, this.userID, this.deviceID)
                .responseCode();
        out.writeObject(res);
    }




    public static void receiveFile(Long fileSize, String path, ObjectInputStream in) {
        try {
            File f = new File(path);
            f.createNewFile();

            FileOutputStream fout = new FileOutputStream(f);
            OutputStream output = new BufferedOutputStream(fout);

            int bytesWritten = 0;
            byte[] buffer = new byte[1024];

            while (fileSize > bytesWritten) {
                int bytesRead = in.read(buffer, 0, 1024);
                output.write(buffer, 0, bytesRead);
                output.flush();
                fout.flush();
                bytesWritten += bytesRead;
                System.out.println(bytesWritten);
            }
            output.close();
            fout.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }



    private void getTemperatures() throws IOException, ClassNotFoundException {
        String domain = (String) in.readObject();
        ServerResponse sResponse = manager.getTemperatures(this.userID, domain);
        CodeMessage res = sResponse.responseCode();
        out.writeObject(res);
        if (res == CodeMessage.OK) {
            // FileHelper.sendFile(sResponse.filePath(),out);
            out.writeObject(sResponse.temperatures());
        }
    }

    private void getImage() throws IOException, ClassNotFoundException {
        String targetUser = (String) in.readObject();
        String targetDev = (String) in.readObject();
        ServerResponse sr = manager.getImage(this.userID, targetUser, targetDev);
        CodeMessage rCode = sr.responseCode();
        // Send code to client
        out.writeObject(rCode);
        // Send file (if aplicable)
        if (rCode == CodeMessage.OK) {
            sendFile(sr.filePath(), out);
        }
    }


    public static void sendFile(String path,ObjectOutputStream out) {
        File f = new File(path);
        long fileSize = f.length();
        try {
            // Send file name
            out.writeObject(f.getName());
            // Send file size
            out.writeObject(fileSize);

            FileInputStream fin = new FileInputStream(f);
            InputStream input = new BufferedInputStream(fin);
            // Send file
            int bytesSent = 0;
            byte[] buffer = new byte[1024];
            while (fileSize > bytesSent) {
                int bytesRead = input.read(buffer, 0, 1024);
                bytesSent += bytesRead;
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
            input.close();
            fin.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void authUnregisteredUser(long nonce) throws IOException,
            ClassNotFoundException, InvalidKeyException, CertificateException,
            NoSuchAlgorithmException, SignatureException {
        AuthenticationService sa = IoTServer.SERVER_AUTH;

        long receivedUnsignedNonce = in.readLong();
        byte[] signedNonce = (byte[]) in.readObject();
        Certificate cert = (Certificate) in.readObject();

        if (sa.verifySignedNonce(signedNonce, cert, nonce) &&
                receivedUnsignedNonce == nonce) {
            sa.registerUser(userID, certPathFromUser(userID));
            sa.saveCertificateInFile(userID, cert);
            out.writeObject(CodeMessage.OK);
        } else {
            out.writeObject(CodeMessage.WRONG_NONCE);
        }
    }
    public static String certPathFromUser(String user) {
        return "output/server/certificado/" + user + ".cert";
    }


    private void authRegisteredUser(long nonce) throws ClassNotFoundException,
            IOException, InvalidKeyException, CertificateException,
            NoSuchAlgorithmException, SignatureException {
        AuthenticationService sa = IoTServer.SERVER_AUTH;

        byte[] signedNonce = (byte[]) in.readObject();
        if (sa.verifySignedNonce(signedNonce, userID, nonce)) {
            out.writeObject(CodeMessage.OK);
        } else {
            out.writeObject(CodeMessage.WRONG_NONCE);
        }
    }
}