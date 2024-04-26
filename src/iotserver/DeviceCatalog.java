package src.iotserver;

import src.iohelper.Utils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DeviceCatalog {
    private Map<String, Device> devices;
    private File devicesFile;
    private Lock wLock;
    private Lock rLock;

    public DeviceCatalog(String deviceFilePath) {
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
        devices.put(Utils.fullID(userID, devID), device);
        updateDevicesFile();
    }

    public void addDomainToDevice(String userID, String devID,
            String domainName) {
        devices.get(Utils.fullID(userID, devID)).registerInDomain(domainName);
        updateDevicesFile();
    }

    public void saveDeviceImage(String userID, String devID, String imgPath) {
        devices.get(Utils.fullID(userID, devID)).registerImage(imgPath);
        updateDevicesFile();
    }

    public String getDeviceImage(String userID, String devID) {
        return devices.get(Utils.fullID(userID, devID)).getImagePath();
    }

    public void saveDeviceTemperature(String userID, String devID, float temp) {
        devices.get(Utils.fullID(userID, devID)).registerTemperature(temp);
        updateDevicesFile();
    }

    public float getDeviceTemperature(String userID, String devID) {
        return devices.get(Utils.fullID(userID, devID)).getTemperature();
    }

    public boolean deviceExists(String userID, String devID) {
        return devices.containsKey(Utils.fullID(userID, devID));
    }

    public boolean isDeviceOnline(String userID, String devID) {
        return devices.get(Utils.fullID(userID, devID)).isOnline();
    }

    public void activateDevice(String userID, String devID) {
        devices.get(Utils.fullID(userID, devID)).goOnline();
    }

    public void deactivateDevice(String userID, String devID) {
        devices.get(Utils.fullID(userID, devID)).goOffline();
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
            String[] tokens = Utils.split(lines[i], SP);
            String uid = tokens[0];
            String did = tokens[1];
            Float temperature = null;
            if(!tokens[2].equals("")){temperature = Float.parseFloat(tokens[2]);}
            String imagePath = tokens[3];;

            Device device = new Device(uid, did);
            if(temperature != null){device.registerTemperature(temperature);}
            if(imagePath!=null) device.registerImage(imagePath);

            devices.put(Utils.fullID(uid, did), device);
        }
    }
}
