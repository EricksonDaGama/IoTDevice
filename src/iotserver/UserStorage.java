package src.iotserver;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UserStorage {
    private Map<String, String> users;
    private File usersFile;
    private Lock wLock;
    private Lock rLock;

    public UserStorage(String usersFilePath) {
        users = new HashMap<>();
        usersFile = new File(usersFilePath);
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);
        wLock = rwLock.writeLock();
        rLock = rwLock.readLock();

        try {
            usersFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            populateUsersFromFile();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public boolean registerUser(String user, String certPath) {
        boolean ret = users.put(user, certPath) != null;
        updateUsersFile();
        return ret;
    }

    public boolean isUserRegistered(String user) {
        return users.containsKey(user);
    }

    public String userCertPath(String user) {
        return users.get(user);
    }

    public void readLock() {
        rLock.lock();
    }

    public void readUnlock() {
        rLock.unlock();
    }

    public void writeLock() {
        wLock.lock();
    }

    public void writeUnlock() {
        wLock.unlock();
    }

    private void updateUsersFile() {
        final String NL = "\n";
        final String SEP = ":";

        StringBuilder sb = new StringBuilder();
        for (String user: users.keySet()) {
            sb.append(user + SEP + users.get(user) + NL);
        }

        try (PrintWriter pw = new PrintWriter(usersFile)) {
            pw.write(sb.toString());
            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void populateUsersFromFile() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(usersFile));
        String[] lines = (String[]) reader.lines().toArray(String[]::new);
        reader.close();

        for (String line: lines) {
            String[] tokens  = split(line, ':');
            String user = tokens[0];
            String certPath = tokens[1];
            registerUser(user, certPath);
        }
    }


    static public String[] split(String str, char sep) {
        int occurrences = 1;
        ArrayList<String> blocks = new ArrayList<>();

        int i = 0;
        int j = str.indexOf(sep) != -1 ? str.indexOf(sep) : str.length();
        blocks.add(str.substring(i, j).trim());

        while (j != str.length()) {
            i = j + 1;
            j = str.indexOf(sep, i) != -1 ? str.indexOf(sep, i) : str.length();
            blocks.add(str.substring(i, j).trim());
            occurrences++;
        }

        return blocks.toArray(new String[occurrences]);
    }
}
