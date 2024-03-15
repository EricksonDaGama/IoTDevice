import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class IoTDevice {
    private static final String USER_ID = "Erickson";
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

            boolean autenticado = false;
            while (!autenticado) {
                autenticado = autenticarUsuario(scanner, outStream, inStream);
            }
            enviarDeviceId(scanner, outStream, inStream);

        } catch (IOException e) {
            System.err.println("Erro ao conectar ao servidor: " + e.getMessage());
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
                System.out.println("Device ID autenticado com sucesso.");
                break;
            } else if ("NOK-DEVID".equals(deviceResponse)) {
                System.out.println("ID do dispositivo já em uso. Tente outro ID.");
            } else {
                System.out.println("Resposta não reconhecida: " + deviceResponse);
                break;
            }
        } while ("NOK-DEVID".equals(deviceResponse));

        System.out.println("digite qualquer coisa para fechar");
        scanner.nextLine();
    }

    private void enviarCredenciais(ObjectOutputStream outStream, String userid, String senha) throws IOException {
        outStream.writeObject(userid);
        outStream.writeObject(senha);
        outStream.flush();
    }
}
