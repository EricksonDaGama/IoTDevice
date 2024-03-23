import java.util.HashSet;
import java.util.Set;

public class Main {


    public static void main(String[] args) {
        String input = "[Rodrigo:1 Erickson:1]";

        // Removendo os colchetes
        String trimmedInput = input.substring(1, input.length() - 1);

        // Separando os elementos
        String[] elements = trimmedInput.split(" ");

        // Adicionando ao conjunto Set
        Set<String> resultSet = new HashSet<>();
        for (String element : elements) {
            resultSet.add(element);
        }

        // Imprimindo o conjunto Set
        System.out.println(resultSet.contains("Erickson:1"));
    }

}
