import java.io.*;
import java.util.HashMap;
import java.util.Map;

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