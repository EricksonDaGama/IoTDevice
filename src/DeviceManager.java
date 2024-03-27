import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class DeviceManager {
    private Map<String, String> registeredDevices;
    private Map<String,String> activeSessions;
    private Map<String, Long> activeSessionsTimestamps;
    private static final long TIMEOUT_THRESHOLD = 60000; // 1 minuto
    public DeviceManager() {
        registeredDevices = new HashMap<>();
        activeSessions = new HashMap<>();
        activeSessionsTimestamps = new ConcurrentHashMap<>();
    }
    public synchronized boolean registerDevice(String userId, String devId) {
        String sessionKey = userId + ":" + devId;
        long currentTime = System.currentTimeMillis();
        // Verifica se o dispositivo para este userId já está registrado e ativo
        if (devId.equals(activeSessions.get(userId))) {
            // Se já estiver ativo, nega o registro para evitar sessão simultânea
            System.out.println("Sessão já ativa para este usuário.");
            return false;
        }
        // Registra o dispositivo e a sessão
        registeredDevices.put(userId, devId);
        activeSessions.put(userId,devId); // Armazena apenas o userId para evitar sessões simultâneas
        activeSessionsTimestamps.put(sessionKey, currentTime);
        System.out.println("Dispositivo registrado com sucesso.");
        return true;
    }
    public synchronized void removeActiveSession(String userId, String devId) {
        activeSessions.remove(userId + ":" + devId);
        activeSessionsTimestamps.remove(userId + ":" + devId);
    }
    public void cleanupExpiredSessions() {
        long currentTime = System.currentTimeMillis();
        activeSessionsTimestamps.entrySet().removeIf(entry -> currentTime - entry.getValue() > TIMEOUT_THRESHOLD);
    }

    public synchronized boolean updateDeviceTemperature(String username, String devId, float temperatura) {
        Map<String, DeviceData> devices = loadDevices();
        String dispositivoKey = username + ":" + devId;
        DeviceData data = devices.getOrDefault(dispositivoKey, new DeviceData(null, ""));
        data.temperatura = temperatura; // Atualiza a temperatura
        devices.put(dispositivoKey, data);
        return saveDevices(devices);
    }
    public synchronized boolean updateDeviceImage(String username, String devId, String filename) {
        Map<String, DeviceData> devices = loadDevices();
        String dispositivoKey = username + ":" + devId;

        DeviceData data = devices.getOrDefault(dispositivoKey, new DeviceData(null, ""));
        data.imagem = filename;
        devices.put(dispositivoKey, data);
        return saveDevices(devices);
    }
    private Map<String, DeviceData> loadDevices() {
        Map<String, DeviceData> devices = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("devices.txt"))) {
            String line;
            String currentDevice = null;
            DeviceData currentData = null;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("Dispositivo:")) {
                    if (currentDevice != null && currentData != null) {
                        devices.put(currentDevice, currentData);
                    }
                    currentDevice = line.substring(line.indexOf(':') + 1).trim();
                    currentData = new DeviceData(null, ""); // Reset para novo dispositivo
                } else if (line.trim().startsWith("Última Temperatura:")) {
                    if (currentData != null) {
                        String tempStr = line.substring(line.indexOf(':') + 1).trim();
                        if (!tempStr.isEmpty() && !tempStr.equals("°C")) { // Adicionada verificação extra
                            tempStr = tempStr.replace("°C", "").trim(); // Remove "°C" se existir
                            currentData.temperatura = Float.parseFloat(tempStr);
                        }
                    }
                } else if (line.trim().startsWith("Última Imagem:")) {
                    if (currentData != null) {
                        currentData.imagem = line.substring(line.indexOf(':') + 1).trim();
                    }
                }
            }
            if (currentDevice != null && currentData != null) {
                devices.put(currentDevice, currentData); // Adiciona o último dispositivo lido
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return devices;
    }
    private boolean saveDevices(Map<String, DeviceData> devices) {
        List<String> fileContent = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("devices.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                fileContent.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        for (Map.Entry<String, DeviceData> entry : devices.entrySet()) {
            String dispositivoKey = "Dispositivo: " + entry.getKey();
            int deviceIndex = -1;

            // Procura o dispositivo no arquivo
            for (int i = 0; i < fileContent.size(); i++) {
                if (fileContent.get(i).equals(dispositivoKey)) {
                    deviceIndex = i;
                    break;
                }
            }
            // Se encontrou, atualiza os dados; se não, adiciona
            if (deviceIndex != -1) {
                updateDeviceData(fileContent, deviceIndex, entry.getValue());
            } else {
                addNewDeviceData(fileContent, dispositivoKey, entry.getValue());
            }
        }
        // Escrever todas as linhas de volta no arquivo
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("devices.txt"))) {
            for (String fileLine : fileContent) {
                bw.write(fileLine);
                bw.newLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    private void updateDeviceData(List<String> fileContent, int deviceIndex, DeviceData data) {
        String temperaturaStr = (data.temperatura != null) ? "Última Temperatura: " + data.temperatura + "°C" : "Última Temperatura: ";
        fileContent.set(deviceIndex + 1, temperaturaStr);
        fileContent.set(deviceIndex + 2, "Última Imagem: " + data.imagem);
    }

    private void addNewDeviceData(List<String> fileContent, String dispositivoKey, DeviceData data) {
        String temperaturaStr = (data.temperatura != null) ? "Última Temperatura: " + data.temperatura + "°C" : "Última Temperatura: ";
        fileContent.add(dispositivoKey);
        fileContent.add(temperaturaStr);
        fileContent.add("Última Imagem: " + data.imagem);
    }
    public synchronized boolean isDeviceRegistered(String username, String devId) {
        String dispositivoKey = username + ":" + devId;
        try (BufferedReader reader = new BufferedReader(new FileReader("dominios.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("Dispositivos registrados:")) {
                    if (line.contains(dispositivoKey)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
    public String getDeviceImagePath(String deviceId) {
        try (BufferedReader reader = new BufferedReader(new FileReader("devices.txt"))) {
            String line;
            boolean isCurrentDevice = false;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Dispositivo: " + deviceId)) {
                    isCurrentDevice = true;
                } else if (isCurrentDevice && line.startsWith("Última Imagem:")) {
                    return line.substring(line.indexOf(':') + 1).trim(); // Retorna o caminho da imagem
                } else if (line.isEmpty()) {
                    isCurrentDevice = false;
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao acessar o arquivo de dispositivos: " + e.getMessage());
        }
        return null; // Nenhum caminho de imagem encontrado
    }
}