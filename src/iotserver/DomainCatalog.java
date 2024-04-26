package src.iotserver;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DomainCatalog {
    private Map<String, Domain> domains;
    private File domainsFile;
    private Lock wLock;
    private Lock rLock;

    public DomainCatalog(String domainFilePath) {
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
        boolean ret = domain.registerDevice(userID + ":" + devID);
        if (ret) updateDomainsFile();
        return ret;
    }

    public Map<String, Float> temperatures(String domainName,
            DeviceCatalog devStorage) {

        Domain domain = domains.get(domainName);
        Map<String, Float> temperatures = new HashMap<>();

        for (String fullID : domain.getDevices()) {
            String userID = fullID.split(":")[0];
            String devID = fullID.split(":")[1];
            float temp =
                devStorage.getDeviceTemperature(userID, devID);
            temperatures.put(fullID, temp);
        }
        return temperatures;
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
        return domain.isDeviceRegistered(userID + ":" + devID);
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

    public boolean hasAccessToDevice(String user, String userID, String devID) {
        boolean hasAccess = false;
        String fullID = userID + ":" + devID;

        for (Domain domain : domains.values()) {
            if (!domain.isDeviceRegistered(fullID)) continue;
            if (domain.isRegistered(user)) {
                hasAccess = true;
                break;
            }
        }

        return user.equals(userID) || hasAccess;
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
            String[] tokens = line.split(":");

            if (isDomainLine) {
                currentDomainName = tokens[0];
                initDomainFromLine(tokens);
            } else {
                String userID = tokens[0];
                String devID = tokens[1];
                domains
                    .get(currentDomainName)
                    .registerDevice(userID + ":" + devID);
            }
        }
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
