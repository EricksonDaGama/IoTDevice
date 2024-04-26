package src.iotserver;

import java.util.HashSet;
import java.util.Set;

public class Domain {
    private String name;
    private String ownerId;
    private Set<String> registeredUsers;
    private Set<String> devices;

    public Domain(String name, String ownerId) {
        this.name = name;
        this.ownerId = ownerId;
        this.registeredUsers = new HashSet<>();
        this.devices = new HashSet<>();
    }

    public boolean registerUser(String userId) {
        if (this.isOwner(userId)){
            return false;
        }
        return registeredUsers.add(userId);
    }

    public boolean isOwner(String userId) {
        return userId.equals(ownerId);
    }

    public boolean isRegistered(String userId) {
        return registeredUsers.contains(userId) || isOwner(userId);
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

    public Set<String> getDevices(){
        return this.devices;
    }

    public Set<String> getUsers(){
        return this.registeredUsers;
    }

    @Override
    public String toString() {
        final char NL = '\n';
        final char TAB = '\t';
        final char SP = ':';

        StringBuilder sb = new StringBuilder();
        sb.append(getName() + SP + ownerId);

        for (String registeredUser : registeredUsers) {
            sb.append(SP + registeredUser);
        }

        sb.append(NL);

        for (String devFullId : devices) {
            sb.append(TAB + devFullId + NL);
        }

        return sb.toString();
    }
}
