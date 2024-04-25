package src.server;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DeviceManager {
    private Map<String, Device> devices;
    private File devicesFile;
    private Lock wLock;
    private Lock rLock;

    public DeviceManager(String deviceFilePath) {
        devices = new HashMap<>();
        devicesFile = new File(deviceFilePath);

        try {
            devicesFile.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            populateDevicesFromFile();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);
        wLock = rwLock.writeLock();
        rLock = rwLock.readLock();
    }

    public void addDevice(String userID, String devID) {
        Device device = new Device(userID, devID);
        device.goOnline();
        devices.put(fullID(userID, devID), device);
        updateDevicesFile();
    }
    public static String fullID(String userId, String devId){
        return (userId + ":" + devId);
    }


    public void addDomainToDevice(String userID, String devID,
            String domainName) {
        devices.get(fullID(userID, devID)).registerInDomain(domainName);
        updateDevicesFile();
    }

    public void saveDeviceImage(String userID, String devID, String imgPath) {
        devices.get(fullID(userID, devID)).registerImage(imgPath);
        updateDevicesFile();
    }

    public String getDeviceImage(String userID, String devID) {
        return devices.get(fullID(userID, devID)).getImagePath();
    }

    public void saveDeviceTemperature(String userID, String devID, float temp) {
        devices.get(fullID(userID, devID)).registerTemperature(temp);
        updateDevicesFile();
    }

    public float getDeviceTemperature(String userID, String devID) {
        return devices.get(fullID(userID, devID)).getTemperature();
    }

    public boolean deviceExists(String userID, String devID) {
        return devices.containsKey(fullID(userID, devID));
    }

    public boolean isDeviceOnline(String userID, String devID) {
        return devices.get(fullID(userID, devID)).isOnline();
    }

    public void activateDevice(String userID, String devID) {
        devices.get(fullID(userID, devID)).goOnline();
    }

    public void deactivateDevice(String userID, String devID) {
        devices.get(fullID(userID, devID)).goOffline();
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

    private void updateDevicesFile() {
        StringBuilder sb = new StringBuilder();
        for (Device device : devices.values()) {
            sb.append(device.toString());
        }

        try (PrintWriter pw = new PrintWriter(devicesFile)) {
            pw.write(sb.toString());
            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void populateDevicesFromFile() throws IOException {
        final char SP = ':';

        BufferedReader reader = new BufferedReader(new FileReader(devicesFile));
        String[] lines = (String[]) reader.lines().toArray(String[]::new);
        reader.close();

        for (int i = 0; i < lines.length; i++) {
            String[] tokens = split(lines[i], SP);
            String uid = tokens[0];
            String did = tokens[1];
            Float temperature = null;
            if(!tokens[2].equals("")){temperature = Float.parseFloat(tokens[2]);}
            String imagePath = tokens[3];;

            Device device = new Device(uid, did);
            if(temperature != null){device.registerTemperature(temperature);}
            if(imagePath!=null) device.registerImage(imagePath);

            devices.put(fullID(uid, did), device);
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
