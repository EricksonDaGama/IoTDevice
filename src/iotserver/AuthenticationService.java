package src.iotserver;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;

public class AuthenticationService {
    private static volatile AuthenticationService INSTANCE;

    private static final String USER_FILEPATH = "user.txt";
    private static String apiKey;

    private UserStorage userStorage;

    public static AuthenticationService getInstance() {
        AuthenticationService instance = INSTANCE;
        if (instance != null)
            return instance;

        synchronized (AuthenticationService.class) {
            if (instance == null)
                instance = new AuthenticationService();
            return instance;
        }
    }

    private AuthenticationService() {
        userStorage = new UserStorage(USER_FILEPATH);
    }

    public boolean isUserRegistered(String user) {
        userStorage.readLock();
        try {
            return userStorage.isUserRegistered(user);
        } finally {
            userStorage.readUnlock();
        }
    }

    public boolean registerUser(String user, String certPath) {
        userStorage.writeLock();
        try {
            return userStorage.registerUser(user, certPath);
        } finally {
            userStorage.writeUnlock();
        }
    }

    public String userCertPath(String user) {
        userStorage.readLock();
        try {
            return userStorage.userCertPath(user);
        } finally {
            userStorage.readUnlock();
        }
    }

    public static long generateNonce() {
        return ThreadLocalRandom.current().nextLong();
    }

    public static void setApiKey(String key) {
        apiKey = key;
    }

    public static int generate2FACode() {
        return ThreadLocalRandom.current().nextInt(0, 100000);
    }

    public static int send2FAEmail(String emailAddress, int code) {
        String codeStr = String.valueOf(code);
        String urlStr = String.format("https://lmpinto.eu.pythonanywhere.com" +
                "/2FA?e=%s&c=%s&a=%s", emailAddress, codeStr, apiKey);

        int responseCode = 500;
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            responseCode = conn.getResponseCode();
            conn.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return responseCode;
    }

    public boolean verifySignedNonce(byte[] signedNonce, String user, long nonce)
            throws FileNotFoundException, IOException, CertificateException,
            NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signature = Signature.getInstance("MD5withRSA");
        Certificate cert = null;
        try (InputStream in = new FileInputStream(certPathFromUser(user))) {
            cert = CertificateFactory.getInstance("X509")
                    .generateCertificate(in);
        }

        signature.initVerify(cert);
        signature.update(ByteBuffer.allocate(Long.BYTES).putLong(nonce).array());
        return signature.verify(signedNonce);
    }
    public static String certPathFromUser(String user) {
        return "output/server/certificado/" + user + ".cert";
    }


    public void saveCertificateInFile(String user, Certificate cert) {
        try {
            initializeFile(certPathFromUser(user));
            FileOutputStream os = new FileOutputStream(certPathFromUser(user));
            os.write("-----BEGIN CERTIFICATE-----\n".getBytes("US-ASCII"));
            os.write(Base64.getEncoder().encode(cert.getEncoded()));
            os.write("-----END CERTIFICATE-----\n".getBytes("US-ASCII"));
            os.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (CertificateEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static File initializeFile(String filename) throws IOException {
        File fileCreated = new File(filename);
        if (!fileCreated.exists()) {
            fileCreated.createNewFile();
            System.out.println("File created: " + fileCreated.getName());
        }
        return fileCreated;
    }

    public boolean verifySignedNonce(byte[] signedNonce, Certificate cert, long nonce)
            throws SignatureException, NoSuchAlgorithmException, InvalidKeyException {
        Signature signature = Signature.getInstance("MD5withRSA");
        signature.initVerify(cert);
        signature.update(ByteBuffer.allocate(Long.BYTES).putLong(nonce).array());
        return signature.verify(signedNonce);
    }

    public static boolean verifyAttestationHash(byte[] hash, long nonce)
            throws IOException, NoSuchAlgorithmException {
        final int CHUNK_SIZE = 1024;
        String clientExecPath = getAttestationPath();
        long clientExecSize = new File(clientExecPath).length();
        FileInputStream clientExecInStream = new FileInputStream(clientExecPath);
        MessageDigest md = MessageDigest.getInstance("SHA");

        long leftToRead = clientExecSize;
        while (leftToRead >= CHUNK_SIZE) {
            md.update(clientExecInStream.readNBytes(CHUNK_SIZE));
            leftToRead -= CHUNK_SIZE;
        }
        md.update(clientExecInStream.readNBytes(Long.valueOf(leftToRead)
                .intValue()));
        md.update(longToByteArray(nonce));

        clientExecInStream.close();

        byte[] computedHash = md.digest();
        return MessageDigest.isEqual(hash, computedHash);
    }


    public static String getAttestationPath() throws IOException{
        BufferedReader br = new BufferedReader(new FileReader("atestacaoRemota.txt"));
        String path = br.readLine();
        return path;
    }

    public static byte[] longToByteArray(long l) {
        return ByteBuffer.allocate(Long.BYTES).putLong(l).array();
    }

}
