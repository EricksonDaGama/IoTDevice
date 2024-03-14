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

            if (autenticarUsuario(scanner, outStream, inStream)) {
                enviarDeviceId(scanner, outStream, inStream);  // Alteração aqui
            }

        } catch (IOException e) {
            System.err.println("Erro ao conectar ao servidor: " + e.getMessage());
        }
    }

    private boolean autenticarUsuario(Scanner scanner, ObjectOutputStream outStream, ObjectInputStream inStream) throws IOException {
        while (true) {
            System.out.println("Digite a sua senha:");
            String senha = scanner.nextLine();

            enviarCredenciais(outStream, USER_ID, senha);

            String response = inStream.readUTF();
            switch (response) {
                case "WRONG-PWD":
                    System.out.println("Senha incorreta. Tente novamente.");
                    break;
                case "OK-NEW-USER":
                    System.out.println("Novo usuário registrado.");
                    return true;
                case "OK-USER":
                    System.out.println("Usuário existente autenticado.");
                    return true;
                default:
                    System.out.println("Resposta não reconhecida.");
                    return false;
            }
        }
    }

    private void enviarDeviceId(Scanner scanner, ObjectOutputStream outStream, ObjectInputStream inStream) throws IOException {
        System.out.println("Digite o ID do dispositivo:");
        String devId = scanner.nextLine();

        outStream.writeObject(devId);
        outStream.flush();

        String deviceResponse = inStream.readUTF();
        System.out.println(deviceResponse.equals("OK-DEVID") ? "Device ID autenticado com sucesso." : "ID do dispositivo já em uso. Tente outro ID.");
    }

    private void enviarCredenciais(ObjectOutputStream outStream, String userid, String senha) throws IOException {
        outStream.writeObject(userid);
        outStream.writeObject(senha);
        outStream.flush();
    }
}
