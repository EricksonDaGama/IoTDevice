package src.server;

import src.others.CodeMessage;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class ManagerSever {
    private static volatile ManagerSever instance;

    private DomainManager domStorage;
    private DeviceManager devStorage;
    private ManagerUsers managerUsers;

    private static final String baseDir = "./output/server/";
    private static final String attestationFilePath = "Info_IoTDevice.txt";
    private static final String domainFilePath = baseDir + "domain.txt";
    private static final String deviceFilePath = baseDir + "device.txt";
    private static final String userFilePath = "user.txt";
    private static final String imageDirectoryPath = baseDir + "img/";
    private static final String temperatureDirectoryPath = baseDir + "temp/";

    private ManagerSever(){
        domStorage = new DomainManager(domainFilePath);
        devStorage = new DeviceManager(deviceFilePath);
        managerUsers = new ManagerUsers(userFilePath);

        new File(imageDirectoryPath).mkdirs();
        new File(temperatureDirectoryPath).mkdirs();

        // register attestation value
       /* try {
            File attestationFile = new File(attestationFilePath);
            BufferedReader attestationReader =
                new BufferedReader(new FileReader(attestationFile));
                clientFilePath = attestationReader.readLine();
            attestationReader.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }*/
    }

    public static ManagerSever getInstance(){
        // thread calls this to get the db
        ManagerSever res = instance;
        if(res != null){
            return res;
        }

        synchronized(ManagerSever.class) {
            if (instance == null) {
                instance = new ManagerSever();
            }
            return instance;
        }
    }

    /*
     * CLIENT COMMANDS====================================================================================================================
     */
    public ServerResponse createDomain(String ownerUID, String domainName){
        domStorage.writeLock();
        try {
            if (domStorage.domainExists(domainName)) {
                return new ServerResponse(CodeMessage.NOK);
            }

            domStorage.addDomain(domainName, ownerUID);
            return new ServerResponse(CodeMessage.OK);
        } finally {
            domStorage.writeUnlock();
        }
    }

    public ServerResponse addUserToDomain(String requesterUID, String newUserID,
            String domainName) {
        domStorage.writeLock();
        managerUsers.readLock();
        try {
            if (!domStorage.domainExists(domainName)) {
                return new ServerResponse(CodeMessage.NODM);
            }

            if (!managerUsers.isUserRegistered(newUserID)) {
                return new ServerResponse(CodeMessage.NOUSER);
            }

            if (!domStorage.isOwnerOfDomain(requesterUID, domainName)) {
                return new ServerResponse(CodeMessage.NOPERM);
            }

            boolean ret = domStorage
                .addUserToDomain(requesterUID, newUserID, domainName);
            if (ret) {
                return new ServerResponse(CodeMessage.OK);
            } else {
                return new ServerResponse(CodeMessage.USEREXISTS);
            }
        } finally {
            managerUsers.readUnlock();
            domStorage.writeUnlock();
        }
    }

    // devID being ID
    public ServerResponse registerDeviceInDomain(String domainName,
            String userId, String devId) {
        domStorage.writeLock();
        devStorage.writeLock();
        try {
            if (!domStorage.domainExists(domainName)) {
                return new ServerResponse(CodeMessage.NODM);
            }

            if (!domStorage.isUserRegisteredInDomain(userId, domainName)) {
                return new ServerResponse(CodeMessage.NOPERM);
            }

            if (domStorage.isDeviceRegisteredInDomain(userId, devId,
                    domainName)) {
                return new ServerResponse(CodeMessage.DEVICEEXISTS);
            }

            domStorage.addDeviceToDomain(userId, devId, domainName);
            devStorage.addDomainToDevice(userId, devId, domainName);
            return new ServerResponse(CodeMessage.OK);
        } finally {
            devStorage.writeUnlock();
            domStorage.writeUnlock();
        }
    }


    public ServerResponse registerTemperature(float temperature, String userId,
            String devId) {
        devStorage.writeLock();
        try {
            devStorage.saveDeviceTemperature(userId, devId, temperature);
            return new ServerResponse(CodeMessage.OK);
        } finally {
            devStorage.writeUnlock();
        }
    }

    public ServerResponse registerImage(String filename, String userId,
            String devId) {
        devStorage.writeLock();
        try {
            devStorage.saveDeviceImage(userId, devId, filename);
            return new ServerResponse(CodeMessage.OK);
        } finally {
            devStorage.writeUnlock();
        }
    }

    public ServerResponse getTemperatures(String user, String domainName)
            throws IOException {
        domStorage.readLock();
        devStorage.readLock();
        try {
            if (!domStorage.domainExists(domainName)) {
                return new ServerResponse(CodeMessage.NODM);
            }

            if (!domStorage.isUserRegisteredInDomain(user, domainName)) {
                return new ServerResponse(CodeMessage.NOPERM);
            }

            Map<String, Float> temps = domStorage.temperatures(domainName,
                    devStorage);

            //XXX ServerResponse is being init with a Map?
            return new ServerResponse(CodeMessage.OK, temps);
        } finally {
            devStorage.readUnlock();
            domStorage.readUnlock();
        }
    }

    public ServerResponse getImage(String requesterUID, String targetUID,
            String targetDID) {
        domStorage.readLock();
        devStorage.readLock();
        try {
            if (!devStorage.deviceExists(targetUID, targetDID)) {
                return new ServerResponse(CodeMessage.NOID);
            }

            String filepath = devStorage.getDeviceImage(targetUID, targetDID);
            if (filepath == null) {
                return new ServerResponse(CodeMessage.NODATA);
            }

            if (domStorage.hasAccessToDevice(requesterUID, targetUID,
                    targetDID)) {
                return new ServerResponse(CodeMessage.OK, filepath);
            }

            return new ServerResponse(CodeMessage.NOPERM);
        } finally {
            devStorage.readUnlock();
            domStorage.readUnlock();
        }
    }

    /*
     *AUTHENTICATION====================================================================================================================
     */

    public ServerResponse authenticateUser(String user)
            throws IOException {
        managerUsers.readLock();
        try {
            if (managerUsers.isUserRegistered(user)) {
                return new ServerResponse(CodeMessage.OK_USER);
            }
        } finally {
            managerUsers.readUnlock();
        }

        managerUsers.writeLock();
        try {
            managerUsers.registerUser(user, "");
            return new ServerResponse(CodeMessage.OK_NEW_USER);
        } finally {
            managerUsers.writeUnlock();
        }
    }

    public void disconnectDevice(String userID, String devID){
        devStorage.writeLock();
        try {
            devStorage.deactivateDevice(userID, devID);
        } finally {
            devStorage.writeUnlock();
        }
    }

    //assumes userId exists
    public ServerResponse authenticateDevice(String userId, String devId)
            throws IOException {
        devStorage.writeLock();
        try {
            if (devStorage.deviceExists(userId, devId)) {
                System.out.println("devid:" + fullID(userId, devId));

                if (devStorage.isDeviceOnline(userId, devId)) {
                    System.out.println("dev is online");
                    return new ServerResponse(CodeMessage.NOK_DEVID);
                } else {
                    devStorage.activateDevice(userId, devId);
                    return new ServerResponse(CodeMessage.OK_DEVID);
                }
            }

            devStorage.addDevice(userId, devId);
            return new ServerResponse(CodeMessage.OK_DEVID);
        } finally {
            devStorage.writeUnlock();
        }
    }
    public static String fullID(String userId, String devId){
        return (userId + ":" + devId);
    }


    /** public ServerResponse attestClient(String devFileName, long devFileSize)
             throws IOException {
         if (devFileName.equals(clientFileName) && devFileSize==clientFileSize) {
             return new ServerResponse(MessageCode.OK_TESTED);
         }

         return new ServerResponse(MessageCode.NOK_TESTED);
     }*/
}
