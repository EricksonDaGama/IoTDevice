import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class IoTDevice {
    private static final String USER_ID = "Erickson";
    private static final String HOST = "localhost";
    private static final int PORT = 23456;

    private static final String DEV_ID = "1";

    public static void main(String[] args) {
        new IoTDevice().iniciarCliente();
    }

    public void iniciarCliente() {
        try (Socket socket = new Socket(HOST, PORT);
             ObjectOutputStream outStream = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream inStream = new ObjectInputStream(socket.getInputStream());
             Scanner scanner = new Scanner(System.in)) {

            while (true) {
                System.out.println("Digite a sua senha:");
                String senha = scanner.nextLine();

                enviarCredenciais(outStream, USER_ID, senha);

                String response = inStream.readUTF();
                System.out.println(response);

                switch (response) {
                    case "WRONG-PWD":
                        System.out.println("Senha incorreta. Tente novamente.");
                        break;
                    case "OK-NEW-USER":
                        System.out.println("Novo usuário registrado.");
                        return; // Finaliza após o registro bem-sucedido
                    case "OK-USER":
                        System.out.println("Usuário existente autenticado.");
                        return; // Finaliza após a autenticação bem-sucedida
                    default:
                        System.out.println("Resposta não reconhecida.");
                        return; // Finaliza em caso de resposta desconhecida
                }
            }

        } catch (IOException e) {
            System.err.println("Erro ao conectar ao servidor: " + e.getMessage());
        }
    }

    private void enviarCredenciais(ObjectOutputStream outStream, String userid, String senha) throws IOException {
        outStream.writeObject(userid);
        outStream.writeObject(senha);
        outStream.flush();
        System.out.println("Credenciais enviadas.");
    }
}
