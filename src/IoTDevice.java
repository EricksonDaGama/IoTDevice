import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.SignedObject;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Scanner;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class IoTDevice {
    private static String userid; //"Rodrigo"; // "Barata" //"Erickson"
    private static String truststore;
    private static String keystore;
    private static String keystorePw;
    private static String devid;
    private static  String host;  //"localhost";
    private static final int PORT = 12345;

    private static KeyStore trustStore;
    private static KeyStore keyStore;
    private static PrivateKey privateKey;

    public static void main(String[] args) {
        // Check arguments
        if (args.length != 6) {
            System.out.println(
                    "Error: Invalid args!\nUsage: IoTDevice <serverAddress> <truststore> <keystore> <passwordkeystore> <dev-id> <user-id>\n");
            System.exit(1);
        }

        host = args[0];
        truststore = args[1];
        keystore = args[2];
        keystorePw = args[3];
        devid = args[4];
        userid = args[5];

        if(!devid.chars().allMatch(Character::isDigit)) {
            System.out.println(
                    "Error: Invalid DevID\nUsage: IoTDevice <serverAddress> <dev-id> <user-id>\n");
            System.exit(1);
        }

        System.out.println("Truststore: " + truststore);
        System.out.println("Keystore: " + keystore);

        System.setProperty("javax.net.ssl.trustStore", truststore);
        System.setProperty("javax.net.ssl.trustStorePassword", "ampgeg");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        System.setProperty("javax.net.ssl.keyStore", keystore);
        System.setProperty("javax.net.ssl.keyStorePassword", keystorePw);
        System.setProperty("javax.net.ssl.keyStoreType", "JKS");

        FileInputStream tfile;
        try {
            tfile = new FileInputStream(truststore);
       
            KeyStore tstore = KeyStore.getInstance(KeyStore.getDefaultType());
            tstore.load(tfile, "ampgeg".toCharArray());

            System.out.println("passei");

            KeyStore kstore = KeyStore.getInstance(KeyStore.getDefaultType());
            kstore.load(new FileInputStream(keystore), keystorePw.toCharArray());

            SocketFactory ssf = SSLSocketFactory.getDefault();
			SSLSocket socket = (SSLSocket) ssf.createSocket(host, PORT);

            System.out.println("Passei SSL");

			ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());

            System.out.println("Passei object streams");

			out.writeObject(userid);

			System.out.println("Autenticating...");

			byte[] nonce = (byte[]) in.readObject();
			boolean userExists = (Boolean) in.readObject();

			PrivateKey privateKey = (PrivateKey) kstore.getKey(kstore.aliases().nextElement().toString(),
					keystorePw.toCharArray());
			Signature signature = Signature.getInstance("MD5withRSA");

            Scanner scanner = new Scanner(System.in);

            if (userExists) {

				SignedObject signedObject = new SignedObject(nonce, privateKey, signature);
				out.writeObject(signedObject);

				if ((boolean) in.readObject()) {
					System.out.println("User autenticated!");
					System.out.println("Chose an operation:");
					mostrarMenu();

					String directoryName = "output/client" + userid;
					File directory = new File(directoryName);
					directory.mkdirs();

				} else {
					System.out.println("Failed to authenticate!");
					socket.close();
					System.exit(1);
				}

			} else {

				out.writeObject(nonce);

				signature.initSign(privateKey);

				out.writeObject(signature.sign());
				String[] name = keystore.split("\\.");
				Certificate certificate = tstore.getCertificate(name[0]);
				out.writeObject(certificate);

				if ((Boolean) in.readObject()) {
					System.out.println("Registration successful!");
					System.out.println("Chose an operation:");
					mostrarMenu();

					String directoryName = "output/client/" + userid;
					File directory = new File(directoryName);
					directory.mkdirs();

				} else {
					System.out.println("Failed to register and authenticate!");
					socket.close();
					System.exit(1);
				}
			}

            // boolean autenticado = false;
            // while (!autenticado) {
            //     autenticado = autenticarUsuario(scanner, outStream, inStream);
            // }
            //2enviar device-id
            enviarDeviceId(scanner, out, in);  // Primeiro envia o Device ID
            //3enviar dados do executavel
            enviarDadosExecutavel(out,in);  // Depois envia os dados do executável
            //4 mostrar menu e enviar um comando ao servidor
            while (true) {
                mostrarMenu();
                System.out.print("Insira um comando: ");
                String comando = new Scanner(System.in).nextLine();
                if ("EXIT".equals(comando.toUpperCase())) {
                    break;
                }
                processarComando(comando, out, in);
                // Recebendo a resposta do servidor sobre o comando enviado.
                String resposta = in.readUTF();
                System.out.println("Resposta do Servidor: " + resposta);
            }


        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (UnrecoverableKeyException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SignatureException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        new IoTDevice().iniciarCliente();
    }
    public void iniciarCliente() {
        // System.out.println("user-id: " + userid);
        // System.out.println("device-id: " + devid);
        // try  {

        //     // SocketFactory sf = SSLSocketFactory.getDefault();
        //     // SSLSocket soc = (SSLSocket) sf.createSocket(host, 12345);

        //     // ObjectOutputStream outStream = new ObjectOutputStream(soc.getOutputStream());
        //     // ObjectInputStream inStream = new ObjectInputStream(soc.getInputStream());
        //     // Scanner scanner = new Scanner(System.in);
        //     //1autenticar usuario

        // } catch (IOException e) {
        //     System.err.println("Erro ao conectar ao servidor.");
        //     e.printStackTrace();
        // }
    }
    private boolean autenticarUsuario(Scanner scanner, ObjectOutputStream outStream, ObjectInputStream inStream) throws IOException {
        while (true) { // Loop até a autenticação ser bem-sucedida ou falhar por outro motivo
            // Ask for pswd
            System.out.println("Digita a senha para o usuario " + userid + ":");
            String senha = scanner.nextLine();
            enviarCredenciais(outStream, userid, senha);
            String response = inStream.readUTF();
            long serverTimestamp = inStream.readLong();
            long localTimestamp = System.currentTimeMillis();
            // Valida se o timestamp do servidor é recente em relação ao tempo local
            if (serverTimestamp <= localTimestamp - 1000) {
                System.out.println("Autenticação inválida. O timestamp do servidor não é recente.");
                return false;
            }
            switch (response) {
                case "WRONG-PWD":
                    System.out.println("Senha incorreta. Tente novamente.");
                    continue; // Permite nova tentativa
                case "OK-NEW-USER":
                    System.out.println("Novo usuário registrado.");
                    return true;
                case "OK-USER":
                    System.out.println("Usuário existente autenticado.");
                    return true;
                default:
                    System.out.println("Resposta não reconhecida: " + response);
                    return false;
            }
        }
    }
    private static void enviarDeviceId(Scanner scanner, ObjectOutputStream outStream, ObjectInputStream inStream) throws IOException {
        String deviceResponse;
        do {
            System.out.println("Enviando o device-id ao servidor...");
            outStream.writeObject(devid);
            outStream.flush();
            deviceResponse = inStream.readUTF();
            if ("OK-DEVID".equals(deviceResponse)) {
                System.out.println(deviceResponse);
                System.out.println("Device ID autenticado com sucesso.");
                break;
            } else if ("NOK-DEVID".equals(deviceResponse)) {
                System.out.println(deviceResponse);
                System.out.println("ID do dispositivo já em uso. Tente outro ID.");
                devid = scanner.nextLine();
                while(!devid.chars().allMatch(Character::isDigit)) {
                    System.out.println(
                            "ID do dispositivo inválido. Tente outro ID (numero inteiro).\n");
                    devid = scanner.nextLine();
                }
            } else {
                System.out.println("Resposta não reconhecida: " + deviceResponse);
                break;
            }
        } while ("NOK-DEVID".equals(deviceResponse));
    }
    private void enviarCredenciais(ObjectOutputStream outStream, String userid, String senha) throws IOException {
        outStream.writeObject(userid);
        outStream.writeObject(senha);
        outStream.flush();
    }
    private static void enviarDadosExecutavel(ObjectOutputStream outStream, ObjectInputStream inStream) throws IOException {
        String path = "IoTDevice.jar";
        File classFile = new File(path);
        String name = path.substring(path.lastIndexOf("/") + 1); //pegar apenas o nome do arquivo
        long fileSize = classFile.length();
        outStream.writeUTF(name);
        outStream.writeLong(fileSize);
        outStream.flush();
        String respostaTeste = inStream.readUTF();
        if ("NOK-TESTED".equals(respostaTeste)) {
            System.err.println(respostaTeste);
            System.err.println("Erro: Programa não validado pelo servidor.");
            System.exit(1);  // Encerra o programa
        } else if ("OK-TESTED".equals(respostaTeste)) {
            System.out.println(respostaTeste);
            System.out.println("Programa validado pelo" +
                    " servidor.");
            // Processo continua após a validação
        }
    }
    private static void mostrarMenu() {
        System.out.println("\nComandos Disponíveis:");
        System.out.println("CREATE <dm> # Criar domínio");
        System.out.println("ADD <user1> <dm> # Adicionar utilizador ao domínio");
        System.out.println("RD <dm> # Registar o Dispositivo no domínio");
        System.out.println("ET <float> # Enviar valor de Temperatura");
        System.out.println("EI <filename.jpg> # Enviar Imagem para o servidor");
        System.out.println("RT <dm> # Receber medições de Temperatura do domínio");
        System.out.println("RI <user-id>:<dev_id> # Receber Imagem do servidor");
        System.out.println("EXIT # Sair do programa");
    }
    //processar o comando inserido pelo cliente e enviar ao servidor metodo pequeno
    private static void processarComando(String comando, ObjectOutputStream outStream, ObjectInputStream inStream) throws IOException {
        if (comando.toUpperCase().startsWith("EI ")) {
            String filename = comando.substring(3);
            enviarImagem(filename, outStream);
        } else if (comando.toUpperCase().startsWith("RT ")) {
            String dominio = comando.substring(3);
            outStream.writeUTF(comando);
            outStream.flush();
            // Receber e processar resposta do servidor
            String response = inStream.readUTF();
            if ("OK".equals(response)) {
                long fileSize = inStream.readLong();
                byte[] fileData = new byte[(int) fileSize];
                inStream.readFully(fileData);
                // Salvar ou exibir os dados recebidos
                Path path = Paths.get(dominio+"_"+"received_temperature_data.txt");
                Files.write(path, fileData);
                System.out.println("OK, " + fileSize + " (long) seguido de " + fileSize + " bytes de dados.");
                System.out.println("Arquivo de temperatura recebido e salvo como " + path.getFileName());
            }
            else {
                System.out.println("Resposta do Servidor: " + response);
            }
        } else if (comando.toUpperCase().startsWith("RI ")) {
            outStream.writeUTF(comando);
            outStream.flush();
            String response = inStream.readUTF();
            if ("OK".equals(response)) {
                long fileSize = inStream.readLong();
                byte[] fileData = new byte[(int) fileSize];
                inStream.readFully(fileData);
                String filename = comando.split(" ")[1] + ".jpg"; // Nome do arquivo baseado no comando
                Path directoryPath = Paths.get("imagensrecebidasdoServidor");
                if (!Files.exists(directoryPath)) {
                    Files.createDirectories(directoryPath);
                }
                Path filePath = directoryPath.resolve(filename);
                Files.write(filePath, fileData);
                System.out.println("OK, " + fileSize + " (long) seguido de " + fileSize + " bytes de dados.");
                System.out.println("Imagem recebida e salva em: " + filePath);
            }
            else {
                System.out.println("Resposta do Servidor: " + response);
            }
        }
        else {
            // Processamento dos outros comandos
            outStream.writeUTF(comando);
            outStream.flush();
        }
    }
    private static void enviarImagem(String filename, ObjectOutputStream outStream) throws IOException {
        String path = filename; //filename=Erickson1_03-22-24";
        File file = new File(path);
        if (!file.exists()) {
            System.out.println("Arquivo não encontrado: " + filename);
            return;
        }
        long fileSize = file.length();
        // Enviando comando e nome do arquivo
        outStream.writeUTF("EI " + filename);
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
    }
}