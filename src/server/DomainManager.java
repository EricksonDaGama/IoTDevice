package src.server;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DomainManager {
    private Map<String, Domain> domains;
    private File domainsFile;
    private Lock wLock;
    private Lock rLock;

    public DomainManager(String domainFilePath) {
        domainsFile = new File(domainFilePath);
        domains = new HashMap<>();

        try {
            domainsFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            populateDomainsFromFile();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);
        wLock = rwLock.writeLock();
        rLock = rwLock.readLock();
    }

    public void addDomain(String domainName, String ownerUID) {
        Domain domain = new Domain(domainName, ownerUID);
        domains.put(domainName, domain);
        updateDomainsFile();
    }

    public boolean addUserToDomain(String requesterUID, String newUserID,
            String domainName) {
        Domain domain = domains.get(domainName);
        boolean ret = domain.registerUser(newUserID);
        if (ret) updateDomainsFile();
        return ret;
    }

    public boolean addDeviceToDomain(String userID, String devID,
            String domainName) {
        Domain domain = domains.get(domainName);
        boolean ret = domain.registerDevice(fullID(userID, devID));
        if (ret) updateDomainsFile();
        return ret;
    }
    public static String fullID(String userId, String devId){
        return (userId + ":" + devId);
    }


    public Map<String, Float> temperatures(String domainName,
            DeviceManager devStorage) {
        //FIXME A better implementation doesn't need access to devStorage
        // This can be achieved by refactoring the domain's registered devices
        // as a Set<Device> instead of Set<String>

        Domain domain = domains.get(domainName);
        Map<String, Float> temperatures = new HashMap<>();

        for (String fullDevID : domain.getDevices()) {
            String userID = userIDFromFullID(fullDevID);
            String devID = devIDFromFullID(fullDevID);
            float devTemperature =
                devStorage.getDeviceTemperature(userID, devID);
            temperatures.put(fullDevID, devTemperature);
        }
        return temperatures;
    }

    public static String devIDFromFullID(String fullDevID) {
        return fullDevID.split(":")[1];
    }



    public static String userIDFromFullID(String fullDevID) {
        return fullDevID.split(":")[0];
    }


    public boolean domainExists(String domainName) {
        return domains.containsKey(domainName);
    }

    public boolean isOwnerOfDomain(String userID, String domainName) {
        Domain domain = domains.get(domainName);
        return domain.isOwner(userID);
    }

    public boolean isUserRegisteredInDomain(String userID, String domainName) {
        Domain domain = domains.get(domainName);
        return domain.isRegistered(userID);
    }

    public boolean isDeviceRegisteredInDomain(String userID, String devID,
            String domainName) {
        Domain domain = domains.get(domainName);
        return domain.isDeviceRegistered(fullID(userID, devID));
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

    public boolean hasAccessToDevice(String user, String devUID, String devDID) {
        boolean hasAccess = false;
        String fullID = fullID(devUID, devDID);

        for (Domain domain : domains.values()) {
            if (!domain.isDeviceRegistered(fullID)) continue;
            if (domain.isRegistered(user)) {
                hasAccess = true;
                break;
            }
        }

        return user.equals(devUID) || hasAccess;
    }

    private void updateDomainsFile(){
        StringBuilder sb = new StringBuilder();
        for (Domain domain : domains.values()) {
            sb.append(domain.toString());
        }

        try (PrintWriter pw = new PrintWriter(domainsFile)) {
            pw.write(sb.toString());
            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void populateDomainsFromFile() throws IOException {
        final char SP = ':';
        final char TAB = '\t';

        BufferedReader reader = new BufferedReader(new FileReader(domainsFile));
        String[] lines = (String[]) reader.lines().toArray(String[]::new);
        reader.close();

        String currentDomainName = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean isDomainLine = line.charAt(0) != TAB;
            String[] tokens = split(line, SP);

            if (isDomainLine) {
                currentDomainName = tokens[0];
                initDomainFromLine(tokens);
            } else {
                String devUID = tokens[0];
                String devDID = tokens[1];
                domains
                    .get(currentDomainName)
                    .registerDevice(fullID(devUID, devDID));
            }
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

    private void initDomainFromLine(String[] tokens) {
        String domainName = tokens[0];
        String owner = tokens[1];

        Domain domain = new Domain(domainName, owner);
        for (int j = 2; j < tokens.length; j++) {
            String user = tokens[j];
            domain.registerUser(user);
        }

        domains.put(domainName, domain);
    }
}
