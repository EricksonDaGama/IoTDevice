import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class IoTDevice {
    private static final String USER_ID = "Erickson";  //"Rodrigo"; // "Barata" //"Erickson"
    private static final String HOST = "localhost";
    private static final int PORT = 23456;
    public static void main(String[] args) {
        new IoTDevice().iniciarCliente();
    }
    public void iniciarCliente() {
        try (Socket socket = new Socket(HOST, PORT);
             ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());
             Scanner scanner = new Scanner(System.in)) {
            //1autenticar usuario
            boolean autenticado = false;
            while (!autenticado) {
                autenticado = autenticarUsuario(scanner, outStream, inStream);
            }
            //2enviar device-id
            enviarDeviceId(scanner, outStream, inStream);  // Primeiro envia o Device ID
            //3enviar dados do executavel
            enviarDadosExecutavel(outStream,inStream);  // Depois envia os dados do executável
            //4 mostrar menu e enviar um comando ao servidor
            while (true) {
                mostrarMenu();
                System.out.print("Insira um comando: ");
                String comando = new Scanner(System.in).nextLine();
                if ("EXIT".equals(comando.toUpperCase())) {
                    break;
                }
                processarComando(comando, outStream, inStream);
                // Recebendo a resposta do servidor sobre o comando enviado.
                String resposta = inStream.readUTF();
                System.out.println("Resposta do Servidor: " + resposta);
            }
        } catch (IOException e) {
            System.err.println("Erro ao conectar ao servidor.");
            e.printStackTrace();
        }
    }
    private boolean autenticarUsuario(Scanner scanner, ObjectOutputStream outStream, ObjectInputStream inStream) throws IOException {
        while (true) { // Loop até a autenticação ser bem-sucedida ou falhar por outro motivo
            System.out.println("Digite a sua senha:");
            String senha = scanner.nextLine();

            enviarCredenciais(outStream, USER_ID, senha);

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
    private void enviarDeviceId(Scanner scanner, ObjectOutputStream outStream, ObjectInputStream inStream) throws IOException {
        String deviceResponse;
        do {
            System.out.println("Digite o ID do dispositivo:");
            String devId = scanner.nextLine();
            outStream.writeObject(devId);
            outStream.flush();
            deviceResponse = inStream.readUTF();
            if ("OK-DEVID".equals(deviceResponse)) {
                System.out.println(deviceResponse);
                System.out.println("Device ID autenticado com sucesso.");
                break;
            } else if ("NOK-DEVID".equals(deviceResponse)) {
                System.out.println(deviceResponse);
                System.out.println("ID do dispositivo já em uso. Tente outro ID.");
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
    private void enviarDadosExecutavel(ObjectOutputStream outStream, ObjectInputStream inStream) throws IOException {
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
    private void mostrarMenu() {
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
    private void processarComando(String comando, ObjectOutputStream outStream, ObjectInputStream inStream) throws IOException {
        if (comando.toUpperCase().startsWith("EI ")) {
            String filename = comando.substring(3);
            enviarImagem(filename, outStream);

            // Recebendo a resposta do servidor
            String resposta = inStream.readUTF();
            System.out.println("Resposta do Servidor: " + resposta);
        } else if (comando.toUpperCase().startsWith("RT ")) {
            outStream.writeUTF(comando);
            outStream.flush();

            // Receber e processar resposta do servidor
            String response = inStream.readUTF();
            if ("OK".equals(response)) {
                long fileSize = inStream.readLong();
                byte[] fileData = new byte[(int) fileSize];
                inStream.readFully(fileData);
                // Salvar ou exibir os dados recebidos
                Path path = Paths.get("received_temperature_data.txt");
                Files.write(path, fileData);
                System.out.println("OK, " + fileSize + " (long) seguido de " + fileSize + " bytes de dados.");
                System.out.println("Arquivo de temperatura recebido e salvo como " + path.getFileName());
            } else {
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
            } else {
                System.out.println("Resposta do Servidor: " + response);
            }
        }
        else {
            // Processamento dos outros comandos
            outStream.writeUTF(comando);
            outStream.flush();

            // Recebendo a resposta do servidor
            String resposta = inStream.readUTF();
            System.out.println("Resposta do Servidor: " + resposta);
        }
    }

    private void enviarImagem(String filename, ObjectOutputStream outStream) throws IOException {
        String path = "src/"+filename; //filename=Erickson1_03-22-24";
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