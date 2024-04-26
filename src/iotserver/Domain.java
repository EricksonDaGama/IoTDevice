package src.iotserver;

import java.util.ArrayList;

public class Domain {
    private String name;
    private String ownerId;
    private ArrayList<String> users;
    private ArrayList<String> devices;

    public Domain(String name, String ownerId) {
        this.name = name;
        this.ownerId = ownerId;
        this.users = new ArrayList<>();
        this.devices = new ArrayList<>();
    }

    public boolean registerUser(String userId) {
        if (this.isOwner(userId)){
            return false;
        }
        return users.add(userId);
    }

    public boolean isOwner(String userId) {
        return userId.equals(ownerId);
    }

    public boolean isRegistered(String userId) {
        return users.contains(userId) || isOwner(userId);
    }

    public boolean registerDevice(String deviceFullID) {
        return devices.add(deviceFullID);
    }

    public boolean isDeviceRegistered(String device) {
        return devices.contains(device);
    }
    
    public String getName(){
        return this.name;
    }

    public ArrayList<String> getDevices(){
        return this.devices;
    }

    public ArrayList<String> getUsers(){
        return this.users;
    }

    @Override
    public String toString() {
        final char NL = '\n';
        final char TAB = '\t';
        final char SP = ':';

        StringBuilder sb = new StringBuilder();
        sb.append(getName() + SP + ownerId);

        for (String registeredUser : users) {
            sb.append(SP + registeredUser);
        }

        sb.append(NL);

        for (String devFullId : devices) {
            sb.append(TAB + devFullId + NL);
        }

        return sb.toString();
    }
}
